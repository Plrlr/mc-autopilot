#!/usr/bin/env python3
"""Trains the learned brain from every run the loop (and the laptop) has played.

    python scripts/loop/train.py --state DIR [--horizon 120]

Reads DIR/data/**/*.jsonl.gz (written by loop.py update), writes DIR/learned.json and a line of
stats into DIR/state.json ("model_rows", "model_r2").

For each decision it measures what happened next: progress over the following `horizon` game
seconds, from a potential function of the state features (tools, key items, milestones and
checkpoints; losing the inventory to a death shows up as a drop) minus a penalty per death in that
window. A tree model (gbt.py) learns how much progress follows a state whatever is chosen, then small
trees per action kind ("collect:log", "explore:lava", ...) learn each one's advantage over that. The
mod ranks options by the advantages (brains/Learned.java). Without numpy it falls back to the old
ridge regression per action kind.

This is the "direct method" of off-policy learning: it compares actions across all the states they
were taken in. Exploration runs (learned.explore) make sure the rules' second and third choices
get tried too, so the comparisons aren't only of the rules' habits. Rows are weighted by the
inverse propensity of the choice, capped, so deliberate tries count for what they are.
"""
import argparse
import datetime
import glob
import gzip
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import skillstats  # noqa: E402

# How much each feature is worth as progress (features are ~0..1; see Learned.features).
POTENTIAL = {
    "pick": 3.0, "sword": 1.0, "shield": 1.0, "armor": 2.0, "log": 0.5, "planks": 0.2, "blocks": 0.5,
    "iron": 2.0, "raw_iron": 1.0, "coal": 0.5, "cooked": 1.0, "buckets": 3.0, "water_bucket": 1.0,
    "lava_bucket": 1.0, "flint_steel": 2.0, "obsidian": 3.0, "milestone": 13.0, "checkpoints": 6.0,
    "nether": 5.0, "end": 10.0, "rods": 6.0, "pearls": 4.0, "eyes": 8.0, "fortress_known": 2.0,
    "portal_known": 3.0, "frame_known": 5.0,
}
DEATH = 3.0
MIN_ROWS = 40      # an action kind needs this many examples to get its own model
RIDGE = 2.0
MAX_WEIGHT = 5.0   # inverse-propensity weight cap
HAZARD_S = 45      # the danger model predicts death within this many game seconds


def potential(x, idx):
    return sum(w * x[idx[n]] for n, w in POTENTIAL.items() if n in idx)


def examples(files, feats, horizon, choices=None):
    """Keep the five-value rows; optionally append (i, o) alongside each yielded row."""
    idx = {n: i for i, n in enumerate(feats)}
    for f in files:
        try:
            with gzip.open(f, "rt", encoding="utf-8") as fh:
                rows = [json.loads(l) for l in fh if l.strip()]
        except (OSError, ValueError):
            continue
        dec = [r for r in rows if r["k"] == "d" and len(r["x"]) == len(feats)]
        deaths = [r["gs"] for r in rows if r["k"] == "death"]
        if len(dec) < 3:
            continue
        end = dec[-1]["gs"]
        for i, r in enumerate(dec):
            if r.get("u"):
                continue  # emergencies are the rules' job; the model never ranks them
            target = r["gs"] + horizon
            if target > end + 30:
                break
            j = i
            while j + 1 < len(dec) and dec[j + 1]["gs"] <= target:
                j += 1
            later = dec[j] if dec[j]["gs"] > r["gs"] else (dec[j + 1] if j + 1 < len(dec) else dec[j])
            y = potential(later["x"], idx) - potential(r["x"], idx)
            y -= DEATH * sum(1 for t in deaths if r["gs"] <= t <= target)
            w = min(MAX_WEIGHT, 1.0 / max(1e-3, r.get("p", 1)))
            if choices is not None:
                choices.append((r.get("i", 0), r.get("o")))
            yield f, r["a"], r["x"], y, w


