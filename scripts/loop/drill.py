#!/usr/bin/env python3
"""Portal drill for a pull request: a quick paired check before a change waits for the loop's race.

    python scripts/loop/drill.py plan --base BASE_SETTINGS --head HEAD_SETTINGS --state STATE_JSON --n 3
    python scripts/loop/drill.py report --batch DIR --minutes 12

plan: the suggestions the PR adds to settings.json are its new behavior. Every drill start (a saved
"lava" checkpoint, else "kit") is played twice on the PR's code: the champion's genes ("off") and
the champion's genes plus the new suggestions ("on"). Prints the job matrix as JSON ([] when the PR
adds no suggestion). report: a markdown table of both sides per start, for a PR comment.

A drill is a smoke test, not a verdict: 3 pairs can't tell a small gain from noise (the loop's race
needs 16). It catches a change that breaks the cast, loops, or dies at once.
"""
import argparse
import glob
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

PORTAL = ("lava_seen", "obsidian_placed", "frame_complete", "portal_lit")


def suggestions(path):
    d = common.read_json(path, {}) or {}
    return {json.dumps(x, sort_keys=True) for x in d.get("suggest", [])}


def cmd_plan(a):
    new = suggestions(a.head) - suggestions(a.base)
    genes_on = {}
    for key in sorted(new):
        genes_on.update({k: v for k, v in json.loads(key).items() if not k.startswith("_")})
    if not genes_on:
        print("[]")
        return
    st = common.read_json(a.state, {}) or {}
    champ = st.get("genomes", {}).get(st.get("champion", ""), {}).get("genes", {})
    bank = st.get("bank", {})
    real = [c for c in bank.get("lava", []) if not c.get("synthetic")] or \
           [c for c in bank.get("kit", []) if not c.get("synthetic")]
    # Newest saves only: the drill's games wait behind the loop's, and meanwhile the loop prunes
    # the oldest saves from the release (bank_keep). A random old one was gone by the time PR #21's
    # drill ran ("no assets match the file pattern"), failing both sides in 20 s.
    real.sort(key=lambda c: c.get("gen", 0), reverse=True)
    pool = [c["asset"] for c in real[:max(2 * a.n, 6)]]
    rng = random.Random()
    starts = rng.sample(pool, min(a.n, len(pool))) if pool else []
    runs = []
    for i, asset in enumerate(starts):
        for side, genes in (("off", champ), ("on", dict(champ, **genes_on))):
            runs.append({"name": "drill-%s-%d" % (side, i), "seed": "drill-%d" % i, "start": asset,
                         "params": json.dumps({"id": "drill-" + side, "genes": genes}, separators=(",", ":"))})
    print(json.dumps(runs, separators=(",", ":")))


def cmd_report(a):
    sb = common.summarizer()
    length = int(a.minutes) * 60
    rows = {}
    for d in sorted(glob.glob(os.path.join(a.batch, "trial-drill-*"))):
        _, _, side, i = os.path.basename(d).split("-")
        r = sb.read_run(d)
        rows.setdefault(int(i), {})[side] = r if r["final"] else None
    out = ["### Portal drill (smoke test: 3 pairs are not a verdict, the loop's race decides)", "",
           "| start | side | score | portal steps | deaths |", "|---|---|---|---|---|"]
    diffs = []
    for i in sorted(rows):
        sc = {}
        for side in ("off", "on"):
            r = rows[i].get(side)
            if r is None:
                out.append("| %d | %s | didn't play | | |" % (i, side))
                continue
            sc[side] = common.score_run(r, length, 10)
            steps = " ".join(c for c in PORTAL if c in r["checkpoints"]) or "none"
            out.append("| %d | %s | %.2f | %s | %d |" % (i, side, sc[side], steps, len(r["deaths"])))
        if len(sc) == 2:
            diffs.append(sc["on"] - sc["off"])
    if diffs:
        out += ["", "Mean paired difference (on - off): %+.2f over %d starts." % (common.mean(diffs), len(diffs))]
    print("\n".join(out))


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("plan")
    p.add_argument("--base", required=True)
    p.add_argument("--head", required=True)
    p.add_argument("--state", required=True)
    p.add_argument("--n", type=int, default=3)
    r = sub.add_parser("report")
    r.add_argument("--batch", required=True)
    r.add_argument("--minutes", default="12")
    a = ap.parse_args()
    {"plan": cmd_plan, "report": cmd_report}[a.cmd](a)


if __name__ == "__main__":
    main()
