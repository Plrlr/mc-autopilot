#!/usr/bin/env python3
"""Level 3 v2: the bot learns a new skill. Claude writes it, a drill gates it, the race keeps it.

    python scripts/loop/evolve.py --state DIR --batch DIR --mode skill [--force]      (writes one)
    python scripts/loop/evolve_skill.py verdict --state DIR [--gid gN]              (reads its drill)

When one failure stays among the costliest for generations (failures.py's table: game minutes
lost), gene races and small edits haven't fixed it. Then evolve.py (mode "skill") asks Claude for
a NEW skill aimed at that failure: new files in skills/evolved/ only, written against the fair-play
facade (skills/evolved/Player.java: the normal player controls; blocks and mobs only as the player
sees them). This file adds the skill's one registry line and its gene (evolved.<skill>, BOOL,
default 0, appended to evolved-genes.json), so the model never edits Tune.java, the registry's
framework or anything the scorer reads.

Gates, cheapest first: fairplay.py's static check (an allowlist of what the code may use), the
path check (only those files changed), the build with pr-check's commands (gradlew test
compileGametestJava) and the evolved-gene test. Then the skill's branch evolve/<gid> is pushed and
drilled by trials.yml with drill.py's plan (3 starts with and without the gene, where the metric
acts); its unit-tests job is pr-check's Java check again, on the pushed commit. Only a PASS makes
the genome a contender. It then races first (loop.py) as the champion plus its gene, judged by the
metric of the failure it targets, with the whole-game score as a safety check. A winner is merged
into main like any code change, and the learned brain gets a head for the new action once it has
rows: it learns when the skill is worth choosing.
"""
import argparse
import collections
import glob
import gzip
import json
import os
import re
import subprocess
import sys
import time
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import drill  # noqa: E402
import evolve  # noqa: E402
import failures  # noqa: E402
import fairplay  # noqa: E402

END_MARK = "// evolve:end"
SKIP_CODES = (None, "INTERRUPTED", "ERROR")
DRILL_WAIT_GENS = 4

SCHEMA = {
    "type": "object", "additionalProperties": False,
    "required": ["summary", "lesson", "skill", "class_name", "help", "files"],
    "properties": {
        "summary": {"type": "string", "description": "One line: what the new skill does and which failure it fixes"},
        "lesson": {"type": "string", "description": "One sentence learned from the evidence, for future attempts"},
        "skill": {"type": "string", "description": "snake_case name, 3-40 chars, new (not an existing skill)"},
        "class_name": {"type": "string", "description": "UpperCamel class name of the main file"},
        "help": {"type": "string", "description": "One line for the skill menu, under 120 chars"},
        "files": {"type": "array", "minItems": 1, "maxItems": 3, "items": {
            "type": "object", "additionalProperties": False, "required": ["name", "content"],
            "properties": {"name": {"type": "string", "description": "<ClassName>.java, in skills/evolved/"},
                           "content": {"type": "string", "description": "the whole Java file"}}}},
    },
}

