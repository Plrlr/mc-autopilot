"""Targeted metrics: judge a change by the thing it is meant to fix, not by the whole-game score.

A whole game is too noisy a judge (paired-score sd 3-4.7 over gens 50-58: a +1 gain needs ~60
worlds). One generation holds hundreds of tries of each skill, so a skill's success rate or the
death rate moves the needle in a generation or two. A suggestion names its metric:

    "_metric": "ok:explore"                 success rate of explore (any arg)
    "_metric": "ok:build_portal|cast_portal" either skill counts
    "_metric": "deaths"                      deaths per game minute (lower is better)
    "_metric": "resolve:shore:TIMEOUT|STUCK+swim_out"
                                             share of shore TIMEOUT/STUCK failures followed, within
                                             3 minutes and before a death, by an ok shore or swim_out:
                                             whether the failure got fixed, by the old skill or the
                                             new one (an evolved skill's metric since 2026-10-04)

Interrupted tries don't count: an interruption says nothing about the skill.

Two more rules since 2026-10-04 (g164's swim_out "passed" its drill on ok:shore without ever running):
- Exposure. A change is judged only if it got its chance: an evolved skill must have been offered,
  chosen and started; a gene must have logged "<gene> on: ..." events (Exposure.java). With none, the
  verdict is INCONCLUSIVE, not a pass or a fail about the change.
- Worlds, not tries. Tries within one game are not independent (one swamp gives a dozen shore
  timeouts), so a race compares the two sides world by world (world_diffs) and tests the mean
  difference over worlds, never pooled tries.
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


RESOLVE_MS = 180000  # wall ms a failure has to be fixed in (resolve:)


def _resolve(rows, metric):
    """(resolved, opportunities) for "resolve:<action>:<CODE|CODE>[+<skill>]". One opportunity per
    episode: further failures while one is open belong to it."""
    body, _, extra = metric[len("resolve:"):].partition("+")
    action, _, codes = body.partition(":")
    codes = set(codes.split("|")) if codes else None
    fixers = [action] + ([extra] if extra else [])
    hits = n = 0
    open_t = None
    for o in rows:
        t = o.get("t")
        if open_t is not None and t is not None and t - open_t > RESOLVE_MS:
            open_t = None
        if o.get("event") == "death":
            open_t = None
            continue
        if o.get("event") != "skill_end" or o.get("code") == "INTERRUPTED":
            continue
        skill = str(o.get("skill", ""))
        if open_t is not None and o.get("ok") and _matches(skill, fixers):
            hits += 1
            open_t = None
        elif open_t is None and not o.get("ok") and _matches(skill, [action]) and (codes is None or o.get("code") in codes):
            n += 1
            open_t = t if t is not None else 0
    return hits, n


def tally(rows, metric):
    """(hits, trials) of `metric` over a run's log rows. For "deaths": (deaths, game minutes)."""
    if metric.startswith("resolve:"):
        return _resolve(rows, metric)
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


def _head(label):
    return str(label).split(" ")[0]


def exposure_spec(idea):
    """What shows an idea got its chance, from its genes (or "_exposure": {"skill"|"gene": name}):
    an evolved skill's own option, a gene's Exposure.java events. None when nothing can show it
    (an old gene that logs nothing): such ideas are judged as before."""
    if idea.get("_exposure"):
        return dict(idea["_exposure"])
    for name in idea:
        if name.startswith("evolved."):
            return {"skill": name[len("evolved."):]}
    return None


def exposure(rows, spec):
    """Counts for one game: offers, choices, starts, ends (not interrupted) and ok ends of the
    skill; "on" and "off" opportunity events of the genes."""
    out = {"offers": 0, "choices": 0, "starts": 0, "ends": 0, "ok": 0, "on": 0, "off": 0}
    if not spec:
        return out
    skill = spec.get("skill")
    genes = spec.get("gene") or []
    genes = [genes] if isinstance(genes, str) else genes
    for o in rows:
        if skill and "options" in o:
            if any(_head(x) == skill for x in o.get("options", [])):
                out["offers"] += 1
            if _head(o.get("choice", "")) == skill:
                out["choices"] += 1
        ev = o.get("event")
        if skill and ev == "skill_start" and _head(o.get("skill", "")) == skill:
            out["starts"] += 1
        elif skill and ev == "skill_end" and _head(o.get("skill", "")) == skill and o.get("code") != "INTERRUPTED":
            out["ends"] += 1
            out["ok"] += 1 if o.get("ok") else 0
        elif genes and ev == "gene":
            d = str(o.get("detail", ""))
            for g in genes:
                if d.startswith(g + " on:"):
                    out["on"] += 1
                elif d.startswith(g + " off:"):
                    out["off"] += 1
    return out


def exposure_run(run_dir, spec):
    return exposure(_rows(run_dir), spec)


def acted(spec, ex):
    """Did the changed behavior run in this game?"""
    if not spec:
        return False
    return ex["starts"] > 0 if spec.get("skill") else ex["on"] > 0


def describe_exposure(spec, ex):
    if not spec:
        return "exposure not logged"
    if spec.get("skill"):
        return "%s offered %d, chosen %d, started %d, ended %d (%d ok)" % (
            spec["skill"], ex["offers"], ex["choices"], ex["starts"], ex["ends"], ex["ok"])
    return "%s: acted %d, old way %d" % (",".join(spec["gene"] if isinstance(spec["gene"], list) else [spec["gene"]]),
                                         ex["on"], ex["off"])


def world_diffs(metric, worlds):
    """One number per world where both sides had tries: the challenger's rate minus the champion's
    (deaths: the champion's death rate minus the challenger's), so positive is better either way.
    `worlds`: [h_on, n_on, h_off, n_off] each."""
    out = []
    for h1, n1, h0, n0 in worlds:
        if n1 <= 0 or n0 <= 0:
            continue
        r1, r0 = h1 / n1, h0 / n0
        out.append(r0 - r1 if lower_is_better(metric) else r1 - r0)
    return out


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
    if metric.startswith("resolve:"):
        return "%d/%d fixed (%.0f%%)" % (h, n, 100.0 * h / n if n else 0)
    return "%d/%d ok (%.0f%%)" % (h, n, 100.0 * h / n if n else 0)
