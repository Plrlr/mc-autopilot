#!/usr/bin/env python3
"""The learning loop: evolve the bot's genes on paired seeds, keep only what clearly helps.

    python scripts/loop/loop.py propose --state DIR --sha SHA --out runs.json
    python scripts/loop/loop.py update  --state DIR --batch DIR
    python scripts/loop/loop.py status  --state DIR

DIR is the loop's folder on the trial-results branch (loop/): state.json, history.jsonl, the
learned model, and compact training data. One "generation" plays the champion and a few
challengers on the same fresh random seeds (plus a few data runs for the learned brain), then
scores every run and decides:

- Racing (irace / F-Race): a challenger stays in the race across generations while its paired
  score difference against the champion looks promising, and replaces the champion only when
  it's clearly better (enough pairs, one-sided t above a bar). One promotion per generation.
- Archive (Darwin Goedel Machine, MAP-Elites): every genome ever tried is kept with its record;
  new challengers are mostly mutations of the champion, sometimes of another good genome, so a
  line that looked slightly worse can still lead somewhere.
- Adaptive mutation: 1-3 genes per child, step sizes that grow for genes whose changes won and
  shrink for those that lost, and genes picked more often when their changes have paid off.

Seeds are fresh every generation, so the champion is a policy for Minecraft, not for 4 worlds.
"""
import argparse
import datetime
import glob
import gzip
import json
import os
import random
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

DEFAULT_SETTINGS = {
    "seeds_per_gen": 4,        # every genome of a generation plays these seeds (paired)
    "max_genomes": 4,          # champion + contenders + new challengers per generation
    "data_runs": 2,            # extra champion runs with exploration on: data for the learned brain
    "explore_data": 0.15,      # learned.explore in the data runs
    "minutes": 20,             # game minutes per run
    "scenario": "natural",
    "max_gens_per_day": 36,
    # Plain drawing, no extra mods: the cloud already plays at 0.98x real time that way, and a
    # 10 fps cap halved the game speed (benchmark 36323172968). Change here to experiment.
    "lean": False,
    "perf_mods": "",
    "accept_pairs": 8,         # a challenger needs this many paired seeds...
    "accept_t": 2.0,           # ...and a one-sided t this high (many looks per challenger: keep it strict)...
    "min_gain": 0.3,           # ...and a mean gain at least this big (a real difference, not a fluke)
    "drop_after_pairs": 4,     # below zero after this many pairs: out
    "max_pairs": 24,           # never promoted after this many pairs: out (not better enough to tell)
    "sigma0": 0.15,            # starting mutation step, as a fraction of each gene's range
}


def today():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")


def load_state(d, genes):
    st = common.read_json(os.path.join(d, "state.json"))
    if st is None:
        st = {"version": 1, "gen": 0, "champion": "g0", "next_id": 1, "days": {}, "pending": None,
              "genomes": {"g0": new_genome("g0", None, {}, 0, [], "the hand-tuned defaults (pre-loop code)")},
              "sigma": {}, "credit": {}, "settings": {}}
        st["genomes"]["g0"]["status"] = "champion"
    for k, v in DEFAULT_SETTINGS.items():
        st["settings"].setdefault(k, v)
    st["gene_specs"] = genes  # defaults and limits, for the dashboard
    for n in genes:
        st["sigma"].setdefault(n, st["settings"]["sigma0"])
        st["credit"].setdefault(n, {"n": 0, "sum": 0.0})
    return st


def new_genome(gid, parent, changed, gen, mutated, note):
    return {"id": gid, "parent": parent, "genes": changed, "born": gen, "code": None, "status": "new",
            "mutated": mutated, "note": note, "evals": [], "pairs": []}


# ---------------------------------------------------------------------------------- mutation