SYSTEM = """You write ONE NEW skill for a Minecraft bot (Java 25, Minecraft 26.3 with unobfuscated Mojang
names, a Fabric mod). The bot plays vanilla survival through the normal player controls. A learning
loop races every change against the current bot on paired worlds and keeps only what wins.

You are shown one failure that has stayed among the costliest for generations, with the evidence.
Write a new skill that avoids or fixes it, and say when to offer it. You don't edit existing files:
the loop registers your skill behind its own on/off gene and races it.

Hard rules (a static checker rejects the code otherwise; nothing you write can bypass it):
- Package io.github.plrlr.autopilot.skills.evolved. The main file is <ClassName>.java with
  `public class <ClassName> extends EvolvedSkill`, `public static Option offer(Context c)` (return an
  Option whose skill() is your skill name when the skill should run now, else null; it runs on every
  decision, so keep it cheap and specific), and `public String name() { return "<skill>"; }`.
  Up to 2 helper files in the same package (no `extends` except EvolvedSkill).
- Use ONLY: Player (the facade: keys, looking, mining, placing, walking to a known position, the
  player's own state, blocks only if visible, a FairProbe, visible mobs), EvolvedSkill/Skill's
  protected members (memory, arg, ticks, timeoutTicks, start, tick, done, fail, cleanup), Context,
  Option, Fail, WorldMemory's read methods (what the player has seen), FairProbe, BlockPos,
  Direction, Vec3 and java.util collections/functions. Imports outside that list, fully qualified
  names, Mc, Minecraft, level, Baritone, Perception, Tune, threads, System, reflection, files,
  network and writing WorldMemory are rejected.
- Fair play: act only through the player controls, know only what the player could see. No x-ray,
  no reading unseen blocks, no searching loaded chunks, no commands, no world edits.
- Never block: start() and tick() do a little work each game tick and return; no loops that wait.
  End with done(...) or fail(Fail.X, ...) (a failure is fine: say why in the detail).
- Small and specific: one idea aimed at the target failure, under 200 lines if you can.
- Don't repeat an earlier skill attempt that lost; learn from its lesson."""

EXAMPLE = '''// An example of the shape (not a skill to copy): collect refused while swimming, so swim to seen land.
package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import net.minecraft.core.BlockPos;

import java.util.Set;

public class LeaveWater extends EvolvedSkill {
	private BlockPos land;

	public static Option offer(Context c) {
		if (!Player.inWater() || !"WRONG_PLACE".equals(c.lastCode())) return null;
		return new Option("leave_water", null, "in water: " + c.lastSkill() + " needs dry ground");
	}

	@Override
	public String name() { return "leave_water"; }

	@Override
	protected void start() {
		land = memory.nearestLand(Set.of());
		if (land == null) { fail(Fail.NOT_FOUND, "no dry ground seen"); return; }
		Player.walkNear(land, 1);
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) { done("on dry ground at " + Player.feet()); return; }
		Player.press(Player.Key.JUMP, Player.eyesInWater());
		if (!Player.walking()) Player.walkNear(land, 1);
	}
}'''


def path(rel):
    """A repo path, resolved when used (tests point common.ROOT at a scratch repo)."""
    return os.path.join(common.ROOT, rel)


# ---------------------------------------------------------------------------------- the target

def gen_tables(state_dir, gens):
    """Per generation (oldest first): Counter of game seconds lost per (action, code), tries, runs."""
    out = []
    for g in sorted(glob.glob(os.path.join(state_dir, "data", "gen-*")))[-gens:]:
        files = glob.glob(os.path.join(g, "*.jsonl.gz"))
        if files:
            runs, lost, tries, died = failures.table(files)
            out.append((os.path.basename(g), lost, tries, runs))
    return out


def existing_targets():
    """Failures a skill already targets on this checkout (evolved-genes.json "target")."""
    doc = common.read_json(path(fairplay.GENES_FILE), {}) or {}
    return {e.get("target") for e in doc.get("genes", []) if e.get("target")}


def pick_target(st, state_dir, strict=True, gens=8, top=5, share=0.5):
    """The costliest failure over the last `gens` generations that was among each generation's
    `top` costliest in at least `share` of them (one generation is 16 games: its own ranking is
    noisy; "top 3 in each of the last 4" never held on gens 77-84), and that no skill targets yet
    (nor lost at within the last 10 skill attempts). Not strict (--force): the costliest one."""
    tables = gen_tables(state_dir, gens)
    if not tables:
        return None
    total, tries, runs, hits = collections.Counter(), collections.Counter(), 0, collections.Counter()
    for _, lost, t, r in tables:
        total.update(lost)
        tries.update(t)
        runs += r
        hits.update(k for k, _ in lost.most_common(top))
    persistent = {k for k, n in hits.items() if len(tables) >= 4 and n >= share * len(tables)}
    blocked = existing_targets() | {a["target"] for a in st.get("skill_attempts", [])[-10:]
                                    if a.get("result") not in ("drilling", "racing", "won")}
    for key, sec in total.most_common():
        name = "%s %s" % key
        if key[1] in SKIP_CODES or name in blocked or (strict and key not in persistent):
            continue
        return {"action": key[0], "code": key[1], "name": name, "minutes": round(sec / 60, 1), "tries": tries[key],
                "runs": runs, "gens": len(tables), "persistent": key in persistent,
                "metric": metric_for(key[0], key[1])}
    return None


