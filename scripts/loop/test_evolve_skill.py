#!/usr/bin/env python3
"""Self-test for level 3 v2 (evolve_skill.py): python scripts/loop/test_evolve_skill.py"""
import gzip
import json
import os
import subprocess
import sys
import tempfile
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import evolve_skill  # noqa: E402
import fairplay  # noqa: E402
import test_fairplay  # noqa: E402


def write_gen(state_dir, gen, fails):
    d = os.path.join(state_dir, "data", "gen-%05d" % gen)
    os.makedirs(d, exist_ok=True)
    with gzip.open(os.path.join(d, "eval-g1-0.jsonl.gz"), "wt", encoding="utf-8") as f:
        for a, c, sec in fails:
            f.write(json.dumps({"k": "s", "a": a, "ok": False, "c": c, "sec": sec}) + "\n")


def test_the_target_is_a_lasting_costly_failure():
    with tempfile.TemporaryDirectory() as d:
        for g in range(8):
            fails = [("explore:any", "HAZARD", 300), ("collect:log", "STUCK", 100)]
            if g == 7:
                fails.append(("goto:surface", "TIMEOUT", 5000))  # one bad generation isn't lasting
            if g % 2:
                fails.append(("attack:zombie", "INTERRUPTED", 9000))  # interruptions say nothing
            write_gen(d, g, fails)
        with patch.object(evolve_skill, "existing_targets", return_value=set()):
            t = evolve_skill.pick_target({}, d)
            assert t["name"] == "explore:any HAZARD" and t["metric"] == "ok:explore" and t["persistent"], t
            assert evolve_skill.pick_target({}, d, strict=False)["name"] == "goto:surface TIMEOUT"
            lost = {"skill_attempts": [{"gen": 1, "gid": "g9", "skill": "x", "target": "explore:any HAZARD",
                                        "result": "drill failed: z -2"}]}
            assert evolve_skill.pick_target(lost, d)["name"] == "collect STUCK"
        with patch.object(evolve_skill, "existing_targets", return_value={"explore:any HAZARD", "collect STUCK"}):
            assert evolve_skill.pick_target({}, d) is None


def scratch_repo(d):
    """The files apply_skill reads and writes, with the real registry markers."""
    def put(rel, text):
        p = os.path.join(d, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "w", encoding="utf-8").write(text)
    put("mod/src/main/java/io/github/plrlr/autopilot/skills/Skills.java", 'MENU.put("collect", x);\nMENU.put("shore", y);\n')
    put(fairplay.REGISTRY, "class EvolvedSkills {\n\tstatic {\n\t\t// evolve:entries (...)\n\t\t// evolve:end\n\t}\n}\n")
    put(fairplay.GENES_FILE, json.dumps({"_about": "x", "genes": []}))


def claude_says(src=test_fairplay.GOOD, skill="leave_water", cls="LeaveWater"):
    return {"summary": "swim to land", "lesson": "l", "skill": skill, "class_name": cls, "help": "swim to seen dry ground",
            "files": [{"name": cls + ".java", "content": src}]}


TARGET = {"name": "collect WRONG_PLACE", "metric": "ok:collect"}


