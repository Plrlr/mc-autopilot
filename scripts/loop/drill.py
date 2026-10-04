#!/usr/bin/env python3
"""Drill gate for a pull request: each new idea is drilled where it acts, before it waits for a race.

    python scripts/loop/drill.py plan --base BASE_SETTINGS --head HEAD_SETTINGS --state STATE_JSON --n 3
    python scripts/loop/drill.py report --base BASE_SETTINGS --head HEAD_SETTINGS --batch DIR

plan: every suggestion the PR adds to settings.json (up to MAX_IDEAS) gets its own drill, picked by
what it targets:
    "_stages": ["lava"] or an ok:build_portal metric  -> saved lava starts, 12 game minutes
    "_stages": ["nether"]                              -> saved Nether starts (real runs that got
                                                          there), 12 game minutes
    "_metric": "deaths"                                -> the combat drill (~30 staged fights), 10 min
    anything else                                      -> fresh worlds, 20 min
Each start is played twice on the PR's code: the champion's genes ("off") and the champion plus the
idea ("on"). Prints the job matrix as JSON ([] when the PR adds no suggestion).

report: per idea, its metric on both sides (metrics.py), deaths, its exposure, and a verdict:
    FAIL          the idea's side never finished a game, or the metric is clearly worse (z <= -1.5,
                  with 5+ tries a side or 5+ deaths in all) in most of the worlds that had tries
    INCONCLUSIVE  the change never ran on its side (an evolved skill never started, a gene logged no
                  "on" event): its metric then measured the old behavior, so it says nothing (g164)
    PASS          otherwise (3 pairs are a gate against breakage and clear harm, not a crown: the loop decides)
A failing idea gets "_drill": "fail" in settings.json before merging; loop.py never races those.
2026-09-29: the old drill lumped all of a PR's ideas into one side and judged portal steps only, so a
combat or movement idea got no drill that could see it.
"""
import argparse
import glob
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import metrics  # noqa: E402

PORTAL = ("lava_seen", "obsidian_placed", "frame_complete", "portal_lit")
MAX_IDEAS = 3
MINUTES = {"lava": "12", "nether": "12", "combat": "10", "natural": "20"}


def new_ideas(base, head):
    """The suggestions head adds, in head's order (a dict per idea). An idea already marked
    "_drill": "fail" is left out: marking it re-ran its whole drill on PR #78 (6 games)."""
    old = {json.dumps(x, sort_keys=True) for x in (common.read_json(base, {}) or {}).get("suggest", [])}
    return [x for x in (common.read_json(head, {}) or {}).get("suggest", [])
            if json.dumps(x, sort_keys=True) not in old and x.get("_drill") != "fail"]


def kind(idea):
    """lava, nether, combat or natural: where the idea acts, so where its drill plays."""
    m = idea.get("_metric", "")
    if "lava" in idea.get("_stages", []) or "build_portal" in m or "cast_portal" in m:
        return "lava"
    if "nether" in idea.get("_stages", []):
        # A Nether change judged in fresh worlds or arenas would almost never act (g141's readiness
        # gene raced 24 worlds; 1 in 12 spawn runs reaches the Nether).
        return "nether"
    if m == "deaths":
        return "combat"
    return "natural"


def metric_of(idea):
    return idea.get("_metric") or ("ok:build_portal|cast_portal" if kind(idea) == "lava" else "deaths")


def lava_starts(st, n, stage="lava", fallback="kit"):
    bank = st.get("bank", {})
    real = [c for c in bank.get(stage, []) if not c.get("synthetic")] or \
           [c for c in bank.get(fallback, []) if not c.get("synthetic")]
    # Newest saves only: the drill's games wait behind the loop's, and meanwhile the loop prunes
    # the oldest saves from the release (bank_keep). A random old one was gone by the time PR #21's
    # drill ran ("no assets match the file pattern"), failing both sides in 20 s.
    real.sort(key=lambda c: c.get("gen", 0), reverse=True)
    pool = [c["asset"] for c in real[:max(2 * n, 6)]]
    return random.Random().sample(pool, min(n, len(pool))) if pool else []


