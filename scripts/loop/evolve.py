#!/usr/bin/env python3
"""Level 2 of the learning loop: Claude writes one small code change, the race judges it.

    python scripts/loop/evolve.py --state DIR --batch DIR [--force]

Runs only when the gene search has stalled (no new champion for `plateau_gens` generations), no
code change is already racing, and today's budget of Claude calls isn't spent. Then:

1. Evidence from the generation just played: the most common failures (action + failure code),
   deaths and what the bot was doing, and short traces of the champion's two worst runs.
2. Memory of earlier attempts (ExpeL-style): every past code change, how it did in the race, and
   the one-line lessons Claude wrote with them.
3. One `claude -p` call (tools off, JSON schema) with that evidence and the source files the
   failures point to, asking for a few exact search/replace edits. Only game code may change: not
   the genes (Tune.java), the learned brain's features, the logs, the test harness or the scorer
   (a self-improving system that can edit its own judge learns to fool it: Darwin Goedel Machine).
4. The edits must apply exactly and compile (one retry with the compiler's errors). Then they go
   on a branch evolve/<id> and join the race as a genome with the champion's genes. The race
   decides like for any gene change; a code change that wins is merged into main (merge.py).
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bank  # noqa: E402
import common  # noqa: E402

SRC = "mod/src/main/java/io/github/plrlr/autopilot/"
# Files a patch may never touch: the genes, the model's inputs, logging (what the scorer reads), and
# the fair-play boundary of loop-written skills (the facade they may use, the registry, their genes,
# FairProbe): a patch that widened Player would hand every later generated skill a way to cheat.
FROZEN = ("Tune.java", "EvolvedGenes.java", "brains/Learned.java", "log/", "state/StateBuilder.java",
          "skills/evolved/", "skills/EvolvedActions.java", "skills/FairProbe.java")
# Nothing that talks to the game's command system or reads what a player couldn't see.
FORBIDDEN = ("runCommand", "performCommand", "sendCommand", "sendChat", "getServer()", "getSingleplayerServer",
             "ServerLevel", "commands.", "setBlock(", "Runtime.getRuntime", "ProcessBuilder")
SKILL_FILES = {
    "collect": "skills/CollectSkill.java", "craft": "skills/CraftSkill.java", "smelt": "skills/SmeltSkill.java",
    "attack": "skills/CombatSkills.java", "shoot": "skills/CombatSkills.java", "retreat": "skills/CombatSkills.java",
    "explore": "skills/MoveSkills.java", "goto": "skills/MoveSkills.java", "shelter": "skills/NightSkills.java",
    "sleep": "skills/NightSkills.java", "eat": "skills/InventorySkills.java", "equip": "skills/InventorySkills.java",
    "pickup": "skills/InventorySkills.java", "place": "skills/InventorySkills.java",
    "fill_bucket": "skills/BucketSkills.java", "make_obsidian": "skills/BucketSkills.java",
    "build_portal": "skills/CastPortal.java", "enter_portal": "skills/PortalSkills.java",
    "fortress": "skills/NetherSkills.java", "locate_stronghold": "skills/PortalSkills.java",
    "fill_end_portal": "skills/PortalSkills.java", "unstuck": "skills/Unstuck.java",
}
SETTINGS = {"plateau_gens": 6, "max_per_day": 3, "max_prompt_chars": 160000}

SCHEMA = {
    "type": "object", "additionalProperties": False,
    "required": ["summary", "lesson", "edits"],
    "properties": {
        "summary": {"type": "string", "description": "One line: what the change does and why"},
        "lesson": {"type": "string", "description": "One sentence learned from the evidence, for future attempts"},
        "edits": {"type": "array", "maxItems": 6, "items": {
            "type": "object", "additionalProperties": False, "required": ["file", "search", "replace"],
            "properties": {"file": {"type": "string"}, "search": {"type": "string"}, "replace": {"type": "string"}}}},
    },
}

SYSTEM = """You improve a Minecraft bot's Java code. The bot plays vanilla survival through normal player
controls (Fabric mod + Baritone). Its hand-tuned numbers are genes that a search already tunes; you
change behavior the genes can't reach: logic, conditions, ordering, skill mechanics.

Rules:
- Fair play: act only through player controls and information a player could see. No commands, no
  server access, no x-ray, no reading unseen blocks.
- Small and targeted: one idea, at most a few edits, aimed at the most costly failure in the evidence.
- Each edit's "search" must be copied exactly from the given file (whitespace included) and occur
  once; "replace" is the new text. Keep the code compiling (Java 25, Minecraft 26.3 unobfuscated
  names, same imports unless you add them in an edit).
