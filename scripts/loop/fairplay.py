#!/usr/bin/env python3
"""The fair-play gate for skills the loop writes itself (evolve.py, skill mode).

    python scripts/loop/fairplay.py FILE.java [--skill NAME]     # prints violations, exit 1 if any

CLAUDE.md's hard rules (vanilla, fair play, no x-ray, no Baritone searches, never block the game
thread) can't be left to a prompt: a generated skill is checked here before it compiles, races or
touches main. It may use ONLY:

- the evolved package's facade: Player (the normal player controls; blocks and mobs only as the
  player sees them), EvolvedSkill, Context, Offer (same package, no import needed),
- Skill and Fail, Option, WorldMemory (what the player has seen; read methods), FairProbe,
- BlockPos, Direction, Vec3 (plain values) and a short list of java.util types.

So it's an allowlist of imports, plus bans on names that reach the game or the machine without an
import (Mc, level, Baritone, threads, reflection, files, processes, fully qualified names), plus a
fixed shape: package, one public class named after its file that extends EvolvedSkill, a static
offer(Context), and name() returning its own skill name. The threat is a shortcut, not an attacker:
a model reaching for Mc.state() to find a block it can't see. Compiling is the next gate, the drill
the one after, the race the last.
"""
import argparse
import json
import re
import sys

PACKAGE = "io.github.plrlr.autopilot.skills.evolved"
EVOLVED_DIR = "mod/src/main/java/io/github/plrlr/autopilot/skills/evolved/"
GENES_FILE = "mod/src/main/resources/evolved-genes.json"
REGISTRY = EVOLVED_DIR + "EvolvedSkills.java"
# The framework: fixed by hand, never written by a patch.
FRAMEWORK = ("Player.java", "EvolvedSkill.java", "Context.java", "Offer.java", "EvolvedSkills.java")

ALLOWED_IMPORTS = {
    "io.github.plrlr.autopilot.skills.Skill", "io.github.plrlr.autopilot.skills.Fail",
    "io.github.plrlr.autopilot.skills.FairProbe", "io.github.plrlr.autopilot.plan.Option",
    "io.github.plrlr.autopilot.state.WorldMemory",
    "net.minecraft.core.BlockPos", "net.minecraft.core.Direction", "net.minecraft.world.phys.Vec3",
}
# Data structures only: nothing that starts threads (concurrent, Timer), reads input or loads code.
JAVA_UTIL = {"List", "ArrayList", "LinkedList", "Map", "HashMap", "LinkedHashMap", "TreeMap", "Set",
             "HashSet", "LinkedHashSet", "TreeSet", "Optional", "Comparator", "ArrayDeque", "Deque", "Queue",
             "PriorityQueue", "Arrays", "Collections", "Objects", "Random", "Iterator", "EnumMap", "EnumSet"}
ALLOWED_IMPORTS |= {"java.util." + n for n in JAVA_UTIL}
ALLOWED_IMPORT_PREFIXES = ("java.util.function.",)

# Names that reach the game, Baritone or the machine without an import (java.lang is implicit).
BANNED_NAMES = [
    (r"\bMc\b", "Mc (raw game access: use Player)"),
    (r"\bMinecraft\b|\bLocalPlayer\b|\bClientLevel\b|\bEntity\b|\bBlockState\b", "a game object (use Player)"),
    (r"\blevel\b", "level (reads blocks the player may not see: use Player.visible / FairProbe / WorldMemory)"),
    (r"getBlockState|getChunk|getEntities|entitiesForRendering|clip\s*\(", "a direct world read"),
    (r"\bBari\b|\b[Bb]aritone", "Baritone directly (use Player.walkTo / walkNear: no searches)"),
    (r"\bPerception\b", "Perception (use Player.mobs: visible mobs only)"),
    (r"\bTune\b", "Tune (genes are the loop's; the registry gates the skill)"),
    (r"\bThread\b|\bThreadLocal\b|\bExecutor\w*|\bCompletableFuture\b|\bTimer\b|\bRunnable\b", "a thread (never block or fork the game thread)"),
    (r"\bRuntime\b|\bProcess(Builder|Handle)?\b|\bSystem\b", "the machine (Runtime / processes / System)"),
    (r"\bClass\b|\.class\b|getClass\s*\(|ClassLoader|\breflect\b|MethodHandle|\bModuleLayer\b|StackWalker", "reflection"),
    (r"\bFiles?\b|\bPaths?\b|\bSocket\b|\bURL\b|\bURI\b|\bHttp\w*|\bInputStream\b|\bOutputStream\b|\bReader\b|\bWriter\b",
     "files or network"),
    (r"\bnative\b", "native code"),
    (r"\bmemory\s*\.\s*(remember|forget|scan|clear)\s*\(|\bworldKey\b", "writing WorldMemory (read it; the scan fills it)"),
]
# The same list evolve.py applies to every patch (commands, server, world edits, processes).
FORBIDDEN = ("runCommand", "performCommand", "sendCommand", "sendChat", "getServer()", "getSingleplayerServer",
             "ServerLevel", "commands.", "setBlock(", "Runtime.getRuntime", "ProcessBuilder")
