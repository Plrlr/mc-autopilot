#!/usr/bin/env python3
"""The learning loop: evolve the bot's genes on paired seeds, keep only what clearly helps.

    python scripts/loop/loop.py propose --state DIR --sha SHA --out runs.json
    python scripts/loop/loop.py update  --state DIR --batch DIR
    python scripts/loop/loop.py status  --state DIR

DIR is the loop's folder on the trial-results branch (loop/): state.json, history.jsonl, the
learned model, and compact training data. One "generation" plays the champion and a few
challengers on the same fresh random seeds (plus a few data runs for the learned brain), then
scores every run and decides:

- Racing (irace / F-Race): a challenger stays in the race across generations while its paired
  score difference against the champion looks promising, and replaces the champion only when
  it's clearly better (enough pairs, one-sided t above a bar). One promotion per generation.
- Archive (Darwin Goedel Machine, MAP-Elites): every genome ever tried is kept with its record;
  new challengers are mostly mutations of the champion, sometimes of another good genome, so a
  line that looked slightly worse can still lead somewhere.
- Adaptive mutation: 1-3 genes per child, step sizes that grow for genes whose changes won and
  shrink for those that lost, and genes picked more often when their changes have paid off.

Seeds are fresh every generation, so the champion is a policy for Minecraft, not for 4 worlds.
"""
import argparse
import datetime
import glob
import gzip
import json
import os
import random
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bank  # noqa: E402
import common  # noqa: E402
import metrics  # noqa: E402

DEFAULT_SETTINGS = {
    "seeds_per_gen": 4,        # every genome of a generation plays these seeds (paired)
    "max_genomes": 3,          # champion + contenders + new challengers per generation
    "suggest_slots": 1,        # queued ideas (settings.json "suggest") racing at once
    "stage_seeds": 2,          # stage starts per genome (checkpoint bank / scenarios), paired like seeds
    "combat_seeds": 0,         # combat drills per genome (CombatDrill.java: short staged fights), paired
    "combat_minutes": 10,      # game minutes per combat drill (a fight every ~30-60 s)
    "bank_keep": 40,           # checkpoints kept per stage
    "data_runs": 2,            # extra champion runs with exploration on: data for the learned brain
    "explore_data": 0.15,      # learned.explore in the data runs
    "minutes": 20,             # game minutes per run
    "scenario": "natural",
    "max_gens_per_day": 36,
    # Plain drawing, no extra mods: the cloud already plays at 0.98x real time that way, and a
    # 10 fps cap halved the game speed (benchmark 36323172968). Change here to experiment.
    "lean": False,
    "perf_mods": "",
    "window": "",              # e.g. "427x240": fewer pixels for the software renderer
    "accept_pairs": 8,         # a challenger needs this many paired seeds...
    "accept_t": 2.0,           # ...and a one-sided t this high (many looks per challenger: keep it strict)...
    "min_gain": 0.3,           # ...and a mean gain at least this big (a real difference, not a fluke)
    "drop_after_pairs": 4,     # below zero after this many pairs: out
    "drop_t": -1.0,            # ...but a suggestion or code change only if its paired t is this low too
    "max_pairs": 24,           # never promoted after this many pairs: out (not better enough to tell)
    "sigma0": 0.15,            # starting mutation step, as a fraction of each gene's range
    "stage_lookahead": True,   # one stage start per generation goes past the frontier (False: all at the frontier)
    "model_gate": 0.02,        # learned.* genes race only when the model's held-out advantage R2 beats this
    "focus_stage_seeds": 1,    # extra starts of a focused challenger's stage (suggestion "_stages") per generation
    "max_jobs": 20,
    "metric_min": 40,          # tries per side before a targeted metric (suggestion "_metric") can decide
    "metric_min_final": 10,    # rare skill tries per side at the pair cap; same z and game safety bars
    "metric_z": 2.5,           # ...and how many standard errors better the challenger must be (many looks)
    "metric_safety_t": -1.5,   # ...while its whole-game pairs aren't worse than this paired t (a safety check)            # genomes x tasks per generation (GitHub's free plan runs 20 jobs at once)
}


def today():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")


def load_state(d, genes):
    st = common.read_json(os.path.join(d, "state.json"))
    if st is None:
        st = {"version": 1, "gen": 0, "champion": "g0", "next_id": 1, "days": {}, "pending": None,
              "genomes": {"g0": new_genome("g0", None, {}, 0, [], "the hand-tuned defaults (pre-loop code)")},
              "sigma": {}, "credit": {}, "settings": {}}
        st["genomes"]["g0"]["status"] = "champion"
    for k, v in DEFAULT_SETTINGS.items():
        st["settings"].setdefault(k, v)
    # scripts/loop/settings.json (in the repo) wins: settings change by a commit, not by editing state.
    repo = common.read_json(os.path.join(os.path.dirname(os.path.abspath(__file__)), "settings.json"), {})
    st["settings"].update({k: v for k, v in repo.items() if not k.startswith("_")})
    st["gene_specs"] = genes  # defaults and limits, for the dashboard
    bank.migrate(st)
    # A new scorer measures something else: differences scored the old way can't be added to new
    # ones, so every race starts over (the champion keeps its crown until beaten the new way).
    if (st.get("score_version", 1) != common.SCORE_VERSION
            or st.get("race_evidence_version", 0) != 1):
        for g in st["genomes"].values():
            reset_race(g)
        st["score_version"] = common.SCORE_VERSION
        st["race_evidence_version"] = 1
    for n in genes:
        st["sigma"].setdefault(n, st["settings"]["sigma0"])
        st["credit"].setdefault(n, {"n": 0, "sum": 0.0})
    return st


def new_genome(gid, parent, changed, gen, mutated, note):
    return {"id": gid, "parent": parent, "genes": changed, "born": gen, "code": None, "status": "new",
            "mutated": mutated, "note": note, "evals": [], "pairs": []}