- Don't repeat a past attempt that lost; learn from its lesson.
- Paths are relative to mod/src/main/java/io/github/plrlr/autopilot/ (e.g. "plan/Planner.java")."""


def git(*a, cwd=common.ROOT, check=True):
    return subprocess.run(["git", *a], cwd=cwd, check=check, capture_output=True, text=True, timeout=120).stdout.strip()


def evidence(batch, champ, frontier=None):
    sb = common.summarizer()
    fails, deaths, runs = {}, {}, []
    front_fails, front_runs = {}, 0
    for d in sorted(os.listdir(batch)) if os.path.isdir(batch) else []:
        if not d.startswith("trial-"):
            continue
        r = sb.read_run(os.path.join(batch, d))
        for key, detail in r["fails"]:
            fails.setdefault(key, [0, detail])[0] += 1
        start, _, _ = bank.read_stages(os.path.join(batch, d))
        if frontier and start == frontier:
            front_runs += 1
            for key, detail in r["fails"]:
                front_fails.setdefault(key, [0, detail])[0] += 1
        for cause, doing in r["deaths"]:
            k = "%s while %s" % (cause, doing)
            deaths[k] = deaths.get(k, 0) + 1
        # Combat drills have their own score (fights); the worst-run traces are about the route.
        if r["final"] and ("-%s-" % champ) in d and not r["fights"]:
            runs.append((common.score_run(r, 1200), d, r))
    runs.sort()
    top = sorted(fails.items(), key=lambda kv: -kv[1][0])[:12]
    lines = ["Most common failures this generation (action code: count, one example):"]
    lines += ["- %s: %d (%s)" % (k, v[0], v[1][:140]) for k, v in top]
    lines.append("Deaths (cause while doing):")
    lines += ["- %s: %d" % (k, v) for k, v in sorted(deaths.items(), key=lambda kv: -kv[1])[:10]]
    for score, name, r in runs[:2]:
        lines.append("Worst champion run %s (score %.2f): milestones %s, checkpoints %s" % (name, score, r["milestones"], r["checkpoints"]))
        lines += ["  " + t for t in trace(os.path.join(batch, name))[-45:]]
    if front_runs:
        # The frontier is where the game is lost now: these failures come first.
        ftop = sorted(front_fails.items(), key=lambda kv: -kv[1][0])[:10]
        lines = ["FRONTIER stage '%s' (%d runs started there; runs rarely get past it). Its failures:" % (frontier, front_runs)] + \
                ["- %s: %d (%s)" % (k, v[0], v[1][:140]) for k, v in ftop] + [""] + lines
        top = ftop + top
    skills = [k.split()[0] for k, _ in top[:4]]
    return "\n".join(lines), skills


def trace(d):
    out = []
    for root, _, files in os.walk(d):
        for f in sorted(files):
            if f.startswith("run-") and f.endswith(".jsonl"):
                for line in open(os.path.join(root, f), errors="replace"):
                    try:
                        o = json.loads(line)
                    except ValueError:
                        continue
                    if o.get("event") == "skill_end":
                        out.append("%s -> %s%s (%.0fs) %s" % (o.get("skill"), "ok" if o.get("ok") else "FAIL ",
                                                             "" if o.get("ok") else o.get("code", ""), o.get("seconds", 0),
                                                             (o.get("detail") or "")[:100]))
                    elif o.get("event") in ("death", "milestone", "checkpoint", "reflex"):
                        out.append("[%s] %s" % (o["event"], o.get("detail", "")))
    return out


def attempts_text(st):
    lines = []
    for g in sorted(st["genomes"].values(), key=lambda g: g["born"]):
        c = g.get("code")
        if not c and not g.get("code_merged"):
            continue
        d = [p[2] for p in g["pairs"]]
        result = {"champion": "WON: became champion, merged", "contender": "still racing",
                  "rejected": "lost"}.get(g["status"], g["status"])
        if d:
            result += " (%+.2f over %d worlds)" % (common.mean(d), len(d))
        lines.append("- gen %d %s: %s -> %s" % (g["born"], g["id"], (c or {}).get("summary", g["note"]), result))
    for a in st.get("code_failures", [])[-6:]:
        lines.append("- gen %d: %s -> did not compile/apply: %s" % (a["gen"], a["summary"], a["why"][:160]))
    lessons = st.get("insights", [])[-12:]
    out = "Earlier code changes and how they did:\n" + ("\n".join(lines[-15:]) or "(none yet)")
    out += "\nLessons from earlier attempts:\n" + ("\n".join("- " + l for l in lessons) or "(none yet)")
    return out


def champion_genes_text(st, genes):
    ch = st["genomes"][st["champion"]]
    full = common.full_genes(genes, ch["genes"])
    return "Champion genes that differ from defaults: " + (", ".join(
        "%s=%s" % (n, full[n]) for n in genes if full[n] != genes[n]["def"]) or "none")


def ask_claude(prompt, claude, system=SYSTEM, schema=SCHEMA,
               instruction="Propose the change as JSON. The evidence and files are on stdin."):
    cmd = [claude, "-p", instruction, "--model", "opus",
           "--tools", "", "--no-session-persistence", "--output-format", "json",
           "--system-prompt", system, "--json-schema", json.dumps(schema)]
    r = subprocess.run(cmd, input=prompt, capture_output=True, text=True, timeout=600,
                       cwd=os.environ.get("RUNNER_TEMP", "/tmp"))
    if r.returncode != 0:
        raise RuntimeError("claude failed: " + (r.stderr or r.stdout)[-500:])
    out = json.loads(r.stdout)
    so = out.get("structured_output")
    if not isinstance(so, dict):
        raise RuntimeError("no structured_output: " + r.stdout[-300:])
    return so


def apply_edits(edits, dry=False):
    changed = {}
    for e in edits:
        rel = e["file"].replace("\\", "/").lstrip("/")
        rel = rel.split(SRC, 1)[-1]
        if ".." in rel or not rel.endswith(".java") or any(rel == f or rel.startswith(f) for f in FROZEN):
            raise ValueError("not allowed to edit " + rel)
        path = os.path.join(common.ROOT, SRC, rel)
        if not os.path.exists(path):
            raise ValueError("no such file " + rel)
        text = changed.get(path) or open(path, encoding="utf-8").read()
        n = text.count(e["search"])
        if n != 1:
            raise ValueError("search text found %d times in %s" % (n, rel))
        for bad in FORBIDDEN:
            if bad in e["replace"] and bad not in e["search"]:
                raise ValueError("forbidden in a patch: " + bad)
        changed[path] = text.replace(e["search"], e["replace"])
    if not dry:
        for path, text in changed.items():
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
    return [os.path.relpath(p, common.ROOT).replace("\\", "/") for p in changed]


def compile_check():
    r = subprocess.run(["./gradlew", "compileJava", "--console=plain", "-q"], cwd=os.path.join(common.ROOT, "mod"),
                       capture_output=True, text=True, timeout=900)
    return r.returncode == 0, (r.stdout + r.stderr)[-3000:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    ap.add_argument("--batch", required=True)
    ap.add_argument("--claude", default="claude")
    ap.add_argument("--force", action="store_true", help="skip the plateau check")
    ap.add_argument("--minutes", type=float, default=35,
                    help="this step's share of the update job's 60 minutes: a retry starts only if it still fits")
    ap.add_argument("--mode", choices=("auto", "edit", "skill"), default="auto",
                    help="edit: change existing code; skill: write a new skill (evolve_skill.py); auto: the rule below")
    ap.add_argument("--branch-prefix", default="evolve/", help="evolve-test/ for a test that must not race")
    a = ap.parse_args()
    t0 = time.time()
    genes = common.load_genes()
    st_path = os.path.join(a.state, "state.json")
    st = common.read_json(st_path)
    if not st:
        print("no loop state")
        return
    s = dict(SETTINGS, **st.get("settings", {}).get("evolve", {}))
    today = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    used = st.setdefault("evolve_days", {}).get(today, 0)
    champ = st["genomes"][st["champion"]]
    last_crown = max([g.get("crowned", 0) for g in st["genomes"].values()] + [0])
    racing = [g for g in st["genomes"].values() if g.get("code") and g["status"] in ("contender", "drilling")]
    if not a.force:
        if used >= s["max_per_day"]:
            print("evolve: today's %d Claude calls are used" % s["max_per_day"]); return
        if st["gen"] - last_crown < s["plateau_gens"]:
            print("evolve: the gene search is still finding things (last champion at gen %d)" % last_crown); return
        if racing:
            print("evolve: %s is already %s" % (racing[0]["id"], racing[0]["status"])); return
    # Level 3 v2 (evolve_skill.py): a failure that stays among the costliest for generations gets a
    # new skill instead of an edit, taking turns with edits (settings "evolve": {"skills": true}).
    import evolve_skill
    mode = a.mode
    if mode == "auto":
        last = (st.get("evolve_log") or [{}])[-1].get("mode")
        target = evolve_skill.pick_target(st, a.state) if s.get("skills") and last != "skill" else None
        mode = "skill" if target else "edit"
    elif mode == "skill":
        target = evolve_skill.pick_target(st, a.state, strict=not a.force)
    if mode == "skill":
        if not target:
            print("evolve: no lasting failure for a new skill to target")
            return
        print("evolve: writing a new skill for %s (%.0f game minutes lost over %d generations)" % (
            target["name"], target["minutes"], target["gens"]))
        return evolve_skill.run(a, st, st_path, s, target, genes, today, used, t0, a.branch_prefix)
    ev, skills = evidence(a.batch, champ["id"], bank.frontier(st))
    files = ["plan/Planner.java"]
    for sk in skills:
        f = SKILL_FILES.get(sk)
        if f and f not in files:
            files.append(f)
        if len(files) >= 3:
            break
    if any("while retreat" in l or "while attack" in l for l in ev.splitlines()) and "Autopilot.java" not in files:
        files.append("Autopilot.java")
    parts = [ev, "", attempts_text(st), "", champion_genes_text(st, genes), ""]
    budget = s["max_prompt_chars"] - sum(len(p) for p in parts)
    for f in files:
        text = open(os.path.join(common.ROOT, SRC, f), encoding="utf-8").read()
        if len(text) > budget:
            continue
        budget -= len(text)
        parts.append("=== FILE %s ===\n%s" % (f, text))
    prompt = "\n".join(parts)
    base = git("rev-parse", "HEAD")
    result, why = None, ""
    for attempt in range(2):
        # Every call counts against evolve.max_per_day, the retry included: the budget is the
        # user's Claude plan. --force (a test by hand) still counts, but isn't stopped by it.
        if attempt and used >= s["max_per_day"] and not a.force:
            why += " (no retry: today's budget is spent)"
            break
        # A retry is a call (up to 10 min) and a compile (up to 15): it must end in our share of the job.
        if attempt and time.time() - t0 > (a.minutes - 25) * 60:
            why += " (no retry: out of time)"
            break
        used += 1
        st["evolve_days"][today] = used
        common.write_json(st_path, st)
        try:
            so = ask_claude(prompt if attempt == 0 else prompt + "\n\nYOUR LAST EDITS FAILED:\n" + why +
                            "\nFix them (search text copied exactly from the files above).", a.claude)
        except Exception as e:  # noqa: BLE001 - any failure here just skips this cycle
            why = str(e)
            break
        try:
            touched = apply_edits(so["edits"])
        except ValueError as e:
            why = str(e)
            continue
        ok, log = compile_check()
        if ok:
            result = (so, touched)
            break
        why = "compile errors:\n" + "\n".join(l for l in log.splitlines() if "error" in l.lower())[:2000]
        git("checkout", "--", "mod/src")
    if not result:
        st = common.read_json(st_path)
        st.setdefault("code_failures", []).append({"gen": st["gen"], "summary": "(no usable patch)", "why": why})
        st.setdefault("evolve_log", []).append({"gen": st["gen"], "mode": "edit", "result": "no usable patch"})
        common.write_json(st_path, st)
        print("evolve: no usable patch: " + why[:300])
        return
    so, touched = result
    st = common.read_json(st_path)
    gid = "g%d" % st["next_id"]
    st["next_id"] += 1
    branch = a.branch_prefix + gid
    st.setdefault("evolve_log", []).append({"gen": st["gen"], "mode": "edit", "gid": gid, "result": "racing"})
    git("checkout", "-q", "-b", branch)
    git("add", *touched)
    git("-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
        "commit", "-q", "-m", "Loop %s: %s\n\nWritten by Claude (scripts/loop/evolve.py) from generation %d's failures.\n\n"
        "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>" % (gid, so["summary"], st["gen"]))
    sha = git("rev-parse", "HEAD")
    git("push", "-q", "origin", "%s:refs/heads/%s" % (sha, branch))
    git("checkout", "-q", base)
    st["genomes"][gid] = {"id": gid, "parent": champ["id"], "genes": dict(champ["genes"]), "born": st["gen"],
                          "code": {"branch": branch, "sha": sha, "base": base, "summary": so["summary"], "files": touched},
                          "status": "contender", "mutated": ["code"], "note": "code: " + so["summary"],
                          "evals": [], "pairs": []}
    if so.get("lesson"):
        st.setdefault("insights", []).append(so["lesson"].strip())
    common.write_json(st_path, st)
    print("evolve: %s on %s races next generation: %s" % (gid, branch, so["summary"]))


if __name__ == "__main__":
    main()
