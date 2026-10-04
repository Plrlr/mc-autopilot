#!/usr/bin/env python3
"""How the bot behaves, from the compact game rows: the habits a person watching would call out.

    python scripts/loop/patterns.py --state <trial-results>/loop --gens 12 [--stage spawn]

The failure table (failures.py) ranks what fails; this ranks how the bot spends a life and where it
dithers, over first lives only (what the score counts):
  - time by action: the share of first-life game time each action takes;
  - thrash: A -> B -> A within 30 game seconds (two choices taking turns: smelting food and iron
    took turns on one furnace 4,700 times in gens 118-177, each pulling the other's load out);
  - refusals in a row: the same failure twice running (collect:log NOT_FOUND 632 times);
  - deaths: cause, the last thing chosen before it, and health, hunger, depth and night then.
Level 3 (evolve.py) gets this report with its failure evidence every generation, so the loop
diagnoses its own play without a person reading the logs.
"""
import argparse
import collections
import glob
import gzip
import json
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

FLIP_S = 30
BEFORE_S = 20


def stage_of(history, gen, name):
    """The task a compact log played (history's "tasks" by slot), "data" for data runs."""
    if name.startswith("data-"):
        return "data"
    try:
        i = int(name.rsplit("-", 1)[1])
    except (ValueError, IndexError):
        return "?"
    tasks = (history.get(gen) or {}).get("tasks") or []
    return tasks[i].rstrip("*") if i < len(tasks) else "?"


def load(state_dir, gens, stage=None):
    """[(rows, first death gs or None)] of the last `gens` generations' eval games (one stage, or all)."""
    hist = {}
    try:
        for line in open(os.path.join(state_dir, "history.jsonl"), encoding="utf-8"):
            h = json.loads(line)
            hist[h["gen"]] = h
    except (OSError, ValueError):
        pass
    out = []
    for g in sorted(glob.glob(os.path.join(state_dir, "data", "gen-*")))[-gens:]:
        gen = int(os.path.basename(g)[4:])
        for f in sorted(glob.glob(os.path.join(g, "eval-*.jsonl.gz"))):
            name = os.path.basename(f)[:-len(".jsonl.gz")]
            if stage and stage_of(hist, gen, name) != stage:
                continue
            try:
                rows = [json.loads(l) for l in gzip.open(f, "rt", encoding="utf-8")]
            except (OSError, ValueError, EOFError):
                continue
            first = min((r["gs"] for r in rows if r.get("k") == "death"), default=None)
            out.append((rows, first))
    return out


def analyse(games, features=None):
    feats = features or common.load_features()
    idx = {n: feats.index(n) for n in ("hp", "food", "underground", "night")}
    secs, flips, repeats = collections.Counter(), collections.Counter(), collections.Counter()
    causes, last_pick, ctx, before = collections.Counter(), collections.Counter(), [], collections.Counter()
    life, decisions = 0.0, 0
    for rows, first in games:
        cut = first if first is not None else max((r.get("gs", 0) for r in rows), default=0)
        life += cut
        d = [r for r in rows if r.get("k") == "d" and r["gs"] <= cut]
        calm = [r for r in d if not r.get("u")]
        decisions += len(calm)
        for a, b, c in zip(calm, calm[1:], calm[2:]):
            if a["a"] == c["a"] != b["a"] and c["gs"] - a["gs"] < FLIP_S:
                flips[tuple(sorted((a["a"], b["a"])))] += 1
        ends = [r for r in rows if r.get("k") == "s" and r["gs"] <= cut]
        for r in ends:
            secs[r["a"]] += r.get("sec", 0)
        for a, b in zip(ends, ends[1:]):
            if a["a"] == b["a"] and not a["ok"] and not b["ok"] and a.get("c") == b.get("c") != "INTERRUPTED":
                repeats["%s %s" % (a["a"], a.get("c"))] += 1
        if first is not None:
            death = next(r for r in rows if r.get("k") == "death" and r["gs"] == first)
            causes[death.get("detail", "?")] += 1
            # The last 20 s: reflexes, being stuck, failed skills with their reasons (rows since 2026-10-04).
            seen = set()
            for r in rows:
                if first - BEFORE_S <= r["gs"] <= first and (r.get("k") == "e" or (r.get("k") == "s" and not r.get("ok"))):
                    what = ("%s: %s" % (r["e"], r.get("t", ""))) if r.get("k") == "e" else "%s %s: %s" % (r["a"], r.get("c"), r.get("t", ""))
                    what = what[:70]
                    if what not in seen and not what.startswith("gene:"):
                        seen.add(what)
                        before[what] += 1
            if d:
                last_pick[d[-1]["a"]] += 1
                x = d[-1].get("x") or []
                if len(x) > max(idx.values()):
                    ctx.append({k: x[i] for k, i in idx.items()})
    return {"games": len(games), "died": sum(1 for _, f in games if f is not None), "life_s": life,
            "decisions": decisions, "secs": secs, "flips": flips, "repeats": repeats, "causes": causes,
            "last_pick": last_pick, "ctx": ctx, "before": before}