def reset_race(g):
    """Both kinds of evidence belong to one opponent and one scoring version."""
    g["pairs"] = []
    g.pop("metric_tally", None)
    g.pop("metric_z", None)


# ---------------------------------------------------------------------------------- mutation

def pick_genes(st, genes, rng, k):
    """Genes to mutate: more often those whose past changes paid off (softmax of mean credit),
    always with some chance for every gene."""
    # The learned brain's genes only once its model predicts unseen runs better than the baseline.
    # The neural brain reports a 5% lower bound over resampled games (train.gain_lower_bound): above
    # zero is enough, and the race then judges it in play. A fixed R2 bar (model_gate) could never
    # pass: R2 is capped by how random outcomes are, not by choice quality. Trees still use the bar.
    if st.get("model_kind") == "mlp" or st.get("model_adv_lo") is not None:
        # No bound yet (too few held-out worlds) is a closed gate, not a fall back to the R2 bar.
        model_ok = (st.get("model_adv_lo") or 0) > 0
    else:
        model_ok = (st.get("model_adv_r2") or -1) > st["settings"].get("model_gate", 0.02)
    names = [n for n in genes if not n.startswith("learned.") or model_ok]
    w = []
    for n in names:
        c = st["credit"][n]
        m = c["sum"] / c["n"] if c["n"] else 0.0
        w.append(2.718 ** max(-2.0, min(2.0, m)))
    out = []
    while len(out) < min(k, len(names)):
        n = rng.choices(names, weights=w)[0]
        if n not in out:
            out.append(n)
    return out


def mutate(st, genes, parent, rng):
    base = dict(st["genomes"][parent]["genes"])
    k = rng.choices([1, 2, 3], weights=[0.5, 0.35, 0.15])[0]
    chosen = pick_genes(st, genes, rng, k)
    for n in chosen:
        g = genes[n]
        cur = common.full_genes(genes, base)[n]
        if g["kind"] == "BOOL":
            v = 1.0 - cur
        else:
            span = g["max"] - g["min"]
            v = cur
            for _ in range(10):  # make sure a whole-number gene actually moves
                v = common.clamp(g, cur + rng.gauss(0, st["sigma"][n] * span))
                if v != cur:
                    break
        if v == g["def"]:
            base.pop(n, None)
        else:
            base[n] = v
    return base, chosen


def same_genes(a, b):
    return json.dumps(a, sort_keys=True) == json.dumps(b, sort_keys=True)


# ---------------------------------------------------------------------------------- propose

