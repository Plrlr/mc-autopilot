#!/usr/bin/env python3
"""The failure table: what cost the most game time and lives over the last generations.

    git fetch origin trial-results && git worktree add /tmp/tr origin/trial-results
    python scripts/loop/failures.py --state /tmp/tr/loop --gens 8

Reads loop/data/gen-*/ (the compact per-run rows loop.py saves) and ranks (action, fail code) by
game minutes lost, with tries and deaths. Interruptions are left out (they say nothing about the
skill). Work goes top-down: reproduce the top row in a drill, fix it behind a gene with a
"_metric" suggestion (metrics.py), race it.
"""
import argparse
import collections
import glob
import gzip
import json
import os


def action(a):
    """"explore:any" and "goto:surface" stay whole; "collect:coal" is just collect."""
    head, _, arg = a.partition(":")
    return a if head in ("explore", "goto", "shelter") and arg else head


def table(files):
    lost, tries, died, runs = collections.Counter(), collections.Counter(), collections.Counter(), 0
    for f in files:
        runs += 1
        for line in gzip.open(f, "rt", encoding="utf-8"):
            r = json.loads(line)
            if r.get("k") != "s" or r.get("ok") or r.get("c") == "INTERRUPTED":
                continue
            key = (action(r["a"]), r.get("c"))
            lost[key] += r.get("sec") or 0
            tries[key] += 1
            died[key] += 1 if r.get("c") == "DIED" else 0
    return runs, lost, tries, died


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True, help="the loop/ folder of the trial-results branch")
    ap.add_argument("--gens", type=int, default=8)
    ap.add_argument("--top", type=int, default=20)
    a = ap.parse_args()
    gens = sorted(glob.glob(os.path.join(a.state, "data", "gen-*")))[-a.gens:]
    files = [f for g in gens for f in glob.glob(os.path.join(g, "*.jsonl.gz"))]
    runs, lost, tries, died = table(files)
    print("%d runs, %s" % (runs, ", ".join(os.path.basename(g) for g in gens[:1] + gens[-1:])))
    print("%-22s %-14s %8s %7s %6s" % ("action", "code", "min lost", "tries", "died"))
    for key, sec in lost.most_common(a.top):
        print("%-22s %-14s %8.0f %7d %6d" % (key[0], key[1], sec / 60, tries[key], died[key]))


if __name__ == "__main__":
    main()