def pick_genes(st, genes, rng, k):
    """Genes to mutate: more often those whose past changes paid off (softmax of mean credit),
    always with some chance for every gene."""
    names = [n for n in genes if not n.startswith("learned.") or st.get("model_rows", 0) >= 500]
    w = []
    for n in names:
        c = st["credit"][n]
        m = c["sum"] / c["n"] if c["n"] else 0.0
        w.append(2.718 ** max(-2.0, min(2.0, m)))
    out = []
    while len(out) < min(k, len(names)):
        n = rng.choices(names, weights=w)[0]
        if n not in out:
            out.append(n)
    return out


def mutate(st, genes, parent, rng):
    base = dict(st["genomes"][parent]["genes"])
    k = rng.choices([1, 2, 3], weights=[0.5, 0.35, 0.15])[0]
    chosen = pick_genes(st, genes, rng, k)
    for n in chosen:
        g = genes[n]
        cur = common.full_genes(genes, base)[n]
        if g["kind"] == "BOOL":
            v = 1.0 - cur
        else:
            span = g["max"] - g["min"]
            v = cur
            for _ in range(10):  # make sure a whole-number gene actually moves
                v = common.clamp(g, cur + rng.gauss(0, st["sigma"][n] * span))
                if v != cur:
                    break
        if v == g["def"]:
            base.pop(n, None)
        else:
            base[n] = v
    return base, chosen


def same_genes(a, b):
    return json.dumps(a, sort_keys=True) == json.dumps(b, sort_keys=True)


# ---------------------------------------------------------------------------------- propose

