"""Targeted metrics: judge a change by the thing it is meant to fix, not by the whole-game score.

A whole game is too noisy a judge (paired-score sd 3-4.7 over gens 50-58: a +1 gain needs ~60
worlds). One generation holds hundreds of tries of each skill, so a skill's success rate or the
death rate moves the needle in a generation or two. A suggestion names its metric:

    "_metric": "ok:explore"                 success rate of explore (any arg)
    "_metric": "ok:build_portal|cast_portal" either skill counts
    "_metric": "deaths"                      deaths per game minute (lower is better)

Interrupted tries don't count: an interruption says nothing about the skill.
"""
import glob
import json
import math
import os


def _rows(run_dir):
    for f in sorted(glob.glob(os.path.join(run_dir, "**", "run-*.jsonl"), recursive=True)):
        for line in open(f, errors="replace"):
            try:
                yield json.loads(line)
            except ValueError:
                continue


def _matches(skill, prefixes):
    return any(skill == p or skill.startswith(p + ":") or skill.startswith(p + " ") for p in prefixes)


def tally(rows, metric):
    """(hits, trials) of `metric` over a run's log rows. For "deaths": (deaths, game minutes)."""
    if metric == "deaths":
        deaths, last = 0, 0.0
        for o in rows:
            if "gs" in o:
                last = max(last, float(o["gs"]))
            if o.get("event") == "death":
                deaths += 1
        return deaths, last / 60.0
    if not metric.startswith("ok:"):
        raise ValueError("unknown metric " + metric)
    prefixes = metric[3:].split("|")
    hits = trials = 0
    for o in rows:
        if o.get("event") != "skill_end" or o.get("code") == "INTERRUPTED":
            continue
        if _matches(str(o.get("skill", "")), prefixes):
            trials += 1
            hits += 1 if o.get("ok") else 0
    return hits, trials


def tally_run(run_dir, metric):
    return tally(_rows(run_dir), metric)


def lower_is_better(metric):
    return metric == "deaths"


def z(metric, on, off):
    """How much better the challenger (`on`) is than the champion (`off`), in standard errors.
    Success rates: a two-proportion z. Death rates: the challenger's share of all deaths against its
    share of the game time (a conditional binomial test), positive when it dies less."""
    h1, n1 = on
    h0, n0 = off
    if n1 <= 0 or n0 <= 0:
        return 0.0
    if lower_is_better(metric):
        total = h1 + h0
        if total == 0:
            return 0.0
        p = n1 / (n1 + n0)
        return (total * p - h1) / math.sqrt(total * p * (1 - p))
    pooled = (h1 + h0) / (n1 + n0)
    se = math.sqrt(pooled * (1 - pooled) * (1 / n1 + 1 / n0))
    return 0.0 if se == 0 else (h1 / n1 - h0 / n0) / se


def describe(metric, t):
    h, n = t
    if lower_is_better(metric):
        return "%d deaths in %.0f min" % (h, n)
    return "%d/%d ok (%.0f%%)" % (h, n, 100.0 * h / n if n else 0)
