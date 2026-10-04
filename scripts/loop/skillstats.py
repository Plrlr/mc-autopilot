#!/usr/bin/env python3
"""Skill stats for brain v2: how often each action works, how long it takes, how often it kills us.

Reads the compact rows loop.py writes ("s" rows: one per skill end; "d" rows: decisions with the
state features; "death" rows), and counts per action key, overall and per context:

    "build_portal"          every try
    "build_portal@nether"   tries in the Nether (or @end, @overworld)
    "build_portal@night"    tries started at night (overworld)
    "build_portal@under"    tries started underground

Each entry: n tries, ok successes, died (a death during the try), sec (median seconds of a
successful try), spent (mean seconds per try, fails included), codes (the three most common
fail codes). The mod smooths these with the route spec's prior (brains/SkillStats), so a key with
3 tries says little and one with 300 says a lot. Small tables, so the game reads them at once.

    python scripts/loop/skillstats.py --state DIR     (prints the table; train.py embeds it)
"""
import argparse
import collections
import glob
import gzip
import json
import os
import statistics

MIN_TRIES = 3   # keys with fewer tries in a context are left out (the overall entry still counts them)


def situation(x, idx):
    """The moment (2026-10-04, brains/SkillStats.situation, gene brain.situations): hurt (8 health or
    less), a monster within 6 blocks, both, and hungry with nothing to eat. Features: hp = health/20,
    food = hunger/20, cooked = food carried/16, hostile_dist = blocks/16 (1 = none in sight)."""
    if any(k not in idx or idx[k] >= len(x) for k in ("hp", "food", "cooked", "hostile_dist")):
        return []
    out = []
    hurt = x[idx["hp"]] * 20 <= 8 + 1e-6
    mob = x[idx["hostile_dist"]] * 16 <= 6 + 1e-6
    if x[idx["food"]] * 20 < 18 - 1e-6 and x[idx["cooked"]] <= 0:
        out.append("nofood")
    if mob:
        out.append("mob")
    if hurt:
        out.append("hurt")
    if hurt and mob:
        out.append("hurt_mob")
    return out


def context(x, idx):
    """Contexts a skill start belongs to, from the decision row's features just before it: the
    place (dimension, night, underground), then the moment (situation)."""
    if x is None:
        return []
    out = []
    if x[idx["nether"]] > 0.5:
        out.append("nether")
    elif x[idx["end"]] > 0.5:
        out.append("end")
    else:
        out.append("overworld")
        if x[idx["night"]] > 0.5:
            out.append("night")
    if x[idx["underground"]] > 0.5:
        out.append("under")
    return out + situation(x, idx)


def rows_of(path):
    try:
        with gzip.open(path, "rt", encoding="utf-8") as fh:
            return [json.loads(l) for l in fh if l.strip()]
    except (OSError, ValueError):
        return []


def tally(files, feats):
    """{key or key@ctx: {"n", "ok", "died", "ok_secs": [...], "spent": [...], "codes": Counter}}"""
    idx = {n: i for i, n in enumerate(feats)}
    acc = collections.defaultdict(lambda: {"n": 0, "ok": 0, "died": 0, "ok_secs": [], "spent": [],
                                           "codes": collections.Counter()})
    for f in files:
        rows = rows_of(f)
        dec = [r for r in rows if r["k"] == "d" and len(r.get("x", [])) == len(feats)]
        for r in rows:
            # Interrupted: a reflex, the brain or the user stopped it. That says nothing about the skill.
            if r["k"] != "s" or r.get("c") == "INTERRUPTED":
                continue
            start = r["gs"] - r.get("sec", 0)
            # The state when the skill started: the last decision at or before its start.
            x = None
            for d in dec:
                if d["gs"] <= start + 1:
                    x = d["x"]
                else:
                    break
            died = r.get("c") == "DIED"
            for key in [r["a"]] + [r["a"] + "@" + c for c in context(x, idx)]:
                e = acc[key]
                e["n"] += 1
                e["spent"].append(r.get("sec", 0))
                if r.get("ok"):
                    e["ok"] += 1
                    e["ok_secs"].append(r.get("sec", 0))
                elif r.get("c"):
                    e["codes"][r["c"]] += 1
                if died:
                    e["died"] += 1
    return acc


def summarize(acc):
    out = {}
    for key, e in sorted(acc.items()):
        if "@" in key and e["n"] < MIN_TRIES:
            continue
        out[key] = {
            "n": e["n"], "ok": e["ok"], "died": e["died"],
            "sec": round(statistics.median(e["ok_secs"]), 1) if e["ok_secs"] else None,
            "spent": round(sum(e["spent"]) / len(e["spent"]), 1) if e["spent"] else 0,
            "codes": dict(e["codes"].most_common(3)),
        }
    return out


def train_skill_stats(files, feats):
    return summarize(tally(files, feats))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    a = ap.parse_args()
    import common  # noqa: E402  (scripts/loop on the path when run from there)
    feats = common.load_features()
    files = sorted(glob.glob(os.path.join(a.state, "data", "**", "*.jsonl.gz"), recursive=True))
    stats = train_skill_stats(files, feats)
    for k, v in stats.items():
        if "@" in k:
            continue
        print("%-28s %5d tries  %5.1f%% ok  %3d died  %6s s  %s" % (
            k, v["n"], 100.0 * v["ok"] / v["n"], v["died"], v["sec"], v["codes"]))


if __name__ == "__main__":
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    main()