def test_a_fair_skill_is_written_with_its_line_and_gene():
    with tempfile.TemporaryDirectory() as d, patch.object(common, "ROOT", d):
        scratch_repo(d)
        for cmd in (["init", "-q"], ["add", "."], ["-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "base"]):
            subprocess.run(["git", *cmd], cwd=d, check=True, capture_output=True)
        touched = evolve_skill.apply_skill(claude_says(), TARGET, 86, {"reflex.creeper_dist": {}})
        assert touched == [fairplay.EVOLVED_DIR + "LeaveWater.java", fairplay.REGISTRY, fairplay.GENES_FILE], touched
        reg = open(os.path.join(d, fairplay.REGISTRY), encoding="utf-8").read()
        line = '\t\tadd(new Entry("evolved.leave_water", "leave_water", LeaveWater::new, LeaveWater::offer, "swim to seen dry ground"));'
        assert line + "\n\t\t// evolve:end" in reg, reg
        genes = json.load(open(os.path.join(d, fairplay.GENES_FILE)))["genes"]
        assert genes == [{"name": "evolved.leave_water", "kind": "BOOL", "def": 0, "skill": "leave_water",
                          "why": "swim to seen dry ground", "gen": 86, "target": "collect WRONG_PLACE", "metric": "ok:collect"}]
        # The real git status, as the loop reads it (the first end-to-end test misread its first line).
        changed = evolve_skill.changed_paths()
        assert sorted(changed) == sorted([("A", touched[0]), ("M", touched[1]), ("M", touched[2])]), changed
        assert fairplay.check_paths(changed) == []
        evolve_skill.undo()
        assert evolve_skill.changed_paths() == []


def test_a_cheat_or_a_clash_writes_nothing():
    cheat = test_fairplay.GOOD.replace("Player.inWater() && Player.onGround()", "Mc.player().isInWater()")
    cases = [claude_says(cheat), claude_says(skill="collect"), claude_says(cls="lower"),
             claude_says(skill="leave_water", cls="Player")]
    for so in cases:
        with tempfile.TemporaryDirectory() as d, patch.object(common, "ROOT", d):
            scratch_repo(d)
            before = open(os.path.join(d, fairplay.REGISTRY)).read()
            try:
                evolve_skill.apply_skill(so, TARGET, 86, {})
                raise AssertionError("accepted: %s" % so["skill"])
            except ValueError:
                pass
            assert open(os.path.join(d, fairplay.REGISTRY)).read() == before
            assert not os.path.exists(os.path.join(d, fairplay.EVOLVED_DIR, so["class_name"] + ".java")) or so["class_name"] == "Player"


def drill_state(gen_born=90):
    g = {"id": "g70", "status": "drilling", "born": gen_born, "code": {"branch": "evolve/g70"},
         "drill": {"run": 123, "idea": {"evolved.x": 1, "_metric": "ok:collect"}, "gen": gen_born}}
    return {"genomes": {"g70": g}, "skill_attempts": [{"gen": gen_born, "gid": "g70", "skill": "x", "target": "t", "result": "drilling"}]}


def fake_gh(status, unit="success"):
    view = {"status": status, "conclusion": "success", "jobs": [{"name": "unit-tests", "conclusion": unit}]}
    return lambda *a, **k: SimpleNamespace(returncode=0, stdout=json.dumps(view))


def test_the_drill_decides_whether_it_races():
    with tempfile.TemporaryDirectory() as d:
        state_dir = os.path.join(d, "loop")
        os.makedirs(os.path.join(d, "runs", "123"))
        verdict = [{"verdict": "PASS", "text": "PASS: on 9/10 ok, off 5/10 ok, z +1.9"}]
        with patch.object(evolve_skill, "gh", fake_gh("completed")), patch.object(evolve_skill.drill, "cmd_report", return_value=verdict):
            st = drill_state()
            evolve_skill.check_drills(st, state_dir, 91, log=lambda *_: None)
            assert st["genomes"]["g70"]["status"] == "contender" and st["skill_attempts"][0]["result"] == "racing"
        with patch.object(evolve_skill, "gh", fake_gh("completed", unit="failure")):
            st = drill_state()
            evolve_skill.check_drills(st, state_dir, 91, log=lambda *_: None)
            assert st["genomes"]["g70"]["status"] == "rejected"
        with patch.object(evolve_skill, "gh", fake_gh("in_progress")):
            st = drill_state()
            evolve_skill.check_drills(st, state_dir, 91, log=lambda *_: None)
            assert st["genomes"]["g70"]["status"] == "drilling"  # still playing: wait
            evolve_skill.check_drills(st, state_dir, 95, log=lambda *_: None)
            assert st["genomes"]["g70"]["status"] == "rejected"  # never finished


def test_gh_trouble_waits():
    def boom(*a, **k):
        raise subprocess.TimeoutExpired("gh", 60)
    with tempfile.TemporaryDirectory() as d, patch.object(evolve_skill, "gh", boom):
        st = drill_state()
        evolve_skill.check_drills(st, os.path.join(d, "loop"), 91, log=lambda *_: None)
        assert st["genomes"]["g70"]["status"] == "drilling"


def test_the_loop_knows_evolved_genes():
    """A skill that won is in main's evolved-genes.json: exports and mutations must keep its gene."""
    with tempfile.TemporaryDirectory() as d:
        tune = os.path.join(d, "mod/src/main/java/io/github/plrlr/autopilot/Tune.java")
        os.makedirs(os.path.dirname(tune))
        open(tune, "w").write('gene("a.b", 1, 0, 2, Kind.INT, "x");\n')
        os.makedirs(os.path.join(d, "mod/src/main/resources"))
        json.dump({"genes": [{"name": "evolved.leave_water", "kind": "BOOL", "def": 0, "skill": "leave_water", "why": "w"},
                             {"name": "evolved.bad", "kind": "REAL", "def": 0, "skill": "bad", "why": "no"}]},
                  open(os.path.join(d, "mod/src/main/resources/evolved-genes.json"), "w"))
        genes = common.load_genes(tune)
        assert list(genes) == ["a.b", "evolved.leave_water"], genes
        assert genes["evolved.leave_water"]["kind"] == "BOOL" and genes["evolved.leave_water"]["max"] == 1.0


def test_patches_cant_touch_the_fair_play_boundary():
    """Edit mode (evolve.apply_edits) may change game code, but not the facade, the registry, the
    evolved genes or FairProbe: widening those would open a door for every later generated skill."""
    import evolve
    for rel in ("skills/evolved/Player.java", "skills/evolved/EvolvedSkills.java", "EvolvedGenes.java",
                "skills/FairProbe.java", "skills/EvolvedActions.java", "Tune.java"):
        try:
            evolve.apply_edits([{"file": rel, "search": "x", "replace": "y"}], dry=True)
            raise AssertionError("a patch may edit " + rel)
        except ValueError as e:
            assert "not allowed" in str(e), (rel, e)


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
