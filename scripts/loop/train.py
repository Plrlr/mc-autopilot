#!/usr/bin/env python3
"""Trains the learned brain from every run the loop (and the laptop) has played.

    python scripts/loop/train.py --state DIR [--horizon 120]

Reads DIR/data/**/*.jsonl.gz (written by loop.py update), writes DIR/learned.json and a line of
stats into DIR/state.json ("model_rows", "model_r2").

For each decision it measures what happened next: progress over the following `horizon` game
seconds, from a potential function of the state features (tools, key items, milestones and
checkpoints; losing the inventory to a death shows up as a drop) minus a penalty per death in that
window. Then one ridge regression per action kind ("collect:log", "explore:lava", ...) predicts that
progress from the state. The mod ranks options by those predictions (brains/Learned.java).

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


def potential(x, idx):
    return sum(w * x[idx[n]] for n, w in POTENTIAL.items() if n in idx)


def examples(files, feats, horizon):
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
    a = ap.parse_args()
    feats = common.load_features()
    files = sorted(glob.glob(os.path.join(a.state, "data", "**", "*.jsonl.gz"), recursive=True))
    # Hold out a fifth of the runs (by file) to check the model predicts runs it hasn't seen.
    rng = random.Random(7)
    held = {f for f in files if rng.random() < 0.2}
    by_key, test = {}, []
    for f, key, x, y, w in examples(files, feats, a.horizon):
        if f in held:
            test.append((key, x, y))
            continue
        for k in (key, key.split(":")[0]):  # per action kind, and per skill as a fallback
            by_key.setdefault(k, ([], [], []))
            by_key[k][0].append(x)
            by_key[k][1].append(y)
            by_key[k][2].append(w)
    # Baseline: how much progress follows this state whatever is chosen. Much of it is just the
    # state (little in the bag -> much to gain). Each action kind then learns its advantage over
    # the baseline, which is what separates choices at the same moment.
    allX, allY, allW = [], [], []
    for k, (X, Y, W) in by_key.items():
        if ":" not in k:
            allX += X
            allY += Y
            allW += W
    models = {}
    base = None
    if len(allX) >= MIN_ROWS:
        base = ridge(allX, allY, allW, RIDGE)
        for k, (X, Y, W) in by_key.items():
            if len(X) < MIN_ROWS:
                continue
            resid = [y - predict(base, x) for x, y in zip(X, Y)]
            adv = ridge(X, resid, W, RIDGE * 4)
            models[k] = [round(b + a, 5) for b, a in zip(base, adv)]
    ys = [y for k, v in by_key.items() if ":" not in k for y in v[1]]
    scale = max(0.1, statistics_std(ys))
    # R^2 on held-out runs: how much of the variation the model explains. r2 is for progress as a
    # whole (flattered: the state alone predicts much of it); adv_r2 is for the part the choice
    # makes, beyond the baseline - the honest measure of whether the model tells actions apart.
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
    st_path = os.path.join(a.state, "state.json")
    st = common.read_json(st_path, {})
    gen = st.get("gen", 0)
    model = {"id": "m%d" % gen, "features": feats, "keys": models, "scale": round(scale, 4),
             "horizon": a.horizon, "rows": len(ys), "runs": len(files), "r2_heldout": r2, "adv_r2_heldout": adv_r2,
             "trained": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")}
    common.write_json(os.path.join(a.state, "learned.json"), model)
    if st:
        st["model_rows"] = len(ys)
        st["model_r2"] = r2
        st["model_adv_r2"] = adv_r2
        st["model_keys"] = len(models)
        common.write_json(st_path, st)
    print("learned model m%d: %d decisions from %d runs, %d action kinds, held-out R2 %s (choice part %s)"
          % (gen, len(ys), len(files), len(models), r2, adv_r2))


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
