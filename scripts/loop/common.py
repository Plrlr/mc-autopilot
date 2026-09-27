"""Shared pieces of the learning loop: the gene list, run scores, small statistics.

The genes come straight from mod/src/main/java/io/github/plrlr/autopilot/Tune.java, so the Java
code stays the one place where a gene, its default and its limits are defined.
"""
import importlib.machinery
import importlib.util
import json
import math
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TUNE = os.path.join(ROOT, "mod", "src", "main", "java", "io", "github", "plrlr", "autopilot", "Tune.java")
LEARNED = os.path.join(ROOT, "mod", "src", "main", "java", "io", "github", "plrlr", "autopilot", "brains", "Learned.java")


def load_genes(path=TUNE):
    """{name: {def, min, max, kind, help}} in declaration order."""
    src = open(path, encoding="utf-8").read()
    genes = {}
    for m in re.finditer(r'gene\("([\w.]+)",\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+),\s*Kind\.(\w+),\s*"([^"]*)"\)', src):
        name, d, lo, hi, kind, help_ = m.groups()
        genes[name] = {"def": float(d), "min": float(lo), "max": float(hi), "kind": kind, "help": help_}
    if not genes:
        raise SystemExit("no genes found in " + path)
    return genes


def load_features(path=LEARNED):
    """The learned brain's feature names, in order (Learned.FEATURES)."""
    src = open(path, encoding="utf-8").read()
    m = re.search(r"FEATURES = List\.of\((.*?)\);", src, re.S)
    return re.findall(r'"(\w+)"', m.group(1))


def clamp(g, v):
    v = max(g["min"], min(g["max"], v))
    return v if g["kind"] == "REAL" else float(round(v))


def full_genes(genes, changed):
    """Defaults with the genome's changes on top, clamped."""
    out = {n: g["def"] for n, g in genes.items()}
    for n, v in (changed or {}).items():
        if n in genes:
            out[n] = clamp(genes[n], float(v))
    return out


def summarizer():
    """scripts/summarize_batch as a module (it has no .py extension)."""
    path = os.path.join(ROOT, "scripts", "summarize_batch")
    loader = importlib.machinery.SourceFileLoader("summarize_batch", path)
    spec = importlib.util.spec_from_loader("summarize_batch", loader)
    mod = importlib.util.module_from_spec(spec)
    loader.exec_module(mod)
    return mod


# Points for each step toward the dragon. Early steps are worth little on their own; the portal
# path in between gives the search a slope to climb where milestones alone would be flat.
MILESTONE_POINTS = {1: 1, 2: 1, 3: 0.25, 4: 2, 5: 0.25, 6: 0.5, 7: 3, 8: 3, 9: 2, 10: 2, 11: 3, 12: 3, 13: 10}
CHECKPOINT_POINTS = {"two_buckets": 0.5, "flint_and_steel": 0.5, "lava_seen": 0.5, "obsidian_placed": 1,
                     "frame_complete": 1, "portal_lit": 1.5}
DEATH_PENALTY = 0.75


def score_run(run, length_s, skip_before=0):
    """One number per run: points for each milestone and checkpoint, up to 50% more the earlier it
    came, minus a penalty per death (they drop the gear the route needs). Higher is better.
    `run` is summarize_batch.read_run's dict. Runs started from a checkpoint or scenario pass
    skip_before: what they already had at the start (reported in the first seconds) scores nothing."""
    s = 0.0
    T = max(1, length_s)
    for m, t in run["milestones"]:
        if t > skip_before:
            s += MILESTONE_POINTS.get(m, 0) * (1 + 0.5 * max(0.0, 1 - t / T))
    for name, t in run["checkpoints"].items():
        if t > skip_before:
            s += CHECKPOINT_POINTS.get(name, 0) * (1 + 0.5 * max(0.0, 1 - t / T))
    s -= DEATH_PENALTY * min(len(run["deaths"]), 4)
    return round(s, 3)


def mean(xs):
    return sum(xs) / len(xs) if xs else 0.0


def stdev(xs):
    if len(xs) < 2:
        return 0.0
    m = mean(xs)
    return math.sqrt(sum((x - m) ** 2 for x in xs) / (len(xs) - 1))


def paired_t(diffs):
    """t statistic of the mean paired difference (0 when undefined)."""
    if len(diffs) < 2:
        return 0.0
    sd = stdev(diffs)
    if sd == 0:
        return 10.0 if mean(diffs) > 0 else (-10.0 if mean(diffs) < 0 else 0.0)
    return mean(diffs) / (sd / math.sqrt(len(diffs)))


def read_json(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def write_json(path, obj):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1, sort_keys=True)
    os.replace(tmp, path)