QUALIFIED = re.compile(r"\b(?:net|com|java|javax|jdk|sun|baritone|io|org)\s*\.\s*[a-z]\w*\s*\.")
GENE = re.compile(r"^evolved\.([a-z][a-z0-9_]{2,39})$")
MAX_LINES = 400


def strip(src, keep_strings=False):
    """Code without comments (and string/char literals unless keep_strings): what the checks read."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            out.append(" ")
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(src[i:j] if keep_strings else '""')
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1] if keep_strings else c + c)
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def check_source(src, file_name, skill=None):
    """Violations (strings) of one generated Java file; [] when it may compile and race."""
    bad = []
    if len(src.splitlines()) > MAX_LINES:
        bad.append("longer than %d lines" % MAX_LINES)
    # Java decodes unicode escapes everywhere before it reads the code, so "Mc" is Mc to the
    # compiler and "Mc" to every check below: none may appear, strings and comments included.
    if re.search(r"\\+u+[0-9a-fA-F]{4}", src):
        bad.append("unicode escapes aren't allowed (Java decodes them before any check could read the name)")
    if any(ord(ch) > 126 for ch in strip(src)):
        bad.append("non-ASCII characters in code (write names in plain ASCII)")
    code = strip(src)
    code_s = strip(src, keep_strings=True)
    if not re.search(r"^\s*package\s+%s\s*;" % re.escape(PACKAGE), code, re.M):
        bad.append("package must be " + PACKAGE)
    body = []
    for line in code.splitlines():
        m = re.match(r"\s*import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;", line)
        if m:
            name = m.group(2)
            if m.group(1) or name.endswith(".*") or not (name in ALLOWED_IMPORTS or name.startswith(ALLOWED_IMPORT_PREFIXES)):
                bad.append("import not allowed: " + name)
        elif not re.match(r"\s*package\s", line):
            body.append(line)
    body = "\n".join(body)
    for rx, why in BANNED_NAMES:
        m = re.search(rx, body)
        if m:
            bad.append("uses %s: '%s'" % (why, m.group(0)))
    for f in FORBIDDEN:
        if f in code_s:
            bad.append("forbidden in a patch: " + f)
    m = QUALIFIED.search(body)
    if m:
        bad.append("fully qualified name (imports are checked, so name types by import): '%s'" % m.group(0))
    cls = file_name[:-5] if file_name.endswith(".java") else file_name
    for m in re.finditer(r"\bextends\s+(\w+)", body):
        if m.group(1) != "EvolvedSkill":
            bad.append("may extend only EvolvedSkill, not " + m.group(1))
    if skill is not None:
        if not re.search(r"\bpublic\s+(?:final\s+)?class\s+%s\s+extends\s+EvolvedSkill\b" % re.escape(cls), body):
            bad.append("needs 'public class %s extends EvolvedSkill'" % cls)
        if not re.search(r"\bpublic\s+static\s+Option\s+offer\s*\(\s*Context\s+\w+\s*\)", body):
            bad.append("needs 'public static Option offer(Context c)' (when to offer the skill)")
        if not re.search(r'String\s+name\s*\(\s*\)\s*\{\s*return\s+"%s"\s*;' % re.escape(skill), code_s):
            bad.append('name() must return "%s"' % skill)
    return bad


def gene_name(skill):
    return "evolved." + skill


def check_gene_list(old, new, known=()):
    """The evolved gene list may only grow at its end, by valid entries (BOOL, default 0, named
    evolved.<skill>, unique, not one of Tune's genes)."""
    bad = []
    old, new = list(old or []), list(new or [])
    if new[:len(old)] != old:
        bad.append("evolved-genes.json is append-only: an existing entry changed or moved")
    seen = set(known)
    for e in new:
        name = e.get("name", "")
        m = GENE.match(name)
        if not m or e.get("kind") != "BOOL" or e.get("def") != 0 or e.get("skill") != (m.group(1) if m else None):
            bad.append("invalid evolved gene entry: %s" % json.dumps(e)[:160])
        if name in seen:
            bad.append("duplicate gene: " + name)
        seen.add(name)
    return bad


def registry_line(skill, cls, help_text):
    """The one line a new skill adds to EvolvedSkills.java (written by evolve.py, not the model)."""
    h = re.sub(r"[^\w .,:;()+/%-]", "", help_text)[:120]
    return '\t\tadd(new Entry("%s", "%s", %s::new, %s::offer, "%s"));' % (gene_name(skill), skill, cls, cls, h)


def check_paths(changed):
    """(status, path) pairs of a patch against its base: only new files in skills/evolved/, the
    registry's one line and the gene list may change."""
    bad = []
    for status, path in changed:
        path = path.replace("\\", "/")
        if path in (REGISTRY, GENES_FILE):
            if status != "M":
                bad.append("%s must be modified, not %s" % (path, status))
        elif path.startswith(EVOLVED_DIR) and path.endswith(".java") and "/" not in path[len(EVOLVED_DIR):]:
            if status != "A" or path[len(EVOLVED_DIR):] in FRAMEWORK:
                bad.append("only new skill files may be added in skills/evolved/: %s %s" % (status, path))
        else:
            bad.append("a new skill may not touch " + path)
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--skill")
    a = ap.parse_args()
    import os
    bad = check_source(open(a.file, encoding="utf-8").read(), os.path.basename(a.file), a.skill)
    for b in bad:
        print(b)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