def report(a, top=10):
    """The report as plain lines (the prompt's evidence, and the CLI's output)."""
    if not a["games"]:
        return ["(no games)"]
    life = max(1.0, a["life_s"])
    out = ["%d games, %d died in their first life; %.1f game hours of first lives, %d calm decisions" % (
        a["games"], a["died"], life / 3600, a["decisions"])]
    out.append("Time by action (share of first-life time): " + ", ".join(
        "%s %.0f%%" % (k, 100 * v / life) for k, v in a["secs"].most_common(top)))
    nflip = sum(a["flips"].values())
    out.append("Thrash, A->B->A within %d s: %d of %d decisions (%.0f%%); top pairs: %s" % (
        FLIP_S, nflip, a["decisions"], 100 * nflip / max(1, a["decisions"]),
        ", ".join("%s<->%s %d" % (k[0], k[1], v) for k, v in a["flips"].most_common(top))))
    out.append("Same failure twice in a row: " + ", ".join("%s %d" % kv for kv in a["repeats"].most_common(top)))
    out.append("First deaths by cause: " + ", ".join("%s %d" % kv for kv in a["causes"].most_common(top)))
    out.append("Last choice before a first death: " + ", ".join("%s %d" % kv for kv in a["last_pick"].most_common(top)))
    if a["ctx"]:
        c = a["ctx"]
        out.append("Then: median health %.0f/20, median hunger %.0f/20 (health only comes back at 18+), underground %.0f%%, night %.0f%%" % (
            20 * statistics.median(x["hp"] for x in c), 20 * statistics.median(x["food"] for x in c),
            100 * sum(x["underground"] > 0 for x in c) / len(c), 100 * sum(x["night"] > 0 for x in c) / len(c)))
    if a.get("before"):
        out.append("In the %d s before a first death (games logged since 2026-10-04): " % BEFORE_S + "; ".join(
            "%s x%d" % kv for kv in a["before"].most_common(top)))
    return out


def text(state_dir, gens=12, stages=("spawn", "nether")):
    """The report for level 3's prompt: one block per stage."""
    lines = ["HOW THE BOT BEHAVES (first lives, last %d generations; scripts/loop/patterns.py):" % gens]
    for stage in stages:
        lines.append("[%s starts]" % stage)
        lines += ["- " + l for l in report(analyse(load(state_dir, gens, stage)))]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True, help="the loop/ folder of the trial-results branch")
    ap.add_argument("--gens", type=int, default=12)
    ap.add_argument("--stage", help="spawn, nether, ... (default: spawn and nether)")
    a = ap.parse_args()
    print(text(a.state, a.gens, (a.stage,) if a.stage else ("spawn", "nether")))


if __name__ == "__main__":
    main()