def ideas_of(a):
    """The ideas to drill: one given as JSON (--idea, a skill the loop wrote), or those a PR adds."""
    if getattr(a, "idea", None):
        return [json.loads(a.idea)]
    return new_ideas(a.base, a.head)[:MAX_IDEAS]


def cmd_plan(a):
    ideas = ideas_of(a)
    st = common.read_json(a.state, {}) or {}
    champ = st.get("genomes", {}).get(st.get("champion", ""), {}).get("genes", {})
    runs = []
    for j, idea in enumerate(ideas):
        genes_on = {k: v for k, v in idea.items() if not k.startswith("_")}
        k = kind(idea)
        starts = lava_starts(st, a.n) if k == "lava" else lava_starts(st, a.n, "nether", "nether") \
            if k == "nether" else [None] * a.n
        for i, asset in enumerate(starts):
            for side, genes in (("off", champ), ("on", dict(champ, **genes_on))):
                runs.append({"name": "drill-%d-%s-%d" % (j, side, i), "seed": "drill-%d-%d" % (j, i),
                             "scenario": "combat" if k == "combat" else "natural", "minutes": MINUTES[k],
                             "start": asset or "",
                             "params": json.dumps({"id": "drill-" + side, "genes": genes}, separators=(",", ":"))})
    if getattr(a, "format", "matrix") == "extra":
        # trials.yml's "extra" runs (a skill the loop wrote is drilled there, on its own branch).
        runs = [{"seed": r["seed"], "scenario": r["scenario"], "minutes": r["minutes"], "start": r["start"],
                 "id": r["name"], "genes": json.loads(r["params"])["genes"], "lean": "true", "window": "427x240"}
                for r in runs]
    print(json.dumps(runs, separators=(",", ":")))


def enough(m, on, off):
    """At least 5 tries a side (success rates) or 5 deaths in all (death rate) before a FAIL."""
    if metrics.lower_is_better(m):
        return on[0] + off[0] >= 5
    return min(on[1], off[1]) >= 5


def verdict(z, crashed, has_evidence, exposed=True, worse_worlds=True):
    """FAIL on clear harm with enough evidence (in most worlds, not a few tries in one), or when the
    idea's side never finished a game; INCONCLUSIVE when the change never ran."""
    if crashed:
        return "FAIL"
    if not exposed:
        return "INCONCLUSIVE"
    return "FAIL" if z <= -1.5 and has_evidence and worse_worlds else "PASS"


def drill_dirs(batch, j):
    """(side, start, dir) of idea j's games: PR drills name them trial-drill-0-on-1, trials.yml
    batches (runs/<id>/) trial-natural-drill-0-1-drill-0-on-1."""
    out = []
    for d in sorted(glob.glob(os.path.join(batch, "*drill-%d-*" % j))):
        m = re.search(r"drill-%d-(on|off)-(\d+)$" % j, os.path.basename(d))
        if m and os.path.isdir(d):
            out.append((m.group(1), m.group(2), d))
    return out


