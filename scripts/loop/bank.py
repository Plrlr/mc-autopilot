"""The checkpoint bank and the stage curriculum (Go-Explore: return to a state, explore from it).

Runs save the world when they first reach a stage (mod/src/gametest/.../Bank.java). The loop keeps
those zips as assets of the GitHub release "checkpoints" (index in state["bank"]) and spends part of
every generation on the frontier: the first stage on the way to the dragon that runs don't yet get
past reliably, started from a real saved world when there is one, else from the staged scenario
(labeled synthetic). So the late game gets practice long before the early game is perfect, and
each stage's own failures reach the race, the model and the code step.
"""
import glob
import json
import os
import random
import re
import shutil
import subprocess
import tempfile

STAGES = ["spawn", "kit", "nether", "rods", "eyes", "stronghold", "end"]
# Staged scenarios (test-world commands) standing in for a stage until real checkpoints exist.
SYNTHETIC = {"kit": "cast", "nether": "nether", "eyes": "stronghold", "end": "end"}
RELEASE = "checkpoints"


def read_stages(run_dir):
    """(start stage, [stages checkpointed in this run], {stage: game second}) from autopilot-test.log."""
    start, reached, when = "spawn", [], {}
    for f in glob.glob(os.path.join(run_dir, "autopilot-test.log")):
        for line in open(f, errors="replace"):
            m = re.search(r"STAGE start (\w+)", line)
            if m:
                start = m.group(1)
            m = re.search(r"CHECKPOINT (\w+) at (\d+)s", line)
            if m:
                reached.append(m.group(1))
                when[m.group(1)] = int(m.group(2))
    return start, reached, when


def record_result(st, start, reached, gen):
    """One more try at a stage: success if the run got past it (banked any later stage)."""
    stats = st.setdefault("stage_stats", {})
    ok = any(STAGES.index(r) > STAGES.index(start) for r in reached if r in STAGES)
    stats.setdefault(start, []).append([gen, 1 if ok else 0])
    stats[start] = stats[start][-40:]


def available(st, stage):
    return bool(st.get("bank", {}).get(stage)) or stage in SYNTHETIC


def rate(st, stage, window=16):
    res = [r[1] for r in st.get("stage_stats", {}).get(stage, [])[-window:]]
    return (sum(res) / len(res)) if res else None, len(res)


def frontier(st):
    """The first stage after spawn that runs don't get past at least half the time (or that has
    too few tries to tell), among the stages there's a way to start from."""
    for stage in STAGES[1:]:
        if not available(st, stage):
            continue
        r, n = rate(st, stage)
        if n < 4 or r < 0.5:
            return stage
    return STAGES[-1]


def pick_tasks(st, rng, count, lookahead=True):
    """`count` stage starts: the frontier, then (with lookahead) one later stage for data from
    further on. Without it every start is at the frontier: look-aheads past a wall nobody crosses
    only add deaths to the score (eyes and End starts: 0 of ~30 by gen 28)."""
    out = []
    f = frontier(st)
    later = [s for s in STAGES[STAGES.index(f) + 1:] if available(st, s)] if lookahead else []
    wanted = [f] + ([rng.choice(later)] if later else [f])
    while len(wanted) < count:
        wanted.append(f)
    for stage in wanted[:count]:
        real = [c for c in st.get("bank", {}).get(stage, []) if not c.get("synthetic")]
        pool = real or st.get("bank", {}).get(stage, [])
        if pool:
            c = rng.choice(pool)
            out.append({"kind": "start", "stage": stage, "asset": c["asset"], "synthetic": bool(c.get("synthetic"))})
        elif stage in SYNTHETIC:
            out.append({"kind": "scenario", "stage": stage, "scenario": SYNTHETIC[stage], "synthetic": True})
    return out


def gh(*args, check=False):
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=check)


def ensure_release():
    if gh("release", "view", RELEASE).returncode != 0:
        gh("release", "create", RELEASE, "--prerelease", "--title", "Checkpoint bank",
           "--notes", "Saved worlds from the learning loop's runs, one per stage reached (Go-Explore starts). "
                      "Written by .github/workflows/loop.yml; index in loop/state.json on trial-results.")


def ingest(st, run_dir, run_name, gen, genome, synthetic, upload=True):
    """Adds this run's checkpoints to the bank (uploads the zips). Returns the stages added."""
    added = []
    for meta_path in sorted(glob.glob(os.path.join(run_dir, "**", "checkpoints", "*.json"), recursive=True)):
        zip_path = meta_path[:-5] + ".zip"
        try:
            meta = json.load(open(meta_path, encoding="utf-8"))
        except ValueError:
            continue
        stage = meta.get("stage")
        if stage not in STAGES:
            continue
        # Marathon and laptop runs upload their zips themselves and name the asset in the json.
        preuploaded = meta.get("asset")
        if not preuploaded and not os.path.exists(zip_path):
            continue
        asset = preuploaded or "%s-gen%d-%s.zip" % (stage, gen, re.sub(r"[^\w-]", "", run_name))
        if upload and not preuploaded:
            tmp = os.path.join(tempfile.gettempdir(), asset)
            shutil.copyfile(zip_path, tmp)
            ensure_release()
            r = gh("release", "upload", RELEASE, tmp, "--clobber")
            os.remove(tmp)
            if r.returncode != 0:
                print("bank: upload of %s failed: %s" % (asset, r.stderr.strip()[:200]))
                continue
        st.setdefault("bank", {}).setdefault(stage, []).append({
            "asset": asset, "gen": gen, "run": run_name, "genome": genome, "synthetic": synthetic,
            "game_seconds": meta.get("game_seconds"), "inventory": meta.get("inventory", "")[:300],
            "dimension": meta.get("dimension"), "bytes": meta.get("bytes")})
        added.append(stage)
    return added


def prune(st, keep=40, upload=True):
    """At most `keep` checkpoints per stage: real ones before synthetic, newest first."""
    for stage, items in st.get("bank", {}).items():
        if len(items) <= keep:
            continue
        items.sort(key=lambda c: (0 if not c.get("synthetic") else 1, -c["gen"]))
        for c in items[keep:]:
            if upload:
                gh("release", "delete-asset", RELEASE, c["asset"], "-y")
        st["bank"][stage] = items[:keep]


def summary(st):
    out = {}
    for stage in STAGES[1:]:
        items = st.get("bank", {}).get(stage, [])
        r, n = rate(st, stage)
        out[stage] = {"real": sum(1 for c in items if not c.get("synthetic")),
                      "synthetic": sum(1 for c in items if c.get("synthetic")),
                      "tries": n, "past": None if r is None else round(r, 2)}
    return out


def random_seed(rng, prefix):
    return "%s-%s" % (prefix, "".join(rng.choice("abcdefghjkmnpqrstuvwxyz23456789") for _ in range(6)))