def cmd_propose(a):
    genes = common.load_genes()
    st = load_state(a.state, genes)
    if a.override:
        st["settings"].update(json.loads(a.override))
    s = st["settings"]
    out = {"runs": [], "gen": st["gen"], "reason": ""}
    if os.path.exists(os.path.join(a.state, "STOP")):
        out["reason"] = "loop/STOP exists"
    elif st["days"].get(today(), 0) >= s["max_gens_per_day"]:
        out["reason"] = "daily cap of %d generations reached" % s["max_gens_per_day"]
    elif st.get("pending"):
        # A generation that never reported back (cancelled job): start over with a fresh one.
        st["pending"] = None
    if out["reason"]:
        common.write_json(a.out, out)
        print(out["reason"])
        return
    rng = random.Random()
    st["gen"] += 1
    gen = st["gen"]
    st["days"][today()] = st["days"].get(today(), 0) + 1
    champ = st["champion"]
    lineup = [champ]
    # Suggestions (settings.json "suggest": ideas from people or Claude sessions) race ahead of
    # everything else, each as the champion plus that change, raced once like any mutation. Up to
    # suggest_slots of them race at once. (It was one at a time: with 47 queued ideas at 2 or more
    # generations each, working through the queue would have taken days.)
    tried = st.setdefault("suggested", [])
    # A dethroned champion defends first: with few slots a promotion must be re-checked on fresh
    # seeds before anything new races (else a lucky crowning is never questioned).
    for g in st["genomes"].values():
        if g["status"] == "contender" and g["note"].endswith("(defending)") and len(lineup) < s["max_genomes"]:
            lineup.append(g["id"])
    # A code change (level 3) races next: each cost a Claude call, and evolve.py writes no other
    # while one races. Behind the queue, g29 waited 44 generations without playing a run, and level
    # 3 made no call in all that time (found 2026-09-30). One that still never played is retired.
    for g in sorted(st["genomes"].values(), key=lambda g: g["born"]):
        if not (g.get("code") and g["status"] == "contender" and g["note"].startswith("code:")):
            continue
        if not g["evals"] and gen - g["born"] > s.get("code_wait_gens", 6):
            g["status"] = "retired"
            g["retired_why"] = "never got a race slot in %d generations; its base is stale" % (gen - g["born"])
            print("%s retired: %s" % (g["id"], g["retired_why"]))
        elif g["id"] not in lineup and len(lineup) < s["max_genomes"]:
            lineup.append(g["id"])
    sug_cap = min(s["max_genomes"], len(lineup) + s.get("suggest_slots", 1))
    queue = learned_idea(st) + st.get("trim", []) + s.get("suggest", [])

    def enter(sug):
        """A queued idea joins the race as the champion plus its genes (once: `tried` remembers it)."""
        key = json.dumps(sug, sort_keys=True)
        if key in tried or len(lineup) >= sug_cap or sug.get("_drill") == "fail":
            return  # (an idea its PR drill failed never races: drill.py)
        changed = dict(st["genomes"][champ]["genes"])
        for n, v in sug.items():
            if n in genes:
                changed[n] = common.clamp(genes[n], float(v))
                if changed[n] == genes[n]["def"]:
                    changed.pop(n)
        tried.append(key)
        if same_genes(changed, st["genomes"][champ]["genes"]):
            return  # the champion already plays it (e.g. cave.torches after g24): nothing to race
        gid = "g%d" % st["next_id"]
        st["next_id"] += 1
        note = "suggested: " + ", ".join("%s %s" % (n, v) for n, v in sug.items() if not n.startswith("_"))
        st["genomes"][gid] = new_genome(gid, champ, changed, gen, [n for n in sug if n in genes], note)
        if sug.get("_stages"):
            # A focused idea (e.g. the portal skills: "_stages": ["lava"]) acts only at those stages,
            # so only those tasks' pairs judge it. Fresh-world pairs add only noise: the portal bundle
            # g35 was +3.4 on its lava starts (it lit one of two portals all night) and dropped at
            # +0.54 over 16 pairs, 12 of them from runs that never reached lava.
            st["genomes"][gid]["focus"] = list(sug["_stages"])
        if sug.get("_priority"):
            st["genomes"][gid]["priority"] = True  # races ahead of unfocused ideas, never sits out
        if sug.get("_max_pairs"):
            # A broad change (the brain itself) moves every game a little: give it more worlds.
            st["genomes"][gid]["max_pairs"] = int(sug["_max_pairs"])
        if sug.get("_metric"):
            # Judged by the thing it fixes (metrics.py): hundreds of tries a generation, not 4 scores.
            st["genomes"][gid]["metric"] = sug["_metric"]
        st["genomes"][gid]["status"] = "contender"
        st["genomes"][gid]["code"] = st["genomes"][champ].get("code")
        if sug.get("_auto", "").startswith("learned"):
            st["learned_race"] = {"gid": gid, "gen": gen}
        lineup.append(gid)

    def first(g):
        # A code change too: the job limit must not bench it (it holds up evolve.py while it waits).
        return bool(g.get("focus") or g.get("priority") or g["note"].startswith("code:"))

    def racing(focused):
        return sorted((g for g in st["genomes"].values() if g["status"] == "contender" and g["id"] not in lineup
                       and g["note"].startswith("suggested") and first(g) == focused),
                      key=lambda g: g["born"])

    # Focused ideas go first (the frontier's stage is where progress is stuck), racing ones before
    # new ones; then the other ideas already racing keep their slot until decided, oldest first;
    # then trim races (a crowned bundle minus one skill each) and the rest of the settings queue.
    for g in racing(True):
        if len(lineup) < sug_cap:
            lineup.append(g["id"])
    for sug in queue:
        if sug.get("_stages") or sug.get("_priority"):
            enter(sug)
    for g in racing(False):
        if len(lineup) < sug_cap:
            lineup.append(g["id"])
    for sug in queue:
        enter(sug)
    # Contenders still in the race, most promising first.
    cont = [g for g in st["genomes"].values() if g["status"] == "contender" and g["id"] not in lineup]
    cont.sort(key=lambda g: -common.paired_t([p[2] for p in g["pairs"]]))
    for g in cont[: s["max_genomes"] - len(lineup)]:
        lineup.append(g["id"])
    # New challengers: mutations of the champion, sometimes of another strong genome.
    tries = 0
    while len(lineup) < s["max_genomes"] and tries < 50:
        tries += 1
        elites = [g for g in st["genomes"].values() if g["status"] in ("retired", "contender") and g["evals"]]
        parent = champ
        if elites and rng.random() < 0.25:
            elites.sort(key=lambda g: -common.mean([e[2] for e in g["evals"]]))
            parent = rng.choice(elites[:5])["id"]
        changed, chosen = mutate(st, genes, parent, rng)
        if any(same_genes(changed, st["genomes"][x]["genes"]) for x in lineup):
            continue
        gid = "g%d" % st["next_id"]
        st["next_id"] += 1
        desc = ", ".join("%s %s" % (n, fmt(common.full_genes(genes, changed)[n])) for n in chosen)
        st["genomes"][gid] = new_genome(gid, parent, changed, gen, chosen, desc)
        st["genomes"][gid]["status"] = "contender"
        # A child of a code change (not yet merged into main) plays that code too.
        st["genomes"][gid]["code"] = st["genomes"][parent].get("code")
        lineup.append(gid)
    # Tasks every genome plays (paired): fresh worlds from spawn, plus starts at the frontier stage
    # from the checkpoint bank (or its staged scenario until real checkpoints exist).
    tasks = [{"kind": "natural", "stage": "spawn", "synthetic": False} for _ in range(s["seeds_per_gen"])]
    tasks += bank.pick_tasks(st, rng, s.get("stage_seeds", 0), s.get("stage_lookahead", True))
    # A focused challenger in the race: more starts at its stages, so its pairs come faster.
    focus = sorted({f for gid in lineup for f in st["genomes"][gid].get("focus", [])})
    for stage in focus:
        for _ in range(s.get("focus_stage_seeds", 0)):
            t = bank.pick_stage(st, rng, stage)
            if t:
                tasks.append(t)
    # Combat drills: many short fights, scored on their own (common.score_fights). Deaths to mobs
    # are the wall (152 of 162 runs in gens 28-37 died), and a full run holds too few fights to
    # tell a better fighter from luck.
    tasks += [{"kind": "combat", "stage": "combat", "synthetic": False, "scenario": "combat",
               "minutes": s.get("combat_minutes", 10)} for _ in range(s.get("combat_seeds", 0))]
    for t in tasks:
        t["seed"] = bank.random_seed(rng, "L%d" % gen)
    # Stay within the job limit: the last genomes in the lineup (unfocused contenders or fresh
    # mutations) sit this generation out and keep their place.
    while len(lineup) > 2 and len(lineup) * len(tasks) + s["data_runs"] > s.get("max_jobs", 10 ** 6):
        out_of_turn = [x for x in lineup[1:] if not first(st["genomes"][x])] or lineup[1:]
        lineup.remove(out_of_turn[-1])
    data_tasks = [{"kind": "natural", "stage": "spawn", "synthetic": False, "seed": bank.random_seed(rng, "D%d" % gen)}
                  for _ in range(s["data_runs"])]
    for gid in list(lineup):
        if st["genomes"][gid].get("code") and not rebase_code(st, gid, a.sha, gen) and gid != champ:
            lineup.remove(gid)
    runs = []
    for gid in lineup:
        for i, t in enumerate(tasks):
            runs.append(run_entry(s, gid, t, "eval", st["genomes"][gid]["genes"], gen, i, ref_of(st, gid, a.sha)))
    # Data runs: the champion with exploration on, on their own seeds (never scored for the race).
    for i, t in enumerate(data_tasks):
        g = dict(st["genomes"][champ]["genes"])
        g["learned.explore"] = s["explore_data"]
        runs.append(run_entry(s, champ, t, "data", g, gen, i, ref_of(st, champ, a.sha)))
    st["pending"] = {"gen": gen, "sha": a.sha, "lineup": lineup, "tasks": tasks, "data_tasks": data_tasks,
                     "seeds": [t["seed"] for t in tasks], "runs": [r["name"] for r in runs],
                     "started": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")}
    common.write_json(os.path.join(a.state, "state.json"), st)
    out["runs"] = runs
    out["gen"] = gen
    common.write_json(a.out, out)
    print("generation %d: %s on %d tasks (%s), %d data runs" % (gen, " ".join(lineup), len(tasks),
          ", ".join(t["stage"] + ("*" if t["synthetic"] else "") for t in tasks), s["data_runs"]))


def learned_idea(st):
    """The learned brain's race, queued first once its gate opens (model_adv_lo > 0: on unseen games
    its choices explain progress the state alone doesn't): the champion with learned.weight 1. Waiting
    for a mutation to pick the gene never came: queued ideas fill every slot. Once per opening; after
    a lost race, again 10 generations later if the gate is still open. learned.explore stays with the
    data runs (explore_data): deliberate tries cost the score a race is judged on."""
    lo = st.get("model_adv_lo")
    champ = st["genomes"][st["champion"]]
    if lo is None or lo <= 0 or champ["genes"].get("learned.weight", 0) > 0:
        return []
    last = st.get("learned_race")
    if last:
        g = st["genomes"].get(last["gid"], {})
        if g.get("status") in ("contender", "champion") or st["gen"] - last["gen"] < 10:
            return []
    return [{"learned.weight": 1, "_priority": True,
             "_auto": "learned brain gate open at gen %d (5%% bound %+.4f)" % (st["gen"], lo)}]


def rebase_code(st, gid, main_sha, gen):
    """Replays a code genome's commit on this generation's main, so it and the champion differ only
    by the change: g29 would have played gen 41's main against a champion on gen 85's. Returns False
    when the change no longer applies (the genome retires). Process trouble keeps the old commit."""
    import subprocess
    import tempfile
    code = st["genomes"][gid]["code"]
    if code.get("base") == main_sha or os.environ.get("LOOP_NO_REBASE"):
        return True

    def git(*args, cwd=common.ROOT, check=True):
        return subprocess.run(["git", *args], cwd=cwd, check=check, capture_output=True, text=True, timeout=120)
    tmp = tempfile.mkdtemp(prefix="rebase-")
    new, applies = None, True
    try:
        git("fetch", "-q", "--depth", "2", "origin", code["sha"])
        git("worktree", "add", "-q", "--detach", tmp, main_sha)
        r = git("-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                "cherry-pick", code["sha"], cwd=tmp, check=False)
        if r.returncode != 0:
            git("cherry-pick", "--abort", cwd=tmp, check=False)
            applies = False
        else:
            new = git("rev-parse", "HEAD", cwd=tmp).stdout.strip()
            git("push", "-q", "--force", "origin", "%s:refs/heads/%s" % (new, code["branch"]))
    except (subprocess.SubprocessError, OSError) as e:
        print("rebase %s: process failed, it plays %s as before: %s" % (gid, code["sha"][:7], e))
        return True
    finally:
        subprocess.run(["git", "worktree", "remove", "--force", tmp], cwd=common.ROOT, capture_output=True, timeout=60)
        shutil.rmtree(tmp, ignore_errors=True)
    old = code["sha"]
    for g in st["genomes"].values():
        c = g.get("code")
        if not c or c["sha"] != old:
            continue
        if applies:
            c.setdefault("first_sha", old)
            c["sha"], c["base"] = new, main_sha
        elif g["id"] != st["champion"]:
            g["status"] = "retired"
            g["retired_why"] = "its change no longer applies to main (gen %d)" % gen
    print("rebase %s: %s" % (gid, ("%s -> %s on main %s" % (old[:7], new[:7], main_sha[:7])) if applies else "conflicts with main: retired"))
    return applies


def ref_of(st, gid, main_sha):
    """The commit a genome plays: its own code change if it has one, else this generation's main."""
    c = st["genomes"][gid].get("code")
    return c["sha"] if c else main_sha


def run_entry(s, gid, task, role, changed, gen, i, ref):
    return {"name": "%s-%s-%d" % (role, gid, i), "seed": task["seed"], "genome": gid, "role": role, "ref": ref,
            "params": json.dumps({"id": "%s@gen%d" % (gid, gen), "genes": changed}, separators=(",", ":")),
            "minutes": str(task.get("minutes", s["minutes"])), "scenario": task.get("scenario", s["scenario"]),
            "start": task.get("asset", ""),
            "lean": "true" if s["lean"] else "false", "perf_mods": s["perf_mods"], "window": s.get("window", "")}


def fmt(v):
    return ("%g" % v) if v != int(v) else str(int(v))


# ---------------------------------------------------------------------------------- update

def cmd_update(a):
    genes = common.load_genes()
    st = load_state(a.state, genes)
    p = st.get("pending")
    if not p:
        print("nothing pending")
        return
    s = st["settings"]
    sb = common.summarizer()
    length = int(s["minutes"]) * 60
    gen = p["gen"]
    results = {}
    speeds = []
    tasks = p.get("tasks") or [{"kind": "natural", "stage": "spawn", "synthetic": False} for _ in p["seeds"]]
    data_tasks = p.get("data_tasks") or []
    upload = not os.environ.get("LOOP_NO_UPLOAD")
    banked = []
    for name in p["runs"]:
        d = os.path.join(a.batch, "trial-" + name)
        if not os.path.isdir(d):
            results[name] = None
            continue
        r = sb.read_run(d)
        if not r["final"]:
            results[name] = None  # the game never got to play (or crashed): not the genome's fault
            continue
        role, gid, i = name.split("-")
        pool = tasks if role == "eval" else data_tasks
        task = pool[int(i)] if int(i) < len(pool) else {}
        skip = 0 if task.get("kind", "natural") == "natural" else 10
        combat = task.get("kind") == "combat"
        score = common.score_fights(r) if combat else common.score_run(r, length, skip)
        results[name] = {"score": score, "milestones": r["milestones"], "fights": r["fights"],
                         "checkpoints": r["checkpoints"], "deaths": len(r["deaths"]),
                         "death_causes": [c for c, _ in r["deaths"]], "stage": task.get("stage", "spawn"),
                         "natural": task.get("kind", "natural") == "natural"}
        sp = read_speed(d)
        if sp:
            speeds.append(sp)
        save_training_rows(a.state, gen, name, d)
        if combat:
            continue  # an arena isn't a stage of the route: nothing for the checkpoint bank
        start, reached, _ = bank.read_stages(d)
        bank.record_result(st, start, reached, gen)
        results[name]["reached_stages"] = reached
        banked += bank.ingest(st, d, name, gen, gid, bool(task.get("synthetic")), upload)
    bank.prune(st, s.get("bank_keep", 40), upload)
    champ = p["lineup"][0]
    seeds = p["seeds"]
    by = {}  # (genome, seed index) -> score
    for name, res in results.items():
        role, gid, i = name.split("-")
        if role == "eval" and res is not None:
            by[(gid, int(i))] = res["score"]
            st["genomes"][gid]["evals"].append([gen, seeds[int(i)], res["score"]])
    # Paired differences against the champion on the same seeds.
    for gid in p["lineup"][1:]:
        g = st["genomes"][gid]
        focus = g.get("focus")
        for i in range(len(tasks)):
            if focus and tasks[i].get("stage") not in focus:
                continue
            if (gid, i) in by and (champ, i) in by:
                g["pairs"].append([gen, seeds[i], round(by[(gid, i)] - by[(champ, i)], 3)])
                if g.get("metric"):
                    t = g.setdefault("metric_tally", {"on": [0, 0], "off": [0, 0]})
                    for side, who in (("on", gid), ("off", champ)):
                        h, n = metrics.tally_run(os.path.join(a.batch, "trial-eval-%s-%d" % (who, i)), g["metric"])
                        t[side][0] += h
                        t[side][1] = round(t[side][1] + n, 2)
    decisions = race(st, genes, p["lineup"][1:], champ, gen)
    showcase = ingest_inbox(a.state, st, sb, gen, upload)
    # History line for the dashboard.
    gen_scores = {gid: [by[(gid, i)] for i in range(len(tasks)) if (gid, i) in by] for gid in p["lineup"]}
    all_ok = [r for r in results.values() if r]
    # The headline numbers count runs from spawn only: stage runs start with gear and score only
    # what they add after the start (they're in stage_scores and the bank summary instead).
    natural_ok = [r for r in all_ok if r.get("natural", True)]
    natural_idx = [i for i, t in enumerate(tasks) if t.get("kind", "natural") == "natural"]
    reached = {}
    for r in natural_ok:
        for m, _ in r["milestones"]:
            reached["m%d" % m] = reached.get("m%d" % m, 0) + 1
        for c in r["checkpoints"]:
            reached[c] = reached.get(c, 0) + 1
    prev = last_history(a.state)
    line = {
        "gen": gen, "time": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "sha": p["sha"][:7], "champion": st["champion"], "old_champion": champ,
        "champion_score": round(common.mean([by[(champ, i)] for i in natural_idx if (champ, i) in by]), 3),
        "champion_score_all": round(common.mean(gen_scores.get(champ, [])), 3),
        "natural_runs": len(natural_ok),
        "scores": {g: [round(x, 2) for x in v] for g, v in gen_scores.items()},
        "genomes": {g: st["genomes"][g]["note"] for g in p["lineup"]},
        "decisions": decisions, "runs": len(p["runs"]), "runs_ok": len(all_ok),
        "reached": reached, "deaths": sum(r["deaths"] for r in all_ok if not r["fights"]),
        "speed": round(common.mean(speeds), 3) if speeds else None,
        "total_runs": (prev.get("total_runs", 0) if prev else 0) + len(all_ok),
        "game_hours": round((prev.get("game_hours", 0) if prev else 0) + len(all_ok) * length / 3600, 2),
        "model_rows": st.get("model_rows", 0),
        # The learned model that played this generation (trained at the end of the one before):
        # its gate (5% bound, > 0 opens it) and its choice check on held-out deviations.
        "model": {"kind": st.get("model_kind"), "adv_lo": st.get("model_adv_lo"), "adv_r2": st.get("model_adv_r2"),
                  "sign": st.get("model_sign"), "heads": st.get("model_keys")},
        "showcase": showcase,
        "tasks": [t["stage"] + ("*" if t.get("synthetic") else "") for t in tasks],
        "banked": banked, "frontier": bank.frontier(st), "bank": bank.summary(st),
        "stage_scores": stage_scores(results),
        "portal_drill": portal_drill(results),
        "combat_drill": combat_drill(results),
    }
    with open(os.path.join(a.state, "history.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps(line, separators=(",", ":")) + "\n")
    st["pending"] = None
    common.write_json(os.path.join(a.state, "state.json"), st)
    print("generation %d scored: champion %s (%s)" % (gen, st["champion"], "; ".join(decisions) or "no change"))


def race(st, genes, challengers, champ, gen):
    """Promote at most one challenger; drop the hopeless; adapt step sizes and gene credit."""
    s = st["settings"]
    out = []
    best, best_t = None, 0.0
    for gid in challengers:
        g = st["genomes"][gid]
        diffs = [x[2] for x in g["pairs"]]
        t = common.paired_t(diffs)
        m = common.mean(diffs)
        # Suggestions and code changes get as many worlds as a promotion needs before they can be
        # dropped: focus.commit was dropped after 5 worlds at -0.24, which is noise at this spread.
        min_pairs = s["accept_pairs"] if g["note"].startswith(("suggested", "code:")) else s["drop_after_pairs"]
        max_pairs = g.get("max_pairs", s["max_pairs"])
        if g.get("metric"):
            verdict = metric_verdict(s, g, t)
            # Ranked by its metric's z (2.5+ is strong evidence); a whole-game t 2+ ranks alongside.
            if verdict == "crown" and g["metric_z"] > best_t:
                best, best_t = gid, g["metric_z"]
            elif verdict == "drop":
                g["status"] = "rejected"
                out.append("%s dropped (%s: %s vs %s)" % (gid, g["metric"], metrics.describe(g["metric"], g["metric_tally"]["on"]),
                                                         metrics.describe(g["metric"], g["metric_tally"]["off"])))
                learn_from(st, g, m)
            continue
        if len(diffs) >= s["accept_pairs"] and m >= s["min_gain"] and t >= s["accept_t"] and t > best_t:
            best, best_t = gid, t
            continue
        # A queued idea is dropped early only on evidence it hurts (t <= drop_t), not on a mean just
        # under zero: g47 (reflex.lava_margin) went out at -0.13 over 8 worlds (t -0.1, sd ~3 per
        # world), a coin flip. A neutral idea still leaves at max_pairs. Mutations stay cheap to drop.
        hurts = m <= 0 and (t <= s.get("drop_t", -1.0) or not g["note"].startswith(("suggested", "code:")))
        if (len(diffs) >= min_pairs and hurts) or len(diffs) >= max_pairs:
            g["status"] = "rejected"
            out.append("%s dropped (%+.2f over %d seeds)" % (gid, m, len(diffs)))
            learn_from(st, g, m)
    if best:
        g = st["genomes"][best]
        m = common.mean([x[2] for x in g["pairs"]])
        # The old champion defends: it races the new one from scratch, so a lucky promotion gets
        # reversed by the same rule that made it.
        old = st["genomes"][champ]
        old["status"] = "contender"
        reset_race(old)
        old["note"] = old["note"].split(" (defending")[0] + " (defending)"
        g["status"] = "champion"
        g["crowned"] = gen
        if g.get("code"):
            st["merge"] = dict(g["code"], genome=best)  # the workflow merges it into main (loop.py merge)
        st["champion"] = best
        if g.get("metric"):
            out.append("%s is the new champion (%s: %s vs %s, z %.1f; game %+.2f over %d seeds): %s" % (
                best, g["metric"], metrics.describe(g["metric"], g["metric_tally"]["on"]),
                metrics.describe(g["metric"], g["metric_tally"]["off"]), best_t, m, len(g["pairs"]), g["note"]))
        else:
            out.append("%s is the new champion (%+.2f over %d seeds, t %.1f): %s" % (best, m, len(g["pairs"]), best_t, g["note"]))
        learn_from(st, g, m)
        # A bundle of skills won (a whole wave of docs/skills-40.md at once): trim it. One race per
        # skill with just that skill off; a skill dragging the bundle down loses its place that way.
        bundle = [n for n in g.get("mutated", []) if n.startswith("skill.")]
        if len(bundle) >= 3:
            st.setdefault("trim", []).extend({n: 0} for n in bundle)
            out.append("trim: %d leave-one-out races queued for %s" % (len(bundle), best))
        # The others were measured against the old champion: they start over against the new one,
        # the ones sitting this generation out too (else they keep pairs against a champion that's gone).
        for other in st["genomes"].values():
            if other["status"] == "contender" and other["id"] != champ:
                reset_race(other)
    return out


def metric_verdict(s, g, t):
    """crown / drop / wait for a challenger judged by its targeted metric (metrics.py)."""
    s = dict(s, max_pairs=g.get("max_pairs", s["max_pairs"]))
    if not g["pairs"]:
        return "wait"
    tl = g.get("metric_tally")
    if not tl:
        return "drop" if len(g["pairs"]) >= s["max_pairs"] else "wait"
    enough = min(tl["on"][1], tl["off"][1]) >= s.get("metric_min", 40)
    at_limit = len(g["pairs"]) >= s["max_pairs"]
    # A hunt may finish only once per world. At the final look, allow a rare success metric
    # to decide after enough paired worlds; minutes of death exposure keep the usual minimum.
    if (at_limit and g["metric"].startswith("ok:")
            and len(g["pairs"]) >= s["accept_pairs"]):
        enough = enough or min(tl["on"][1], tl["off"][1]) >= s.get("metric_min_final", 10)
    zv = metrics.z(g["metric"], tl["on"], tl["off"])
    g["metric_z"] = round(zv, 2)
    safe = len(g["pairs"]) < 2 or t >= s.get("metric_safety_t", -1.5)
    if enough and zv >= s.get("metric_z", 2.5) and safe:
        return "crown"
    if (enough and zv <= 0) or not safe or at_limit:
        return "drop"
    return "wait"


def learn_from(st, g, m):
    """Credit the mutated genes with the outcome; widen steps that won, narrow those that lost."""
    for n in g.get("mutated", []):
        if n not in st["credit"]:
            continue
        c = st["credit"][n]
        c["n"] += 1
        c["sum"] += max(-3.0, min(3.0, m))
        f = 1.4 if m > 0 else 0.85
        st["sigma"][n] = max(0.03, min(0.4, st["sigma"][n] * f))


def read_speed(d):
    for f in glob.glob(os.path.join(d, "autopilot-test.log")):
        for line in open(f, errors="replace"):
            if "SPEED " in line:
                try:
                    return float(line.split("SPEED ")[1].split()[0])
                except (IndexError, ValueError):
                    pass
    return None


def save_training_rows(state_dir, gen, name, d):
    """The learned brain's data from one run, compact: decisions (game second, state features,
    action kind, propensity, urgent) and outcomes (milestones, checkpoints, deaths with game
    seconds interpolated from wall time). Written as loop/data/gen-NNNNN/<run>.jsonl.gz."""
    rows, wall_to_gs = [], []
    events, skills = [], []
    for f in sorted(glob.glob(os.path.join(d, "**", "run-*.jsonl"), recursive=True)):
        for line in open(f, errors="replace"):
            try:
                o = json.loads(line)
            except ValueError:
                continue
            # Reflex rows (fights and escapes, logged since 2026-09-28) are emergencies: the
            # advantage trees skip them, the danger model learns from them most of all.
            if o.get("layer") in ("tactician", "reflex") and "x" in o and "gs" in o:
                wall_to_gs.append((o["t"], o["gs"]))
                opts = o.get("options", [])
                idx = o.get("idx", 0)
                label = opts[idx] if 0 <= idx < len(opts) else o.get("choice", "")
                # "o": the options it chose among (since 2026-09-30). Without them training saw only
                # the pick, never what else was on the table, so it couldn't compare the two.
                rows.append({"k": "d", "gs": o["gs"], "x": o["x"], "a": action_key(label),
                             "p": o.get("prop", 1), "i": idx, "u": label in o.get("urgent", []),
                             "o": [action_key(l) for l in opts[:6]]})
            elif o.get("event") in ("death", "milestone", "checkpoint"):
                events.append((o["t"], o["event"], o.get("detail", "")))
            elif o.get("event") == "skill_end" and "t" in o:
                # Skill results (brain v2's skill stats): which action, ok or its fail code, how long.
                skills.append((o["t"], action_key(o.get("skill", "")), bool(o.get("ok")), o.get("code"),
                               round(float(o.get("seconds", 0)), 1)))
    if not rows:
        return
    for t, ev, detail in events:
        rows.append({"k": ev, "gs": interp(wall_to_gs, t), "detail": detail})
    for t, key, ok, code, sec in skills:
        r = {"k": "s", "gs": interp(wall_to_gs, t), "a": key, "ok": ok, "sec": sec}
        if code:
            r["c"] = code
        rows.append(r)
    rows.sort(key=lambda r: r["gs"])
    out = os.path.join(state_dir, "data", "gen-%05d" % gen, name + ".jsonl.gz")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with gzip.open(out, "wt", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, separators=(",", ":")) + "\n")


def interp(pairs, t):
    if not pairs:
        return 0
    if t <= pairs[0][0]:
        return pairs[0][1]
    for (t0, g0), (t1, g1) in zip(pairs, pairs[1:]):
        if t0 <= t <= t1:
            return g0 + (g1 - g0) * (t - t0) / max(1, t1 - t0)
    return pairs[-1][1] + (t - pairs[-1][0]) / 1000.0


def action_key(label):
    """Same as Learned.key: 'collect log:3' -> 'collect:log', 'explore cow,pig' -> 'explore:cow'."""
    skill, _, arg = label.partition(" ")
    if not arg:
        return skill
    cut = len(arg)
    for c in ":, ":
        i = arg.find(c)
        if i >= 0:
            cut = min(cut, i)
    return skill + ":" + arg[:cut]


def last_history(d):
    try:
        with open(os.path.join(d, "history.jsonl"), encoding="utf-8") as f:
            lines = f.read().strip().splitlines()
        return json.loads(lines[-1]) if lines else None
    except OSError:
        return None


def portal_drill(results):
    """The wall (0 of 16 kit starts got past it by gen 28): of the runs started from the iron kit
    or beside lava, how many got each portal step. Watched directly, since the race's score mixes
    it with deaths."""
    out = {}
    for stage in ("kit", "lava", "diamond"):
        runs = [r for r in results.values() if r and r.get("stage") == stage]
        if not runs:
            continue
        d = {"tries": len(runs)}
        for c in ("at_depth", "diamond_pickaxe", "lava_seen", "obsidian_10", "obsidian_placed", "frame_complete", "portal_lit"):
            d[c] = sum(1 for r in runs if c in r["checkpoints"])
        d["nether"] = sum(1 for r in runs if "nether" in (r.get("reached_stages") or []))
        out[stage] = d
    return out


def combat_drill(results):
    """This generation's combat drills (all genomes): fights, monsters killed of those summoned,
    deaths, and mean health lost per fight."""
    fights = [f for r in results.values() if r for f in r.get("fights", [])]
    if not fights:
        return {}
    return {"fights": len(fights), "kills": sum(f["kills"] for f in fights), "of": sum(f["of"] for f in fights),
            "deaths": sum(1 for f in fights if f["died"]),
            "damage": round(common.mean([f["damage"] for f in fights]), 1)}


def stage_scores(results):
    """Mean score per starting stage this generation (all genomes)."""
    by = {}
    for r in results.values():
        if r:
            by.setdefault(r.get("stage", "spawn"), []).append(r["score"])
    return {k: round(common.mean(v), 2) for k, v in by.items()}


def ingest_inbox(state_dir, st, sb, gen, upload=True):
    """Runs played elsewhere (the laptop's showcase runs of the champion, scripts/laptop-loop.ps1)
    arrive in loop/inbox/<run>/ with the same files a cloud run has. Their decisions become
    training data; their scores are shown, but never used to promote (unpaired, other machine)."""
    inbox = os.path.join(state_dir, "inbox")
    out = []
    for d in sorted(glob.glob(os.path.join(inbox, "*"))):
        if not os.path.isdir(d):
            continue
        name = os.path.basename(d)
        info = common.read_json(os.path.join(d, "run.json"), {})
        r = sb.read_run(d)
        if r["final"]:
            length = int(info.get("minutes", 20)) * 60
            start, reached, _ = bank.read_stages(d)
            out.append({"name": name, "genome": info.get("genome", "?"), "machine": info.get("machine", "?"),
                        "minutes": info.get("minutes"), "score": common.score_run(r, length),
                        "milestones": r["milestones"], "checkpoints": r["checkpoints"], "deaths": len(r["deaths"]),
                        "furthest": reached[-1] if reached else start})
            save_training_rows(state_dir, gen, name, d)
            bank.record_result(st, start, reached, gen)
            bank.ingest(st, d, name, gen, info.get("genome", "?"), False, upload)

        shutil.rmtree(d, ignore_errors=True)
    st["showcase_runs"] = st.get("showcase_runs", 0) + len(out)
    return out


def cmd_merge(a):
    """A code change that won the race goes into main: fast-forward when main hasn't moved, else a
    merge commit. On a conflict the change stays on its branch (the champion keeps playing it)."""
    import subprocess
    st_path = os.path.join(a.state, "state.json")
    st = common.read_json(st_path)
    m = st.get("merge") if st else None
    if not m:
        print("merge: nothing to merge")
        return

    def git(*args, check=True):
        return subprocess.run(["git", *args], cwd=common.ROOT, check=check, capture_output=True, text=True, timeout=120)
    ok = False
    try:
        git("fetch", "-q", "origin", "main")
        main = git("rev-parse", "FETCH_HEAD").stdout.strip()
        if git("merge-base", "--is-ancestor", main, m["sha"], check=False).returncode == 0:
            ok = git("push", "-q", "origin", "%s:refs/heads/main" % m["sha"], check=False).returncode == 0
        else:
            git("checkout", "-q", "--detach", main)
            r = git("-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                    "merge", "--no-ff", "-m", "Merge %s: won the loop's race (%s)" % (m["branch"], m["summary"]), m["sha"], check=False)
            if r.returncode == 0:
                ok = git("push", "-q", "origin", "HEAD:refs/heads/main", check=False).returncode == 0
            else:
                git("merge", "--abort", check=False)
    except (subprocess.SubprocessError, OSError) as e:
        print("merge: process failed: %s" % e)
        # A timed-out merge may have left local state; abort it before the publish steps.
        try:
            git("merge", "--abort", check=False)
        except (subprocess.SubprocessError, OSError):
            pass
    g = st["genomes"].get(m["genome"], {})
    if ok:
        # It's in main now: every genome carrying this change plays main from here on.
        for other in st["genomes"].values():
            if other.get("code") and other["code"]["sha"] == m["sha"]:
                other["code_merged"] = other["code"]
                other["code"] = None
        print("merge: %s is in main" % m["branch"])
    else:
        g["merge_failed"] = True
        print("merge: couldn't merge %s into main; it keeps racing from its branch" % m["branch"])
    st["merge"] = None
    common.write_json(st_path, st)


def cmd_export(a):
    """The champion's genes as a params file (for the laptop, or the user's own game)."""
    genes = common.load_genes()
    st = load_state(a.state, genes)
    ch = st["genomes"][st["champion"]]
    common.write_json(a.out, {"id": "%s@gen%d" % (ch["id"], st["gen"]), "genes": ch["genes"]})
    print(ch["id"])


# ---------------------------------------------------------------------------------- status

def cmd_status(a):
    genes = common.load_genes()
    st = load_state(a.state, genes)
    ch = st["genomes"][st["champion"]]
    print("generation %d, champion %s (%s)" % (st["gen"], ch["id"], ch["note"]))
    full = common.full_genes(genes, ch["genes"])
    for n, g in genes.items():
        if full[n] != g["def"]:
            print("  %-28s %s (default %s)" % (n, fmt(full[n]), fmt(g["def"])))
    for g in st["genomes"].values():
        if g["status"] == "contender":
            d = [x[2] for x in g["pairs"]]
            print("  contender %s: %+.2f over %d seeds (t %.1f) - %s" % (g["id"], common.mean(d), len(d), common.paired_t(d), g["note"]))


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p1 = sub.add_parser("propose")
    p1.add_argument("--state", required=True)
    p1.add_argument("--sha", required=True)
    p1.add_argument("--out", required=True)
    p1.add_argument("--override", default="", help="settings to change, as JSON (smoke tests)")
    p2 = sub.add_parser("update")
    p2.add_argument("--state", required=True)
    p2.add_argument("--batch", required=True)
    p3 = sub.add_parser("status")
    p3.add_argument("--state", required=True)
    p4 = sub.add_parser("export")
    p4.add_argument("--state", required=True)
    p4.add_argument("--out", required=True)
    p5 = sub.add_parser("merge")
    p5.add_argument("--state", required=True)
    a = ap.parse_args()
    {"propose": cmd_propose, "update": cmd_update, "status": cmd_status, "export": cmd_export, "merge": cmd_merge}[a.cmd](a)


if __name__ == "__main__":
    main()