def metric_for(action, code):
    """What the race judges the new skill by: the failing action's success rate (metrics.py), or
    deaths per minute for a failure that kills."""
    return "deaths" if code == "DIED" else "ok:" + action.split(":")[0]


# ---------------------------------------------------------------------------------- evidence

def examples(batch, target, n=8, before=8):
    """Up to n examples of the target failure in this generation's games, each with the events
    that led to it (skill ends, reflexes, deaths)."""
    out = []
    head = target["action"].split(":")[0]
    arg = target["action"].split(":")[1] if ":" in target["action"] else None
    for f in sorted(glob.glob(os.path.join(batch, "*", "**", "run-*.jsonl"), recursive=True)):
        recent = collections.deque(maxlen=before)
        for line in open(f, errors="replace"):
            try:
                o = json.loads(line)
            except ValueError:
                continue
            ev = o.get("event")
            if ev == "skill_end":
                label = str(o.get("skill", ""))
                text = "%s -> %s (%.0fs) %s" % (label, "ok" if o.get("ok") else "FAIL " + str(o.get("code")),
                                                 o.get("seconds", 0), (o.get("detail") or "")[:110])
                parts = label.split(" ", 1)
                hit = (not o.get("ok") and o.get("code") == target["code"] and parts[0] == head
                       and (arg is None or (len(parts) > 1 and parts[1].startswith(arg))))
                if hit:
                    out.append("- in %s:\n    %s\n    >> %s" % (os.path.relpath(f, batch).split(os.sep)[0],
                                                               "\n    ".join(recent) or "(start)", text))
                    if len(out) >= n:
                        return out
                recent.append(text)
            elif ev in ("death", "milestone", "reflex", "checkpoint"):
                recent.append("[%s] %s" % (ev, (o.get("detail") or "")[:110]))
    return out


def skill_stats_text(st, action):
    head = action.split(":")[0]
    rows = []
    for k, v in sorted((st.get("skill_stats") or {}).items()):
        if k == head or k.startswith(head + ":"):
            codes = ", ".join("%s %d" % c for c in sorted(v.get("codes", {}).items(), key=lambda c: -c[1])[:5])
            rows.append("- %s: %d/%d ok, %s s per success, %d deaths; fails: %s" % (
                k, v.get("ok", 0), v.get("n", 0), v.get("sec"), v.get("died", 0), codes or "-"))
    return "Skill stats (all loop games) for %s:\n%s" % (head, "\n".join(rows[:12]) or "(none)")


def signatures(path, max_lines=60):
    """Public signatures of a source file: enough to use it, much shorter than the file."""
    try:
        src = open(path, encoding="utf-8").read()
    except OSError:
        return ""
    keep = [l.rstrip() for l in src.splitlines() if re.match(r"\s*(public|protected)\s", l)]
    return "\n".join(keep[:max_lines])


def framework_text():
    parts = []
    for name in fairplay.FRAMEWORK:
        p = path(fairplay.EVOLVED_DIR + name)
        if os.path.exists(p):
            parts.append("=== FILE skills/evolved/%s ===\n%s" % (name, open(p, encoding="utf-8").read()))
    for rel in ("state/WorldMemory.java", "skills/FairProbe.java", "skills/Fail.java", "plan/Option.java", "skills/Skill.java"):
        parts.append("=== PUBLIC API of %s ===\n%s" % (rel, signatures(os.path.join(common.ROOT, evolve.SRC, rel))))
    return "\n\n".join(parts)


