#!/usr/bin/env python3
"""Self-test: code genomes (level 3) get raced, on this generation's main, and never block evolve.py.

    python scripts/loop/test_code_genomes.py

2026-09-30: g29 sat as a "contender" for 44 generations without one run (queued ideas filled every
slot), and evolve.py wrote nothing while it "raced". These tests pin the fix.
"""
import os
import subprocess
import sys
import tempfile
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import loop  # noqa: E402


def genome(gid, born, note, code=None, evals=None, status="contender", genes=None):
    g = loop.new_genome(gid, "g0", genes or {}, born, ["code"] if code else [], note)
    g.update(status=status, code=code, evals=evals or [])
    return g


def propose(st):
    with tempfile.TemporaryDirectory() as d:
        common.write_json(os.path.join(d, "state.json"), st)
        out = os.path.join(d, "runs.json")
        with patch.dict(os.environ, {"LOOP_NO_REBASE": "1", "LOOP_NO_UPLOAD": "1"}):
            loop.cmd_propose(SimpleNamespace(state=d, sha="main-sha", out=out, override=""))
        return common.read_json(os.path.join(d, "state.json")), common.read_json(out)


def base_state(gen, genomes):
    st = {"version": 1, "gen": gen, "champion": "g0", "next_id": 100, "days": {}, "pending": None,
          "genomes": {"g0": genome("g0", 0, "defaults", status="champion")}, "sigma": {}, "credit": {},
          "settings": {}}
    for g in genomes:
        st["genomes"][g["id"]] = g
    return st


def test_stale_code_genome_retires_and_a_fresh_one_races_first():
    code = {"branch": "evolve/g90", "sha": "abc", "base": "main-sha", "summary": "x", "files": []}
    stale = genome("g29", 41, "code: old cast change", code=dict(code, branch="evolve/g29"))
    fresh = genome("g90", 85, "code: new change", code=code)
    st, out = propose(base_state(85, [stale, fresh]))
    assert st["genomes"]["g29"]["status"] == "retired", st["genomes"]["g29"]
    lineup = st["pending"]["lineup"]
    assert lineup[:2] == ["g0", "g90"], lineup  # ahead of every queued idea
    assert any(r["genome"] == "g90" and r["ref"] == "abc" for r in out["runs"])


def test_learned_idea_only_when_the_gate_is_open():
    st = base_state(90, [])
    assert loop.learned_idea(st) == []
    st["model_adv_lo"] = -0.004
    assert loop.learned_idea(st) == []
    st["model_adv_lo"] = 0.003
    idea = loop.learned_idea(st)
    assert idea and idea[0]["learned.weight"] == 1 and idea[0]["_priority"]
    st2, _ = propose(st)
    lr = st2["learned_race"]
    assert st2["genomes"][lr["gid"]]["genes"].get("learned.weight") == 1.0
    assert lr["gid"] in st2["pending"]["lineup"]
    assert loop.learned_idea(st2) == []  # racing: not queued twice
    st2["genomes"][lr["gid"]]["status"] = "rejected"
    st2["gen"] = lr["gen"] + 10
    assert loop.learned_idea(st2)  # lost, gate still open, 10 generations later: again


def git(*a, cwd):
    return subprocess.run(["git", *a], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def repo_with_branch(d, conflicting):
    """origin (bare) with main and evolve/g5 (one commit on main's old tip); main then moves on."""
    origin, work = os.path.join(d, "origin.git"), os.path.join(d, "work")
    git("init", "-q", "--bare", origin, cwd=d)
    git("clone", "-q", origin, work, cwd=d)
    for k, v in (("user.name", "t"), ("user.email", "t@t"), ("commit.gpgsign", "false")):
        git("config", k, v, cwd=work)
    open(os.path.join(work, "a.txt"), "w").write("one\ntwo\nthree\n")
    git("add", ".", cwd=work)
    git("commit", "-qm", "base", cwd=work)
    git("branch", "-M", "main", cwd=work)
    base = git("rev-parse", "HEAD", cwd=work)
    git("checkout", "-qb", "evolve/g5", cwd=work)
    open(os.path.join(work, "a.txt"), "w").write("ONE\ntwo\nthree\n")
    git("commit", "-qam", "the change", cwd=work)
    sha = git("rev-parse", "HEAD", cwd=work)
    git("checkout", "-q", "main", cwd=work)
    if conflicting:
        open(os.path.join(work, "a.txt"), "w").write("uno\ntwo\nthree\n")
    else:
        open(os.path.join(work, "b.txt"), "w").write("new file\n")
        git("add", ".", cwd=work)
    git("commit", "-qam", "main moves on", cwd=work)
    main = git("rev-parse", "HEAD", cwd=work)
    git("push", "-q", "origin", "main", "evolve/g5", cwd=work)
    return work, origin, base, sha, main


def rebase(conflicting):
    with tempfile.TemporaryDirectory() as d:
        work, origin, base, sha, main = repo_with_branch(d, conflicting)
        code = {"branch": "evolve/g5", "sha": sha, "base": base, "summary": "x", "files": ["a.txt"]}
        st = base_state(90, [genome("g5", 88, "code: x", code=code),
                             genome("g6", 89, "child", code=dict(code))])
        with patch.object(common, "ROOT", work), patch.dict(os.environ, {"GIT_AUTHOR_NAME": "t"}):
            ok = loop.rebase_code(st, "g5", main, 90)
        remote = git("rev-parse", "refs/heads/evolve/g5", cwd=origin)
        parent = git("rev-parse", remote + "^", cwd=origin)
        return ok, st, remote, sha, main, parent


def test_rebase_replays_the_change_on_current_main():
    ok, st, remote, old, main, parent = rebase(conflicting=False)
    c = st["genomes"]["g5"]["code"]
    assert ok and c["base"] == main and c["sha"] != old and c["first_sha"] == old
    assert remote == c["sha"]  # the branch now holds the replayed commit
    assert st["genomes"]["g6"]["code"]["sha"] == c["sha"]  # a child sharing the change follows
    assert parent == main


def test_rebase_conflict_retires_the_genome():
    ok, st, remote, old, _, _ = rebase(conflicting=True)
    assert not ok and st["genomes"]["g5"]["status"] == "retired"
    assert remote == old  # nothing pushed


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