def ridge(X, Y, W, lam):
    """Weighted ridge regression with an unpenalized bias; numpy if present, else plain Python."""
    d = len(X[0]) + 1
    try:
        import numpy as np
        A = np.hstack([np.ones((len(X), 1)), np.array(X)])
        w = np.sqrt(np.array(W))[:, None]
        Aw, yw = A * w, np.array(Y) * w[:, 0]
        R = lam * np.eye(d)
        R[0, 0] = 0
        return list(np.linalg.solve(Aw.T @ Aw + R, Aw.T @ yw))
    except ImportError:
        pass
    M = [[0.0] * d for _ in range(d)]
    b = [0.0] * d
    for x, y, wt in zip(X, Y, W):
        v = [1.0] + list(x)
        for p in range(d):
            vp = v[p] * wt
            b[p] += vp * y
            row = M[p]
            for q in range(d):
                row[q] += vp * v[q]
    for p in range(1, d):
        M[p][p] += lam
    # Gaussian elimination with partial pivoting
    for c in range(d):
        piv = max(range(c, d), key=lambda r: abs(M[r][c]))
        M[c], M[piv] = M[piv], M[c]
        b[c], b[piv] = b[piv], b[c]
        if abs(M[c][c]) < 1e-12:
            continue
        for r in range(c + 1, d):
            f = M[r][c] / M[c][c]
            if f:
                for q in range(c, d):
                    M[r][q] -= f * M[c][q]
                b[r] -= f * b[c]
    out = [0.0] * d
    for c in range(d - 1, -1, -1):
        s = b[c] - sum(M[c][q] * out[q] for q in range(c + 1, d))
        out[c] = s / M[c][c] if abs(M[c][c]) > 1e-12 else 0.0
    return out


