#!/usr/bin/env python3
"""What a Nether game achieved before its first death, from the logs a trial already writes.

    python scripts/loop/nether_report.py <batch dir> [--json OUT]

Per game (one folder per trial artifact, as trials.yml and the loop download them):
  rods_first_life    blaze rods held before the first death (the decision rows' rods feature, and
                     the test's 30-second inventory lines)
  fortress_known     a fortress was known before the first death
  fortress_fight_s   game seconds spent in the fortress fight ("fortress blazes") before it
  bricks_dug         most nether bricks ever carried: bricks in the bag mean Baritone dug through a
                     fortress, out of sight of the blazes ("In a fortress, never tunnel", lessons.md)
  first_death_s      game second of the first death (clock.py), with its cause
  safe_return        alive at the end, in the overworld, holding a rod: the stage's real goal
Pairs are matched by start (drill-<j>-on-<i> against drill-<j>-off-<i>). Staged scenarios
(nether_piglins, nether_hungry, nether_fortress) are labeled staged: never quote them as natural runs.
"""
import argparse
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

INV = re.compile(r"\[autopilot-test\]    at (-?\d+) (-?\d+) (-?\d+) hp (\d+) food (\d+) inv \{(.*)\}")
STATUS = re.compile(r"\[autopilot-test\] \S+ \d+s: .*deaths (\d+)")
STAGED = ("nether_piglins", "nether_hungry", "nether_fortress", "nether_entry", "nether", "blaze")


def _rows(d):
    out = []
    for f in sorted(glob.glob(os.path.join(d, "**", "run-*.jsonl"), recursive=True)):
        for line in open(f, errors="replace"):
            try:
                out.append(json.loads(line))
            except ValueError:
                continue
    return out


def _inv(text):
    return {k: int(v) for k, v in re.findall(r"(\w+)=(\d+)", text)}


def outcome(d, features=None):
    """One game's Nether outcome (see the module doc). None if the game never finished."""
    sb = common.summarizer()
    run = sb.read_run(d)
    if not run["final"]:
        return None
    feats = features or common.load_features()
    i_rods, i_fort = feats.index("rods"), feats.index("fortress_known")
    rows = _rows(d)
    rods = fort = 0
    fight = 0.0
    for o in rows:
        if o.get("event") == "death":
            break
        if o.get("event") == "milestone" and str(o.get("detail", "")).split(" ")[0] == "8":
            rods = max(rods, 1)  # a rod in hand, even with no decision logged after it
        x = o.get("x")
        if x and len(x) > max(i_rods, i_fort):
            rods = max(rods, round(x[i_rods] * 6))
            fort = fort or x[i_fort] > 0
        if o.get("event") == "skill_end" and str(o.get("skill", "")).startswith("fortress blazes"):
            fight += float(o.get("seconds", 0))
    bricks, last_inv, deaths_seen = 0, {}, 0
    log = os.path.join(d, "autopilot-test.log")
    lines = open(log, errors="replace").read().splitlines() if os.path.exists(log) else []
    for line in lines:
        m = STATUS.search(line)
        if m:
            deaths_seen = int(m.group(1))
        m = INV.search(line)
        if m:
            last_inv = _inv(m.group(6))
            bricks = max(bricks, last_inv.get("nether_bricks", 0))
            if deaths_seen == 0:
                rods = max(rods, last_inv.get("blaze_rod", 0))
    dim = re.search(r"dimension (\w+)", run["final"] or "")
    first = min(run["death_times"]) if run["death_times"] else None
    name = os.path.basename(d.rstrip("/\\"))
    # The scenario from the FINAL line ("FINAL nether_entry: ..."): PR drills name their games
    # drill-0-on-1, without it.
    scenario = run["final"].split()[1].rstrip(":") if run["final"] and len(run["final"].split()) > 1 else ""
    return {"name": name, "scenario": scenario, "staged": scenario in STAGED,
            "rods_first_life": rods, "fortress_known": bool(fort), "fortress_fight_s": round(fight),
            "bricks_dug": bricks, "first_death_s": first,
            "first_death": run["deaths"][0][0] if run["deaths"] else None, "deaths": len(run["deaths"]),
            "safe_return": not run["deaths"] and bool(dim) and dim.group(1) == "overworld" and last_inv.get("blaze_rod", 0) > 0,
            "length_s": run["length"]}


def pairs(batch):
    """{(idea, start): {"on": outcome, "off": outcome}} for drill folders, plus unpaired games."""
    out = {}
    for d in sorted(glob.glob(os.path.join(batch, "*"))):
        if not os.path.isdir(d):
            continue
        m = re.search(r"drill-(\d+)-(on|off)-(\d+)$", os.path.basename(d))
        key = (int(m.group(1)), int(m.group(3))) if m else (None, os.path.basename(d))
        o = outcome(d)
        if o:
            out.setdefault(key, {})[m.group(2) if m else "game"] = o
    return out


def table(batch):
    lines = ["| idea | start | side | staged | rods (1st life) | fortress known | fight s | bricks dug | first death | safe return |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for (j, i), sides in sorted(pairs(batch).items(), key=lambda kv: str(kv[0])):
        for side in ("off", "on", "game"):
            o = sides.get(side)
            if not o:
                continue
            death = "-" if o["first_death_s"] is None else "%s at %ds" % (o["first_death"], o["first_death_s"])
            lines.append("| %s | %s | %s | %s | %d | %s | %d | %d | %s | %s |" % (
                "-" if j is None else j, i, side, "staged" if o["staged"] else "saved/natural", o["rods_first_life"],
                "yes" if o["fortress_known"] else "no", o["fortress_fight_s"], o["bricks_dug"], death,
                "yes" if o["safe_return"] else "no"))
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("batch")
    ap.add_argument("--json")
    a = ap.parse_args()
    print(table(a.batch))
    if a.json:
        common.write_json(a.json, {"%s/%s" % k: v for k, v in pairs(a.batch).items()})


if __name__ == "__main__":
    main()