def prompt_for(st, state_dir, batch, target, genes, budget):
    tables = gen_tables(state_dir, 8)
    total, tries = collections.Counter(), collections.Counter()
    for _, lost, t, _ in tables:
        total.update(lost)
        tries.update(t)
    lines = ["TARGET: %s: %.0f game minutes lost over %d tries in the last %d generations (%d games)%s." % (
                 target["name"], target["minutes"], target["tries"], target["gens"], target["runs"],
                 ", among each generation's 5 costliest in at least half of them" if target["persistent"] else ""),
             "The race will judge your skill by: %s (plus the whole-game score as a safety check)." % target["metric"],
             "", "Failure table, last %d generations (game minutes lost, tries):" % len(tables)]
    lines += ["- %s %s: %.0f min, %d tries" % (k[0], k[1], s / 60, tries[k]) for k, s in total.most_common(14)]
    lines += ["", skill_stats_text(st, target["action"]), "", "Examples of the target failure this generation:"]
    lines += examples(batch, target) or ["(no game logs of it in this batch)"]
    parts = ["\n".join(lines), "", evolve.attempts_text(st), skill_attempts_text(st), "",
             evolve.champion_genes_text(st, genes), "", "THE FRAMEWORK YOUR SKILL PLUGS INTO:", framework_text(), "",
             "=== EXAMPLE ===\n" + EXAMPLE]
    left = budget - sum(len(p) for p in parts)
    src = evolve.SKILL_FILES.get(target["action"].split(":")[0])
    if src:
        text = open(os.path.join(common.ROOT, evolve.SRC, src), encoding="utf-8").read()
        if len(text) < left:
            parts.append("=== THE FAILING SKILL'S CURRENT CODE (read only: you can't edit it) %s ===\n%s" % (src, text))
    return "\n".join(parts)


def skill_attempts_text(st):
    rows = ["- gen %d %s: new skill %s for %s -> %s" % (a["gen"], a["gid"], a["skill"], a["target"], a.get("result"))
            for a in st.get("skill_attempts", [])[-10:]]
    return "Earlier new-skill attempts:\n" + ("\n".join(rows) or "(none yet)")


# ---------------------------------------------------------------------------------- writing it

def menu_names():
    names = set(re.findall(r'MENU\.put\("(\w+)"', open(path(evolve.SRC + "skills/Skills.java"), encoding="utf-8").read()))
    if os.path.exists(path(fairplay.REGISTRY)):
        names |= set(re.findall(r'new Entry\("[\w.]+",\s*"(\w+)"', open(path(fairplay.REGISTRY), encoding="utf-8").read()))
    return names