def cmd_report(a):
    sb = common.summarizer()
    ideas = ideas_of(a)
    verdicts = []
    out = ["### Drill gate (3 pairs per idea: a gate against breakage and clear harm; the loop's race decides)", ""]
    for j, idea in enumerate(ideas):
        k, m = kind(idea), metric_of(idea)
        genes = ", ".join("%s %s" % (n, v) for n, v in idea.items() if not n.startswith("_"))
        spec = metrics.exposure_spec(idea)
        tally = {"on": [0, 0.0], "off": [0, 0.0]}
        played = {"on": 0, "off": 0}
        acted_games, ex_on, per_start = 0, metrics.exposure([], None), {}
        rows = []
        for side, i, d in drill_dirs(a.batch, j):
            r = sb.read_run(d)
            if not r["final"]:
                rows.append("| %s | %s | didn't play | | | |" % (i, side))
                continue
            played[side] += 1
            h, n = metrics.tally_run(d, m)
            tally[side][0] += h
            tally[side][1] += n
            per_start.setdefault(i, {})[side] = [h, n]
            ex = metrics.exposure_run(d, spec)
            if side == "on":
                acted_games += 1 if metrics.acted(spec, ex) else 0
                ex_on = {key: ex_on[key] + ex[key] for key in ex_on}
            steps = " ".join(c for c in PORTAL if c in r["checkpoints"]) if k == "lava" else ""
            rows.append("| %s | %s | %s | %d | %s | %s |" % (i, side, metrics.describe(m, (h, n)), len(r["deaths"]),
                                                          metrics.describe_exposure(spec, ex), steps))
        z = metrics.z(m, tally["on"], tally["off"])
        diffs = metrics.world_diffs(m, [w["on"] + w["off"] for w in per_start.values() if "on" in w and "off" in w])
        worse = sum(1 for x in diffs if x < 0) > len(diffs) / 2
        # An idea that logs no exposure (an old gene) can't be checked for it: judged as before.
        exposed = spec is None or acted_games > 0
        v = verdict(z, played["on"] == 0 and played["off"] > 0, enough(m, tally["on"], tally["off"]), exposed, worse)
        header = m.replace("|", r"\|")
        exposure_text = "exposure on its side: %s; acted in %d of %d games" % (
            metrics.describe_exposure(spec, ex_on), acted_games, played["on"])
        out += ["#### %s: %s (%s drill, metric `%s`)" % (v, genes, k, m), "",
                "on: %s, off: %s, z %+.1f (pooled tries, for scale); worse in %d of %d worlds with tries" % (
                    metrics.describe(m, tally["on"]), metrics.describe(m, tally["off"]), z,
                    sum(1 for x in diffs if x < 0), len(diffs)), "", exposure_text, "",
                "| start | side | %s | deaths | exposure | portal steps |" % header, "|---|---|---|---|---|---|"] + rows + [""]
        if v == "FAIL":
            out.append('Mark it `"_drill": "fail"` in settings.json before merging (the loop skips those).\n')
        if v == "INCONCLUSIVE":
            out.append("The change never ran on its side, so the metric measured the old behavior: no evidence either way.\n")
        verdicts.append({"idea": idea, "verdict": v, "metric": m, "z": round(z, 2), "on": tally["on"], "off": tally["off"],
                         "played": played, "acted_games": acted_games, "exposure": ex_on,
                         "text": "%s: on %s, off %s, z %+.1f; %s" % (
                             v, metrics.describe(m, tally["on"]), metrics.describe(m, tally["off"]), z, exposure_text)})
    if any(kind(idea) == "nether" for idea in ideas):
        import nether_report
        out += ["### Nether outcomes, first life (saved starts are real runs' saves; staged ones are labeled)", "",
                nether_report.table(a.batch), ""]
    if getattr(a, "json", None):
        common.write_json(a.json, verdicts)
    print("\n".join(out))
    return verdicts


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("plan")
    p.add_argument("--base")
    p.add_argument("--head")
    p.add_argument("--idea", help="drill this idea (JSON) instead of the ones settings.json adds")
    p.add_argument("--state", required=True)
    p.add_argument("--n", type=int, default=3)
    p.add_argument("--format", choices=("matrix", "extra"), default="matrix", help="extra: trials.yml's runs")
    r = sub.add_parser("report")
    r.add_argument("--base")
    r.add_argument("--head")
    r.add_argument("--idea")
    r.add_argument("--batch", required=True)
    r.add_argument("--json", help="also write the verdicts here")
    a = ap.parse_args()
    {"plan": cmd_plan, "report": cmd_report}[a.cmd](a)


if __name__ == "__main__":
    main()