def cmd_propose(a):
    genes = common.load_genes()
    st = load_state(a.state, genes)
    if a.override:
        st["settings"].update(json.loads(a.override))
    s = st["settings"]
    out = {"runs": [], "gen": st["gen"], "reason": ""}
    if os.path.exists(os.path.join(a.state, "STOP")):
        out["reason"] = "loop/STOP exists"
    elif st["days"].get(today(), 0) >= s["max_gens_per_day"]:
        out["reason"] = "daily cap of %d generations reached" % s["max_gens_per_day"]
    elif st.get("pending"):
        # A generation that never reported back (cancelled job): start over with a fresh one.
        st["pending"] = None
    if out["reason"]:
        common.write_json(a.out, out)
        print(out["reason"])
        return
    rng = random.Random()
    st["gen"] += 1
    gen = st["gen"]
    st["days"][today()] = st["days"].get(today(), 0) + 1
    champ = st["champion"]
    lineup = [champ]
    # Contenders still in the race, most promising first.
    cont = [g for g in st["genomes"].values() if g["status"] == "contender"]
    cont.sort(key=lambda g: -common.paired_t([p[2] for p in g["pairs"]]))
    for g in cont[: s["max_genomes"] - 1]:
        lineup.append(g["id"])
    # New challengers: mutations of the champion, sometimes of another strong genome.
    tries = 0
    while len(lineup) < s["max_genomes"] and tries < 50:
        tries += 1
        elites = [g for g in st["genomes"].values() if g["status"] in ("retired", "contender") and g["evals"]]
        parent = champ
        if elites and rng.random() < 0.25:
            elites.sort(key=lambda g: -common.mean([e[2] for e in g["evals"]]))
            parent = rng.choice(elites[:5])["id"]
        changed, chosen = mutate(st, genes, parent, rng)
        if any(same_genes(changed, st["genomes"][x]["genes"]) for x in lineup):
            continue
        gid = "g%d" % st["next_id"]
        st["next_id"] += 1
        desc = ", ".join("%s %s" % (n, fmt(common.full_genes(genes, changed)[n])) for n in chosen)
        st["genomes"][gid] = new_genome(gid, parent, changed, gen, chosen, desc)
        st["genomes"][gid]["status"] = "contender"
        # A child of a code change (not yet merged into main) plays that code too.
        st["genomes"][gid]["code"] = st["genomes"][parent].get("code")
        lineup.append(gid)
    seeds = ["L%d-%s" % (gen, "".join(rng.choice("abcdefghjkmnpqrstuvwxyz23456789") for _ in range(6)))
             for _ in range(s["seeds_per_gen"] + s["data_runs"])]
    runs = []
    for gid in lineup:
        full = common.full_genes(genes, st["genomes"][gid]["genes"])
        for i, seed in enumerate(seeds[: s["seeds_per_gen"]]):
            runs.append(run_entry(s, gid, seed, "eval", st["genomes"][gid]["genes"], gen, i, ref_of(st, gid, a.sha)))
    # Data runs: the champion with exploration on, on their own seeds (never scored for the race).
    for i, seed in enumerate(seeds[s["seeds_per_gen"]:]):
        g = dict(st["genomes"][champ]["genes"])
        g["learned.explore"] = s["explore_data"]
        runs.append(run_entry(s, champ, seed, "data", g, gen, i, ref_of(st, champ, a.sha)))
    st["pending"] = {"gen": gen, "sha": a.sha, "lineup": lineup, "seeds": seeds, "runs": [r["name"] for r in runs],
                     "started": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")}
    common.write_json(os.path.join(a.state, "state.json"), st)
    out["runs"] = runs
    out["gen"] = gen
    common.write_json(a.out, out)
    print("generation %d: %s on %d seeds, %d data runs" % (gen, " ".join(lineup), s["seeds_per_gen"], s["data_runs"]))


def ref_of(st, gid, main_sha):
    """The commit a genome plays: its own code change if it has one, else this generation's main."""
    c = st["genomes"][gid].get("code")
    return c["sha"] if c else main_sha


def run_entry(s, gid, seed, role, changed, gen, i, ref):
    return {"name": "%s-%s-%d" % (role, gid, i), "seed": seed, "genome": gid, "role": role, "ref": ref,
            "params": json.dumps({"id": "%s@gen%d" % (gid, gen), "genes": changed}, separators=(",", ":")),
            "minutes": str(s["minutes"]), "scenario": s["scenario"],
            "lean": "true" if s["lean"] else "false", "perf_mods": s["perf_mods"]}


def fmt(v):
    return ("%g" % v) if v != int(v) else str(int(v))


# ---------------------------------------------------------------------------------- update

def cmd_update(a):
    genes = common.load_genes()
    st = load_state(a.state, genes)
    p = st.get("pending")
    if not p:
        print("nothing pending")
        return
    s = st["settings"]
    sb = common.summarizer()
    length = int(s["minutes"]) * 60
    gen = p["gen"]
    results = {}
    speeds = []
    for name in p["runs"]:
        d = os.path.join(a.batch, "trial-" + name)
        if not os.path.isdir(d):
            results[name] = None
            continue
        r = sb.read_run(d)
        if not r["final"]:
            results[name] = None  # the game never got to play (or crashed): not the genome's fault
            continue
        results[name] = {"score": common.score_run(r, length), "milestones": r["milestones"],
                         "checkpoints": r["checkpoints"], "deaths": len(r["deaths"]),
                         "death_causes": [c for c, _ in r["deaths"]]}
        sp = read_speed(d)
        if sp:
            speeds.append(sp)
        save_training_rows(a.state, gen, name, d)
    champ = p["lineup"][0]
    seeds = p["seeds"]
    by = {}  # (genome, seed index) -> score
    for name, res in results.items():
        role, gid, i = name.split("-")
        if role == "eval" and res is not None:
            by[(gid, int(i))] = res["score"]
            st["genomes"][gid]["evals"].append([gen, seeds[int(i)], res["score"]])
    # Paired differences against the champion on the same seeds.
    for gid in p["lineup"][1:]:
        g = st["genomes"][gid]
        for i in range(s["seeds_per_gen"]):
            if (gid, i) in by and (champ, i) in by:
                g["pairs"].append([gen, seeds[i], round(by[(gid, i)] - by[(champ, i)], 3)])
    decisions = race(st, genes, p["lineup"][1:], champ, gen)
    showcase = ingest_inbox(a.state, st, sb, gen)
    # History line for the dashboard.
    gen_scores = {gid: [by[(gid, i)] for i in range(s["seeds_per_gen"]) if (gid, i) in by] for gid in p["lineup"]}
    all_ok = [r for r in results.values() if r]
    reached = {}
    for r in all_ok:
        for m, _ in r["milestones"]:
            reached["m%d" % m] = reached.get("m%d" % m, 0) + 1
        for c in r["checkpoints"]:
            reached[c] = reached.get(c, 0) + 1
    prev = last_history(a.state)
    line = {
        "gen": gen, "time": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "sha": p["sha"][:7], "champion": st["champion"], "old_champion": champ,
        "champion_score": round(common.mean(gen_scores.get(champ, [])), 3),
        "scores": {g: [round(x, 2) for x in v] for g, v in gen_scores.items()},
        "genomes": {g: st["genomes"][g]["note"] for g in p["lineup"]},
        "decisions": decisions, "runs": len(p["runs"]), "runs_ok": len(all_ok),
        "reached": reached, "deaths": sum(r["deaths"] for r in all_ok),
        "speed": round(common.mean(speeds), 3) if speeds else None,
        "total_runs": (prev.get("total_runs", 0) if prev else 0) + len(all_ok),
        "game_hours": round((prev.get("game_hours", 0) if prev else 0) + len(all_ok) * length / 3600, 2),
        "model_rows": st.get("model_rows", 0),
        "showcase": showcase,
    }
    with open(os.path.join(a.state, "history.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps(line, separators=(",", ":")) + "\n")
    st["pending"] = None
    common.write_json(os.path.join(a.state, "state.json"), st)
    print("generation %d scored: champion %s (%s)" % (gen, st["champion"], "; ".join(decisions) or "no change"))


def race(st, genes, challengers, champ, gen):
    """Promote at most one challenger; drop the hopeless; adapt step sizes and gene credit."""
    s = st["settings"]
    out = []
    best, best_t = None, 0.0
    for gid in challengers:
        g = st["genomes"][gid]
        diffs = [x[2] for x in g["pairs"]]
        t = common.paired_t(diffs)
        m = common.mean(diffs)
        if len(diffs) >= s["accept_pairs"] and m >= s["min_gain"] and t >= s["accept_t"] and t > best_t:
            best, best_t = gid, t
        elif (len(diffs) >= s["drop_after_pairs"] and m <= 0) or len(diffs) >= s["max_pairs"]:
            g["status"] = "rejected"
            out.append("%s dropped (%+.2f over %d seeds)" % (gid, m, len(diffs)))
            learn_from(st, g, m)
    if best:
        g = st["genomes"][best]
        m = common.mean([x[2] for x in g["pairs"]])
        # The old champion defends: it races the new one from scratch, so a lucky promotion gets
        # reversed by the same rule that made it.
        old = st["genomes"][champ]
        old["status"] = "contender"
        old["pairs"] = []
        old["note"] = old["note"].split(" (defending")[0] + " (defending)"
        g["status"] = "champion"
        g["crowned"] = gen
        if g.get("code"):
            st["merge"] = dict(g["code"], genome=best)  # the workflow merges it into main (loop.py merge)
        st["champion"] = best
        out.append("%s is the new champion (%+.2f over %d seeds, t %.1f): %s" % (best, m, len(g["pairs"]), best_t, g["note"]))
        learn_from(st, g, m)
        # The others were measured against the old champion: they start over against the new one.
        for gid in challengers:
            if st["genomes"][gid]["status"] == "contender":
                st["genomes"][gid]["pairs"] = []
    return out


def learn_from(st, g, m):
    """Credit the mutated genes with the outcome; widen steps that won, narrow those that lost."""
    for n in g.get("mutated", []):
        if n not in st["credit"]:
            continue
        c = st["credit"][n]
        c["n"] += 1
        c["sum"] += max(-3.0, min(3.0, m))
        f = 1.4 if m > 0 else 0.85
        st["sigma"][n] = max(0.03, min(0.4, st["sigma"][n] * f))


def read_speed(d):
    for f in glob.glob(os.path.join(d, "autopilot-test.log")):
        for line in open(f, errors="replace"):
            if "SPEED " in line:
                try:
                    return float(line.split("SPEED ")[1].split()[0])
                except (IndexError, ValueError):
                    pass
    return None


def save_training_rows(state_dir, gen, name, d):
    """The learned brain's data from one run, compact: decisions (game second, state features,
    action kind, propensity, urgent) and outcomes (milestones, checkpoints, deaths with game
    seconds interpolated from wall time). Written as loop/data/gen-NNNNN/<run>.jsonl.gz."""
    rows, wall_to_gs = [], []
    events = []
    for f in sorted(glob.glob(os.path.join(d, "**", "run-*.jsonl"), recursive=True)):
        for line in open(f, errors="replace"):
            try:
                o = json.loads(line)
            except ValueError:
                continue
            if o.get("layer") == "tactician" and "x" in o and "gs" in o:
                wall_to_gs.append((o["t"], o["gs"]))
                opts = o.get("options", [])
                idx = o.get("idx", 0)
                label = opts[idx] if 0 <= idx < len(opts) else o.get("choice", "")
                rows.append({"k": "d", "gs": o["gs"], "x": o["x"], "a": action_key(label),
                             "p": o.get("prop", 1), "i": idx, "u": label in o.get("urgent", [])})
            elif o.get("event") in ("death", "milestone", "checkpoint"):
                events.append((o["t"], o["event"], o.get("detail", "")))
    if not rows:
        return
    for t, ev, detail in events:
        rows.append({"k": ev, "gs": interp(wall_to_gs, t), "detail": detail})
    rows.sort(key=lambda r: r["gs"])
    out = os.path.join(state_dir, "data", "gen-%05d" % gen, name + ".jsonl.gz")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with gzip.open(out, "wt", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, separators=(",", ":")) + "\n")


def interp(pairs, t):
    if not pairs:
        return 0
    if t <= pairs[0][0]:
        return pairs[0][1]
    for (t0, g0), (t1, g1) in zip(pairs, pairs[1:]):
        if t0 <= t <= t1:
            return g0 + (g1 - g0) * (t - t0) / max(1, t1 - t0)
    return pairs[-1][1] + (t - pairs[-1][0]) / 1000.0


def action_key(label):
    """Same as Learned.key: 'collect log:3' -> 'collect:log', 'explore cow,pig' -> 'explore:cow'."""
    skill, _, arg = label.partition(" ")
    if not arg:
        return skill
    cut = len(arg)
    for c in ":, ":
        i = arg.find(c)
        if i >= 0:
            cut = min(cut, i)
    return skill + ":" + arg[:cut]


def last_history(d):
    try:
        with open(os.path.join(d, "history.jsonl"), encoding="utf-8") as f:
            lines = f.read().strip().splitlines()
        return json.loads(lines[-1]) if lines else None
    except OSError:
        return None


def ingest_inbox(state_dir, st, sb, gen):
    """Runs played elsewhere (the laptop's showcase runs of the champion, scripts/laptop-loop.ps1)
    arrive in loop/inbox/<run>/ with the same files a cloud run has. Their decisions become
    training data; their scores are shown, but never used to promote (unpaired, other machine)."""
    inbox = os.path.join(state_dir, "inbox")
    out = []
    for d in sorted(glob.glob(os.path.join(inbox, "*"))):
        if not os.path.isdir(d):
            continue
        name = os.path.basename(d)
        info = common.read_json(os.path.join(d, "run.json"), {})
        r = sb.read_run(d)
        if r["final"]:
            length = int(info.get("minutes", 20)) * 60
            out.append({"name": name, "genome": info.get("genome", "?"), "score": common.score_run(r, length),
                        "milestones": r["milestones"], "checkpoints": r["checkpoints"], "deaths": len(r["deaths"])})
            save_training_rows(state_dir, gen, name, d)

        shutil.rmtree(d, ignore_errors=True)
    st["showcase_runs"] = st.get("showcase_runs", 0) + len(out)
    return out


def cmd_merge(a):
    """A code change that won the race goes into main: fast-forward when main hasn't moved, else a
    merge commit. On a conflict the change stays on its branch (the champion keeps playing it)."""
    import subprocess
    st_path = os.path.join(a.state, "state.json")
    st = common.read_json(st_path)
    m = st.get("merge") if st else None
    if not m:
        print("merge: nothing to merge")
        return

    def git(*args, check=True):
        return subprocess.run(["git", *args], cwd=common.ROOT, check=check, capture_output=True, text=True)
    git("fetch", "-q", "origin", "main")
    main = git("rev-parse", "FETCH_HEAD").stdout.strip()
    ok = False
    if git("merge-base", "--is-ancestor", main, m["sha"], check=False).returncode == 0:
        ok = git("push", "-q", "origin", "%s:refs/heads/main" % m["sha"], check=False).returncode == 0
    else:
        git("checkout", "-q", "--detach", main)
        r = git("-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                "merge", "--no-ff", "-m", "Merge %s: won the loop's race (%s)" % (m["branch"], m["summary"]), m["sha"], check=False)
        if r.returncode == 0:
            ok = git("push", "-q", "origin", "HEAD:refs/heads/main", check=False).returncode == 0
        else:
            git("merge", "--abort", check=False)
    g = st["genomes"].get(m["genome"], {})
    if ok:
        # It's in main now: every genome carrying this change plays main from here on.
        for other in st["genomes"].values():
            if other.get("code") and other["code"]["sha"] == m["sha"]:
                other["code_merged"] = other["code"]
                other["code"] = None
        print("merge: %s is in main" % m["branch"])
    else:
        g["merge_failed"] = True
        print("merge: couldn't merge %s into main; it keeps racing from its branch" % m["branch"])
    st["merge"] = None
    common.write_json(st_path, st)


def cmd_export(a):
    """The champion's genes as a params file (for the laptop, or the user's own game)."""
    genes = common.load_genes()
    st = load_state(a.state, genes)
    ch = st["genomes"][st["champion"]]
    common.write_json(a.out, {"id": "%s@gen%d" % (ch["id"], st["gen"]), "genes": ch["genes"]})
    print(ch["id"])


# ---------------------------------------------------------------------------------- status

def cmd_status(a):
    genes = common.load_genes()
    st = load_state(a.state, genes)
    ch = st["genomes"][st["champion"]]
    print("generation %d, champion %s (%s)" % (st["gen"], ch["id"], ch["note"]))
    full = common.full_genes(genes, ch["genes"])
    for n, g in genes.items():
        if full[n] != g["def"]:
            print("  %-28s %s (default %s)" % (n, fmt(full[n]), fmt(g["def"])))
    for g in st["genomes"].values():
        if g["status"] == "contender":
            d = [x[2] for x in g["pairs"]]
            print("  contender %s: %+.2f over %d seeds (t %.1f) - %s" % (g["id"], common.mean(d), len(d), common.paired_t(d), g["note"]))


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p1 = sub.add_parser("propose")
    p1.add_argument("--state", required=True)
    p1.add_argument("--sha", required=True)
    p1.add_argument("--out", required=True)
    p1.add_argument("--override", default="", help="settings to change, as JSON (smoke tests)")
    p2 = sub.add_parser("update")
    p2.add_argument("--state", required=True)
    p2.add_argument("--batch", required=True)
    p3 = sub.add_parser("status")
    p3.add_argument("--state", required=True)
    p4 = sub.add_parser("export")
    p4.add_argument("--state", required=True)
    p4.add_argument("--out", required=True)
    p5 = sub.add_parser("merge")
    p5.add_argument("--state", required=True)
    a = ap.parse_args()
    {"propose": cmd_propose, "update": cmd_update, "status": cmd_status, "export": cmd_export, "merge": cmd_merge}[a.cmd](a)


if __name__ == "__main__":
    main()