def apply_skill(so, target, gen, genes):
    """Checks Claude's skill and writes it with its registry line and gene. Returns the paths
    touched; raises ValueError (with the reasons, for the retry) when a gate says no."""
    skill, cls = so["skill"].strip(), so["class_name"].strip()
    gene = fairplay.gene_name(skill)
    bad = []
    if not re.match(r"^[a-z][a-z0-9_]{2,39}$", skill) or skill in menu_names():
        bad.append("skill name must be new snake_case (3-40 chars): %r" % skill)
    if gene in genes:
        bad.append("gene %s exists" % gene)
    if not re.match(r"^[A-Z][A-Za-z0-9]{2,40}$", cls):
        bad.append("class_name must be UpperCamel: %r" % cls)
    files = {f["name"].replace("\\", "/").split("/")[-1]: f["content"] for f in so["files"]}
    if cls + ".java" not in files:
        bad.append("the main file must be %s.java" % cls)
    for name, content in files.items():
        if not re.match(r"^[A-Z][A-Za-z0-9]{2,40}\.java$", name) or name in fairplay.FRAMEWORK:
            bad.append("bad file name " + name)
        elif os.path.exists(path(fairplay.EVOLVED_DIR + name)):
            bad.append("%s exists already: new files only" % name)
        bad += ["%s: %s" % (name, v) for v in fairplay.check_source(content, name, skill if name == cls + ".java" else None)]
    if bad:
        raise ValueError("gates said no:\n- " + "\n- ".join(bad))
    touched = []
    for name, content in files.items():
        p = path(fairplay.EVOLVED_DIR + name)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(content if content.endswith("\n") else content + "\n")
        touched.append(p)
    reg = open(path(fairplay.REGISTRY), encoding="utf-8").read()
    if reg.count(END_MARK) != 1:
        raise ValueError("the registry lost its end marker")
    i = reg.index(END_MARK)
    i = reg.rindex("\n", 0, i) + 1
    reg = reg[:i] + fairplay.registry_line(skill, cls, so["help"]) + "\n" + reg[i:]
    with open(path(fairplay.REGISTRY), "w", encoding="utf-8", newline="\n") as f:
        f.write(reg)
    doc = common.read_json(path(fairplay.GENES_FILE), {"genes": []})
    old = list(doc.get("genes", []))
    doc["genes"] = old + [{"name": gene, "kind": "BOOL", "def": 0, "skill": skill, "why": so["help"][:200],
                           "gen": gen, "target": target["name"], "metric": target["metric"]}]
    bad = fairplay.check_gene_list(old, doc["genes"], known=genes)
    if bad:
        raise ValueError("; ".join(bad))
    with open(path(fairplay.GENES_FILE), "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, indent=1)
        f.write("\n")
    return [os.path.relpath(p, common.ROOT).replace("\\", "/") for p in touched] + [fairplay.REGISTRY, fairplay.GENES_FILE]


def build_check():
    """pr-check's commands: the Java unit tests with the game-test code compiled, then the loop's
    evolved-gene test. (ok, log tail)."""
    try:
        r = subprocess.run(["./gradlew", "test", "compileGametestJava", "--console=plain", "-q"],
                           cwd=os.path.join(common.ROOT, "mod"), capture_output=True, text=True, timeout=1200)
        if r.returncode:
            return False, (r.stdout + r.stderr)[-3000:]
        r = subprocess.run([sys.executable, os.path.join(common.ROOT, "scripts", "loop", "test_evolved_genes.py")],
                           capture_output=True, text=True, timeout=120)
        return r.returncode == 0, (r.stdout + r.stderr)[-2000:]
    except subprocess.TimeoutExpired as e:
        return False, "timed out: %s" % e


def undo():
    """Back to the base: the registry and gene list restored, new (untracked) files in
    skills/evolved/ removed. Nothing else is touched."""
    evolve.git("checkout", "--", fairplay.REGISTRY, fairplay.GENES_FILE, check=False)
    evolve.git("clean", "-fq", "--", fairplay.EVOLVED_DIR, check=False)


def changed_paths():
    """(status, path) of the mod's changes (the loop's checkout also holds batch/ and results/)."""
    out = []
    for line in evolve.git("status", "--porcelain", "--untracked-files=all", "--", "mod", "scripts", ".github").splitlines():
        status, path = line[:2].strip(), line[3:].strip()
        out.append(("A" if status == "??" else status[0], path))
    return out


# ---------------------------------------------------------------------------------- the drill

def gh(*args, timeout=60):
    return subprocess.run(["gh", *args], capture_output=True, text=True, timeout=timeout, cwd=common.ROOT)


def dispatch_drill(st_path, branch, idea):
    """Starts trials.yml on the skill's branch with drill.py's plan; returns its run id or None."""
    plan = subprocess.run([sys.executable, os.path.join(common.ROOT, "scripts", "loop", "drill.py"), "plan",
                           "--idea", json.dumps(idea), "--state", st_path, "--n", "3", "--format", "extra"],
                          capture_output=True, text=True, timeout=60)
    if plan.returncode:
        print("drill plan failed: " + plan.stderr[-300:])
        return None
    r = gh("workflow", "run", "trials.yml", "--ref", branch, "-f", "seeds=[]", "-f", "extra=" + plan.stdout.strip(),
           "-f", "minutes=20")
    if r.returncode:
        print("drill dispatch failed: " + (r.stderr or r.stdout)[-300:])
        return None
    return find_run(branch)


def find_run(branch, tries=8):
    for _ in range(tries):
        r = gh("run", "list", "--workflow", "trials.yml", "--branch", branch, "--limit", "1", "--json", "databaseId")
        if r.returncode == 0:
            runs = json.loads(r.stdout or "[]")
            if runs:
                return runs[0]["databaseId"]
        time.sleep(5)
    return None


def check_drills(st, state_dir, gen, log=print):
    """For each skill genome waiting on its drill: PASS makes it a contender (it races next), FAIL
    or no result after DRILL_WAIT_GENS generations rejects it. Called by loop.py propose."""
    runs_root = os.path.join(os.path.dirname(os.path.abspath(state_dir)), "runs")
    for g in st["genomes"].values():
        if g["status"] != "drilling":
            continue
        d = g.setdefault("drill", {})
        verdict, why = None, ""
        try:
            if not d.get("run"):
                d["run"] = find_run(g["code"]["branch"], tries=1)
            status = None
            if d.get("run"):
                r = gh("run", "view", str(d["run"]), "--json", "status,conclusion,jobs")
                status = json.loads(r.stdout) if r.returncode == 0 else None
            if status and status.get("status") == "completed":
                unit = [j for j in status.get("jobs", []) if j.get("name") == "unit-tests"]
                batch = os.path.join(runs_root, str(d["run"]))
                if unit and unit[0].get("conclusion") != "success":
                    verdict, why = "FAIL", "the unit tests (pr-check's Java check) failed on its commit"
                elif not os.path.isdir(batch):
                    # Finished after this checkout of trial-results was taken: next generation.
                    if gen - d.get("gen", g["born"]) >= DRILL_WAIT_GENS:
                        verdict, why = "FAIL", "its drill left no results (runs/%s)" % d["run"]
                else:
                    v = drill.cmd_report(SimpleNamespace(idea=json.dumps(d["idea"]), batch=batch, json=None, base=None, head=None))[0]
                    verdict, why = v["verdict"], v["text"]
            elif gen - d.get("gen", g["born"]) >= DRILL_WAIT_GENS:
                verdict, why = "FAIL", "no drill result after %d generations" % DRILL_WAIT_GENS
        except (subprocess.SubprocessError, OSError, ValueError, KeyError, IndexError) as e:
            log("drill check %s: %s" % (g["id"], e))
            continue
        if not verdict:
            continue
        d["verdict"], d["why"] = verdict, why
        g["status"] = "contender" if verdict == "PASS" else "rejected"
        for a in st.get("skill_attempts", []):
            if a["gid"] == g["id"]:
                a["result"] = "racing" if verdict == "PASS" else "drill failed: " + why
        log("%s drill %s: %s" % (g["id"], verdict, why))


# ---------------------------------------------------------------------------------- one attempt

def run(a, st, st_path, s, target, genes, today, used, t0, prefix="evolve/"):
    """One skill attempt (budget and plateau gates already passed in evolve.py)."""
    prompt = prompt_for(st, a.state, a.batch, target, genes, s["max_prompt_chars"])
    base = evolve.git("rev-parse", "HEAD")
    result, why, touched = None, "", []
    for attempt in range(2):
        if attempt and used >= s["max_per_day"] and not a.force:
            why += " (no retry: today's budget is spent)"
            break
        if attempt and time.time() - t0 > (a.minutes - 25) * 60:
            why += " (no retry: out of time)"
            break
        used += 1
        st["evolve_days"][today] = used
        common.write_json(st_path, st)
        try:
            so = evolve.ask_claude(prompt if attempt == 0 else prompt + "\n\nYOUR LAST SKILL FAILED ITS GATES:\n" + why +
                                   "\nFix it and return the whole skill again.", a.claude, SYSTEM, SCHEMA,
                                   "Write the new skill as JSON. The evidence and the framework are on stdin.")
        except Exception as e:  # noqa: BLE001 - a failed call just ends this cycle
            why = str(e)
            break
        try:
            touched = apply_skill(so, target, st["gen"], genes)
            bad = fairplay.check_paths(changed_paths())
            if bad:
                raise ValueError("; ".join(bad))
        except ValueError as e:
            why = str(e)[:2500]
            undo()
            touched = []
            continue
        ok, log = build_check()
        if ok:
            result = so
            break
        why = "build or tests failed:\n" + "\n".join(l for l in log.splitlines() if "error" in l.lower() or "FAIL" in l)[:2500]
        undo()
        touched = []
    st = common.read_json(st_path)
    log_entry = {"gen": st["gen"], "mode": "skill", "target": target["name"]}
    if not result:
        st.setdefault("code_failures", []).append({"gen": st["gen"], "summary": "(no usable skill for %s)" % target["name"], "why": why})
        st.setdefault("evolve_log", []).append(dict(log_entry, result="no usable skill"))
        common.write_json(st_path, st)
        print("evolve: no usable skill: " + why[:400])
        return
    so = result
    gid = "g%d" % st["next_id"]
    st["next_id"] += 1
    branch = prefix + gid
    skill, gene = so["skill"], fairplay.gene_name(so["skill"])
    evolve.git("checkout", "-q", "-b", branch)
    evolve.git("add", *touched)
    evolve.git("-c", "user.name=github-actions[bot]", "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
               "commit", "-q", "-m", "Loop %s: new skill %s (%s)\n\n%s\n\nWritten by Claude (scripts/loop/evolve_skill.py) for "
               "generation %d's costliest lasting failure; behind gene %s (default off).\n\n"
               "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>" % (gid, skill, target["name"], so["summary"], st["gen"], gene))
    sha = evolve.git("rev-parse", "HEAD")
    evolve.git("push", "-q", "origin", "%s:refs/heads/%s" % (sha, branch))
    evolve.git("checkout", "-q", base)
    champ = st["genomes"][st["champion"]]
    idea = {gene: 1, "_metric": target["metric"]}
    st["genomes"][gid] = {
        "id": gid, "parent": champ["id"], "genes": dict(champ["genes"], **{gene: 1.0}), "born": st["gen"],
        "code": {"branch": branch, "sha": sha, "base": base, "summary": so["summary"], "files": touched,
                 "kind": "skill", "skill": skill, "gene": gene, "target": target["name"]},
        "status": "drilling", "mutated": ["code", gene], "metric": target["metric"],
        "note": "code: new skill %s for %s: %s" % (skill, target["name"], so["summary"]),
        "drill": {"idea": idea, "gen": st["gen"]}, "evals": [], "pairs": []}
    if so.get("lesson"):
        st.setdefault("insights", []).append(so["lesson"].strip())
    st.setdefault("skill_attempts", []).append({"gen": st["gen"], "gid": gid, "skill": skill, "target": target["name"],
                                                "result": "drilling"})
    st.setdefault("evolve_log", []).append(dict(log_entry, gid=gid, result="drilling"))
    common.write_json(st_path, st)
    run_id = dispatch_drill(st_path, branch, idea)
    st = common.read_json(st_path)
    st["genomes"][gid]["drill"]["run"] = run_id
    common.write_json(st_path, st)
    print("evolve: new skill %s (%s) on %s for %s; drill run %s" % (skill, gene, branch, target["name"], run_id))


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    v = sub.add_parser("verdict", help="read the drills of skills waiting on one")
    v.add_argument("--state", required=True)
    t = sub.add_parser("target", help="print the failure a new skill would target now")
    t.add_argument("--state", required=True)
    t.add_argument("--force", action="store_true")
    a = ap.parse_args()
    st_path = os.path.join(a.state, "state.json")
    st = common.read_json(st_path)
    if a.cmd == "target":
        print(json.dumps(pick_target(st, a.state, strict=not a.force), indent=1))
        return
    check_drills(st, a.state, st["gen"])
    common.write_json(st_path, st)


if __name__ == "__main__":
    main()