def predict(w, x):
    return w[0] + sum(a * b for a, b in zip(w[1:], x))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    ap.add_argument("--horizon", type=float, default=120)
    # Retired 2026-09-29 (docs/brain-v3.md): the advantage re-ranker's held-out choice R2 stayed at
    # 0.008 on 82k rows (gate 0.1), so it never played. Thompson sampling in the strategist explores
    # instead. --advantage trains it again; the danger model and the skill stats always train.
    ap.add_argument("--advantage", action="store_true", help="also train the retired advantage re-ranker")
    a = ap.parse_args()
    feats = common.load_features()
    files = sorted(glob.glob(os.path.join(a.state, "data", "**", "*.jsonl.gz"), recursive=True))
    # Split by run, never by row: rows of one run are alike, so a row split would flatter the model.
    # A fifth is the test set (the R2 the loop's gate reads); 15% stops the tree boosting early.
    rng = random.Random(7)
    part = {f: rng.random() for f in files}
    choices = []
    ap_rows = list(examples(files, feats, a.horizon, choices))
    rows = ap_rows if a.advantage else []
    hazard = net = None
    keys, stats, kind = {}, {"r2": None, "adv_r2": None}, "retired"
    try:
        import numpy  # noqa: F401
        if a.advantage:
            keys, stats = train_trees(rows, part)
            kind = "trees"
        else:
            # 2026-09-30: the neural brain (mlp.py, ~110k parameters) replaces the retired trees.
            net, stats = train_mlp(ap_rows, part, choices)
            if net:
                kind, rows = "mlp", ap_rows
        hazard = train_hazard(files, feats, part)
    except ImportError:
        if a.advantage:
            keys, stats = train_linear(rows, part)
            kind = "linear"
    ys = [y for _, _, _, y, _ in rows]
    scale = max(0.1, statistics_std(ys)) if ys else 1.0
    st_path = os.path.join(a.state, "state.json")
    st = common.read_json(st_path, {})
    gen = st.get("gen", 0)
    model = {"id": "m%d" % gen, "kind": kind, "features": feats, "keys": keys, "scale": round(scale, 4),
             "horizon": a.horizon, "rows": len(ys), "runs": len(files), "r2_heldout": stats["r2"],
             "adv_r2_heldout": stats["adv_r2"], "adv_lo_heldout": stats.get("adv_lo"),
             "sign_check": stats.get("sign"),
             "trained": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")}
    if hazard:
        model["hazard"] = hazard
    if net:
        model["mlp"] = net  # "keys" stays empty: older mods read an empty model and play the rules
    # Brain v2: per action key, how often it works, how long it takes, how often it kills us.
    model["skills"] = skillstats.train_skill_stats(files, feats)
    common.write_json(os.path.join(a.state, "learned.json"), model)
    if st:
        st["model_rows"] = len(ys)
        st["model_r2"] = stats["r2"]
        st["model_adv_r2"] = stats["adv_r2"]
        st["model_adv_lo"] = stats.get("adv_lo")
        st["model_sign"] = stats.get("sign")
        st["model_keys"] = len(net["heads"]) - 1 if net else len(keys)
        st["model_kind"] = kind
        st["hazard_auc"] = hazard["auc"] if hazard else None
        st["skill_stats"] = {k: v for k, v in model["skills"].items() if "@" not in k}
        common.write_json(st_path, st)
    if net:
        print("neural brain: %d parameters, %d heads" % (net["params"], len(net["heads"])))
    sign = stats.get("sign")
    if sign and sign["agree"] is not None:
        sign_text = "sign check %.2f (5%% bound %.2f) on %d deviations in %d games" % (
            sign["agree"], sign["lo"], sign["n"], sign["games"])
    else:
        sign_text = "sign check: too few deviations yet (n=%d)" % (sign["n"] if sign else 0)
    print("learned model m%d (%s): %d decisions from %d runs, %d action kinds, held-out R2 %s (choice part %s, 5%% bound %s), %s"
          % (gen, kind, len(ys), len(files), len(net["heads"]) - 1 if net else len(keys), stats["r2"], stats["adv_r2"], stats.get("adv_lo"), sign_text))
    if hazard:
        print("danger model: %d decisions (%d followed by a death), held-out AUC %s, %d trees"
              % (hazard["rows"], hazard["death_rows"], hazard["auc"], len(hazard["trees"])))


def train_hazard(files, feats, part):
    """The danger model: the chance of dying within HAZARD_S game seconds after a decision, from
    the state and the action kind (one-hot). Every decision counts here, emergencies and reflexes
    most of all: that's where the bot dies. Deaths are frequent (about 3 per game) and their label
    is exact, so this learns fast; on the first 598 games held-out AUC was 0.87 (2026-09-28)."""
    import numpy as np
    import gbt
    rows = []
    for f in files:
        try:
            with gzip.open(f, "rt", encoding="utf-8") as fh:
                rs = [json.loads(l) for l in fh if l.strip()]
        except (OSError, ValueError):
            continue
        deaths = [r["gs"] for r in rs if r["k"] == "death"]
        for r in rs:
            if r["k"] == "d" and len(r["x"]) == len(feats):
                died = any(r["gs"] < t <= r["gs"] + HAZARD_S for t in deaths)
                rows.append((part[f], r["a"], r["x"], 1.0 if died else 0.0))
    counts = {}
    for p, k, _, _ in rows:
        if p >= 0.35:
            counts[k] = counts.get(k, 0) + 1
    keys = sorted(k for k, n in counts.items() if n >= 30)
    col = {k: i for i, k in enumerate(keys)}

    def arr(rs):
        X = np.zeros((len(rs), len(feats) + len(keys)))
        for i, (_, k, x, _) in enumerate(rs):
            X[i, :len(feats)] = x
            if k in col:
                X[i, len(feats) + col[k]] = 1
        return X, np.array([r[3] for r in rs])

    tr = [r for r in rows if r[0] >= 0.35]
    va = [r for r in rows if 0.2 <= r[0] < 0.35]
    te = [r for r in rows if r[0] < 0.2]
    if len(tr) < MIN_ROWS or len(va) < MIN_ROWS or sum(r[3] for r in tr) < 20:
        return None
    Xt, yt = arr(tr)
    Xv, yv = arr(va)
    m = gbt.fit(Xt, yt, np.ones(len(yt)), Xv, yv, np.ones(len(yv)), trees=300, depth=4, lr=0.05, min_leaf=40)
    auc = None
    if te:
        Xe, ye = arr(te)
        auc = roc_auc(gbt.predict(m, Xe), ye)
    # The bias goes into the first tree's leaves: the game adds up trees only.
    if m["trees"]:
        m["trees"][0]["v"] = [round(v + m["bias"], 6) for v in m["trees"][0]["v"]]
    return {"keys": keys, "trees": m["trees"], "horizon": HAZARD_S, "auc": auc,
            "rows": len(rows), "death_rows": int(sum(r[3] for r in rows))}


def roc_auc(p, y):
    """Chance a random death-row is ranked riskier than a random safe row (ties count half)."""
    import numpy as np
    pos = y == 1
    if pos.sum() < 10 or (~pos).sum() < 10:
        return None
    order = np.argsort(p, kind="mergesort")
    ranks = np.empty(len(p))
    ranks[order] = np.arange(1, len(p) + 1)
    # average ranks over ties
    for v in np.unique(p):
        tie = p == v
        if tie.sum() > 1:
            ranks[tie] = ranks[tie].mean()
    return round(float((ranks[pos].sum() - pos.sum() * (pos.sum() + 1) / 2) / (pos.sum() * (~pos).sum())), 3)


def by_key_rows(rows, part, lo, hi):
    """Rows of runs whose split number is in [lo, hi), grouped per action kind and per skill."""
    out = {}
    for f, key, x, y, w in rows:
        if lo <= part[f] < hi:
            for k in (key, key.split(":")[0]):
                out.setdefault(k, []).append((x, y, w))
    return out


def train_trees(rows, part):
    """A tree baseline V(state) for the progress that follows whatever is chosen, then per action
    kind small trees for its advantage over V. In the game only differences between options count,
    so V cancels out and only the advantage trees ship (brains/Learned.java).

    adv_r2 is measured against the same tree baseline for every model kind. The old linear model
    was measured against its own, weaker, linear baseline, which let state effects it missed pass
    for choice effects: 0.05 there was about 0.02 here on the same data (2026-09-28)."""
    import numpy as np
    import gbt

    def arr(rs):
        return (np.array([r[0] for r in rs], dtype=float), np.array([r[1] for r in rs], dtype=float),
                np.array([r[2] for r in rs], dtype=float))

    def split(lo, hi):
        return [(k, x, y, w) for f, k, x, y, w in rows if lo <= part[f] < hi]

    tr, va, te = split(0.35, 1.01), split(0.2, 0.35), split(0, 0.2)
    if len(tr) < MIN_ROWS or len(va) < MIN_ROWS:
        return {}, {"r2": None, "adv_r2": None}
    Xt, yt, wt = arr([r[1:] for r in tr])
    Xv, yv, wv = arr([r[1:] for r in va])
    V = gbt.fit(Xt, yt, wt, Xv, yv, wv, depth=4, lr=0.05, min_leaf=40)
    groups_t, groups_v = {}, {}
    for (k, x, y, w), b in zip(tr, gbt.predict(V, Xt)):
        for g in (k, k.split(":")[0]):
            groups_t.setdefault(g, []).append((x, y - b, w))
    for (k, x, y, w), b in zip(va, gbt.predict(V, Xv)):
        for g in (k, k.split(":")[0]):
            groups_v.setdefault(g, []).append((x, y - b, w))
    keys = {}
    for g, rs in groups_t.items():
        if len(rs) < 2 * MIN_ROWS:
            continue
        X, r, w = arr(rs)
        vs = groups_v.get(g, [])
        if len(vs) >= 30:
            Xv2, rv2, wv2 = arr(vs)
            m = gbt.fit(X, r, w, Xv2, rv2, wv2, trees=80, depth=3, lr=0.1, min_leaf=30, patience=15,
                        init=(np.zeros(len(r)), np.zeros(len(rv2))))
            if not m["trees"]:
                continue  # its advantage doesn't hold up on other runs: the key gets no say
        else:
            m = gbt.fit(X, r, w, trees=10, depth=2, lr=0.1, min_leaf=30, init=(np.zeros(len(r)), None))
        keys[g] = {"trees": m["trees"]}
    # Held-out check on runs neither the baseline nor the advantage trees saw.
    r2 = adv_r2 = None
    if te:
        Xe, ye, _ = arr([r[1:] for r in te])
        be = gbt.predict(V, Xe)
        adv = np.zeros(len(te))
        for i, (k, x, _, _) in enumerate(te):
            m = keys.get(k) or keys.get(k.split(":")[0])
            if m:
                adv[i] = gbt.predict({"bias": 0.0, "trees": m["trees"]}, Xe[i:i + 1])[0]
        r2 = rsq(list(zip(be + adv, ye)))
        adv_r2 = rsq(list(zip(adv, ye - be)))
    return keys, {"r2": r2, "adv_r2": adv_r2}


def train_mlp(rows, part, choices=None):
    """The neural brain (mlp.py). Trained on runs with split >= 0.35, stopped early on 0.2-0.35,
    tested on the rest. adv_r2 is measured like the trees': the net's choice part (its action head
    minus its own "_v") against what a tree baseline of the state leaves unexplained. A net that only
    learned the state can't pass the gate."""
    import numpy as np
    import gbt
    import mlp

    tr = [(k, x, y, w) for f, k, x, y, w in rows if part[f] >= 0.35]
    va = [(k, x, y, w) for f, k, x, y, w in rows if 0.2 <= part[f] < 0.35]
    te = [(k, x, y, f) for f, k, x, y, w in rows if part[f] < 0.2]
    test_choices = [choices[i] if choices is not None else (0, None)
                    for i, r in enumerate(rows) if part[r[0]] < 0.2]
    sign = sign_check([], [], [], [])
    if len(tr) < 20 * mlp.MIN_HEAD_ROWS or len(va) < MIN_ROWS:
        return None, {"r2": None, "adv_r2": None, "sign": sign}
    # The newest 300k training rows at most: keeps a generation's training under ~6 minutes.
    tr = tr[-300000:]
    heads = mlp.heads_for(tr)
    scale = max(0.1, statistics_std([r[2] for r in tr]))
    layers, sizes = mlp.fit(tr, va, heads, scale)
    r2 = adv_r2 = adv_lo = None
    if te:
        col = {h: i for i, h in enumerate(heads)}
        Xe = np.array([r[1] for r in te], dtype=float)
        ye = np.array([r[2] for r in te], dtype=float)
        out = mlp.predict(layers, heads, scale, Xe)
        sign = sign_check(te, test_choices, heads, out)
        Xt = np.array([r[1] for r in tr], dtype=float)
        yt = np.array([r[2] for r in tr], dtype=float)
        V = gbt.fit(Xt, yt, np.array([r[3] for r in tr], dtype=float), depth=4, lr=0.05, min_leaf=40, trees=150)
        be = gbt.predict(V, Xe)
        pred, adv = [], []
        for i, (k, _, _, _) in enumerate(te):
            j = col.get(k, col.get(k.split(":")[0]))
            q = out[i, j] if j is not None else out[i, 0]
            pred.append((q, ye[i]))
            adv.append((q - out[i, 0], ye[i] - be[i]))
        r2, adv_r2 = rsq(pred), rsq(adv)
        adv_lo = gain_lower_bound(adv, [r[3] for r in te])
    return mlp.export(layers, sizes, heads, scale), {"r2": r2, "adv_r2": adv_r2, "adv_lo": adv_lo, "sign": sign}


def sign_check(rows, choices, heads, out, draws=400, seed=7):
    """Held-out (key, x, y, run) rows: does Q(chosen) - Q(rules) have the sign of y - V(x)?

    Resample whole games, as in gain_lower_bound: one game's decisions aren't independent.
    Keep the counts when data is thin so the loop can report how many usable deviations it has.
    """
    col = {h: i for i, h in enumerate(heads)}
    per, better, worse = {}, [], []
    for (key, _, y, run), (i, options), pred in zip(rows, choices, out):
        if i <= 0 or not options:
            continue
        chosen = col.get(key, col.get(key.split(":")[0]))
        rules = col.get(options[0], col.get(options[0].split(":")[0]))
        if chosen is None or rules is None:
            continue
        gap = float(pred[chosen] - pred[rules])
        if gap == 0:
            continue
        actual = float(y - pred[col["_v"]])
        g = per.setdefault(run, [0, 0])
        g[0] += int(gap * actual > 0)
        g[1] += 1
        (better if gap > 0 else worse).append(actual)
    games = list(per.values())
    n = sum(g[1] for g in games)
    result = {"n": n, "games": len(games), "agree": None, "lo": None,
              "mean_gap_ok": None, "mean_gap_bad": None}
    if n < 30 or len(games) < 10:
        return result
    rng = random.Random(seed)
    stats = []
    for _ in range(draws):
        pick = [games[rng.randrange(len(games))] for _ in games]
        stats.append(sum(g[0] for g in pick) / sum(g[1] for g in pick))
    stats.sort()
    result.update(agree=round(sum(g[0] for g in games) / n, 4), lo=round(stats[int(0.05 * draws)], 4),
                  mean_gap_ok=round(sum(better) / len(better), 4) if better else None,
                  mean_gap_bad=round(sum(worse) / len(worse), 4) if worse else None)
    return result


def gain_lower_bound(adv, runs, draws=400, seed=7):
    """How much of the unexplained progress the choice part explains on unseen games, at its 5th
    percentile over games resampled whole (rows of one game move together).

    Why not a fixed R2 bar: R2 is capped by how random one decision's outcome is, not by how good
    the choices are. On synthetic games where the net picked the better action 92% of the time, its
    choice R2 was 0.02, so the old 0.1 gate (and the trees' before it) could never pass, whatever
    the model learned. Above zero here means "better than knowing only the state, on games it never
    saw"; whether it helps the bot is then the in-game race's call (learned.weight)."""
    per = {}
    for (a, r), f in zip(adv, runs):
        g = per.setdefault(f, [0.0, 0.0, 0.0, 0])
        g[0] += r * r - (r - a) ** 2   # squared error removed by the choice part
        g[1] += r * r
        g[2] += r
        g[3] += 1
    games = list(per.values())
    if len(games) < 10:
        return None
    rng = random.Random(seed)
    stats = []
    for _ in range(draws):
        pick = [games[rng.randrange(len(games))] for _ in games]
        n = sum(g[3] for g in pick)
        mu = sum(g[2] for g in pick) / n
        tot = sum(g[1] for g in pick) - n * mu * mu
        stats.append(sum(g[0] for g in pick) / tot if tot > 0 else 0.0)
    stats.sort()
    return round(stats[int(0.05 * draws)], 4)


def train_linear(rows, part):
    """The old model, kept for machines without numpy: ridge baseline plus per-key ridge advantage."""
    by_key = by_key_rows(rows, part, 0.2, 1.01)
    test = [(k, x, y) for f, k, x, y, w in rows if part[f] < 0.2]
    allX, allY, allW = [], [], []
    for k, rs in by_key.items():
        if ":" not in k:
            allX += [r[0] for r in rs]
            allY += [r[1] for r in rs]
            allW += [r[2] for r in rs]
    models, base = {}, None
    if len(allX) >= MIN_ROWS:
        base = ridge(allX, allY, allW, RIDGE)
        for k, rs in by_key.items():
            if len(rs) < MIN_ROWS:
                continue
            X, Y, W = [r[0] for r in rs], [r[1] for r in rs], [r[2] for r in rs]
            resid = [y - predict(base, x) for x, y in zip(X, Y)]
            adv = ridge(X, resid, W, RIDGE * 4)
            models[k] = [round(b + a, 5) for b, a in zip(base, adv)]
    r2 = adv_r2 = None
    if test and base:
        preds, advs = [], []
        for key, x, y in test:
            w = models.get(key) or models.get(key.split(":")[0])
            if w:
                p, b = predict(w, x), predict(base, x)
                preds.append((p, y))
                advs.append((p - b, y - b))
        r2, adv_r2 = rsq(preds), rsq(advs)
    return models, {"r2": r2, "adv_r2": adv_r2}


def rsq(pairs):
    if len(pairs) < 20:
        return None
    my = sum(y for _, y in pairs) / len(pairs)
    ss_res = sum((y - p) ** 2 for p, y in pairs)
    ss_tot = sum((y - my) ** 2 for _, y in pairs) or 1
    return round(1 - ss_res / ss_tot, 3)


def statistics_std(ys):
    if len(ys) < 2:
        return 1.0
    m = sum(ys) / len(ys)
    return math.sqrt(sum((y - m) ** 2 for y in ys) / (len(ys) - 1))


if __name__ == "__main__":
    main()
