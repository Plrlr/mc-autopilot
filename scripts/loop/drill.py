#!/usr/bin/env python3
"""Drill gate for a pull request: each new idea is drilled where it acts, before it waits for a race.

    python scripts/loop/drill.py plan --base BASE_SETTINGS --head HEAD_SETTINGS --state STATE_JSON --n 3
    python scripts/loop/drill.py report --base BASE_SETTINGS --head HEAD_SETTINGS --batch DIR

plan: every suggestion the PR adds to settings.json (up to MAX_IDEAS) gets its own drill, picked by
what it targets:
    "_stages": ["lava"] or an ok:build_portal metric  -> saved lava starts, 12 game minutes
    "_metric": "deaths"                                -> the combat drill (~30 staged fights), 10 min
    anything else                                      -> fresh worlds, 20 min
Each start is played twice on the PR's code: the champion's genes ("off") and the champion plus the
idea ("on"). Prints the job matrix as JSON ([] when the PR adds no suggestion).

report: per idea, its metric on both sides (metrics.py), deaths, and a verdict:
    FAIL  the metric is clearly worse (z <= -1.5, with 5+ tries a side or 5+ deaths in all), or the
          idea's side never finished a game
    PASS  otherwise (3 pairs are a gate against breakage and clear harm, not a crown: the loop decides)
A failing idea gets "_drill": "fail" in settings.json before merging; loop.py never races those.
2026-09-29: the old drill lumped all of a PR's ideas into one side and judged portal steps only, so a
combat or movement idea got no drill that could see it.
"""
import argparse
import glob
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import metrics  # noqa: E402

PORTAL = ("lava_seen", "obsidian_placed", "frame_complete", "portal_lit")
MAX_IDEAS = 3
MINUTES = {"lava": "12", "combat": "10", "natural": "20"}


def new_ideas(base, head):
    """The suggestions head adds, in head's order (a dict per idea)."""
    old = {json.dumps(x, sort_keys=True) for x in (common.read_json(base, {}) or {}).get("suggest", [])}
    return [x for x in (common.read_json(head, {}) or {}).get("suggest", []) if json.dumps(x, sort_keys=True) not in old]


def kind(idea):
    """lava, combat or natural: where the idea acts, so where its drill plays."""
    m = idea.get("_metric", "")
    if "lava" in idea.get("_stages", []) or "build_portal" in m or "cast_portal" in m:
        return "lava"
    if m == "deaths":
        return "combat"
    return "natural"


def metric_of(idea):
    return idea.get("_metric") or ("ok:build_portal|cast_portal" if kind(idea) == "lava" else "deaths")


def lava_starts(st, n):
    bank = st.get("bank", {})
    real = [c for c in bank.get("lava", []) if not c.get("synthetic")] or \
           [c for c in bank.get("kit", []) if not c.get("synthetic")]
    # Newest saves only: the drill's games wait behind the loop's, and meanwhile the loop prunes
    # the oldest saves from the release (bank_keep). A random old one was gone by the time PR #21's
    # drill ran ("no assets match the file pattern"), failing both sides in 20 s.
    real.sort(key=lambda c: c.get("gen", 0), reverse=True)
    pool = [c["asset"] for c in real[:max(2 * n, 6)]]
    return random.Random().sample(pool, min(n, len(pool))) if pool else []


def cmd_plan(a):
    ideas = new_ideas(a.base, a.head)[:MAX_IDEAS]
    st = common.read_json(a.state, {}) or {}
    champ = st.get("genomes", {}).get(st.get("champion", ""), {}).get("genes", {})
    runs = []
    for j, idea in enumerate(ideas):
        genes_on = {k: v for k, v in idea.items() if not k.startswith("_")}
        k = kind(idea)
        starts = lava_starts(st, a.n) if k == "lava" else [None] * a.n
        for i, asset in enumerate(starts):
            for side, genes in (("off", champ), ("on", dict(champ, **genes_on))):
                runs.append({"name": "drill-%d-%s-%d" % (j, side, i), "seed": "drill-%d-%d" % (j, i),
                             "scenario": "combat" if k == "combat" else "natural", "minutes": MINUTES[k],
                             "start": asset or "",
                             "params": json.dumps({"id": "drill-" + side, "genes": genes}, separators=(",", ":"))})
    print(json.dumps(runs, separators=(",", ":")))


def enough(m, on, off):
    """At least 5 tries a side (success rates) or 5 deaths in all (death rate) before a FAIL."""
    if metrics.lower_is_better(m):
        return on[0] + off[0] >= 5
    return min(on[1], off[1]) >= 5


def verdict(z, crashed, has_evidence):
    """FAIL on clear harm with enough evidence, or when the idea's side never finished a game."""
    return "FAIL" if (z <= -1.5 and has_evidence) or crashed else "PASS"


def cmd_report(a):
    sb = common.summarizer()
    ideas = new_ideas(a.base, a.head)[:MAX_IDEAS]
    out = ["### Drill gate (3 pairs per idea: a gate against breakage and clear harm; the loop's race decides)", ""]
    for j, idea in enumerate(ideas):
        k, m = kind(idea), metric_of(idea)
        genes = ", ".join("%s %s" % (n, v) for n, v in idea.items() if not n.startswith("_"))
        tally = {"on": [0, 0.0], "off": [0, 0.0]}
        played = {"on": 0, "off": 0}
        rows = []
        for d in sorted(glob.glob(os.path.join(a.batch, "trial-drill-%d-*" % j))):
            side, i = os.path.basename(d).split("-")[3:5]
            r = sb.read_run(d)
            if not r["final"]:
                rows.append("| %s | %s | didn't play | | |" % (i, side))
                continue
            played[side] += 1
            h, n = metrics.tally_run(d, m)
            tally[side][0] += h
            tally[side][1] += n
            steps = " ".join(c for c in PORTAL if c in r["checkpoints"]) if k == "lava" else ""
            rows.append("| %s | %s | %s | %d | %s |" % (i, side, metrics.describe(m, (h, n)), len(r["deaths"]), steps))
        z = metrics.z(m, tally["on"], tally["off"])
        v = verdict(z, played["on"] == 0 and played["off"] > 0, enough(m, tally["on"], tally["off"]))
        header = m.replace("|", r"\|")
        out += ["#### %s: %s (%s drill, metric `%s`)" % (v, genes, k, m), "",
                "on: %s, off: %s, z %+.1f" % (metrics.describe(m, tally["on"]), metrics.describe(m, tally["off"]), z), "",
                "| start | side | %s | deaths | portal steps |" % header, "|---|---|---|---|---|"] + rows + [""]
        if v == "FAIL":
            out.append('Mark it `"_drill": "fail"` in settings.json before merging (the loop skips those).\n')
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
    r.add_argument("--base", required=True)
    r.add_argument("--head", required=True)
    r.add_argument("--batch", required=True)
    a = ap.parse_args()
    {"plan": cmd_plan, "report": cmd_report}[a.cmd](a)


if __name__ == "__main__":
    main()
