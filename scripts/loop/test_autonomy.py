#!/usr/bin/env python3
"""Self-test for the loop running on its own: python scripts/loop/test_autonomy.py.

The plan's limit must not count as failed attempts (gens 120-166 lost 45 that way), and the
behavior report level 3 reads must reflect the games."""
import datetime
import gzip
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import evolve  # noqa: E402
import patterns  # noqa: E402

LIMIT_JSON = json.dumps({"type": "result", "subtype": "success", "is_error": True, "num_turns": 1,
                         "api_error_status": 429, "result": "You've hit your weekly limit · resets 3am (UTC)"})
UTC = datetime.timezone.utc


def test_a_limit_is_recognized_from_the_json_not_its_tail():
    msg, limited = evolve.claude_error(LIMIT_JSON, "")
    assert limited and "weekly limit" in msg
    msg, limited = evolve.claude_error(json.dumps({"is_error": True, "result": "Invalid schema"}), "")
    assert not limited and msg == "Invalid schema"
    assert evolve.claude_error("not json", "boom")[0] == "boom"


def test_reset_time_is_the_next_one_in_utc():
    now = datetime.datetime(2026, 10, 3, 20, 0, tzinfo=UTC)
    assert evolve.resume_time("resets 3am (UTC)", now) == datetime.datetime(2026, 10, 4, 3, 0, tzinfo=UTC)
    assert evolve.resume_time("resets 11:30pm (UTC)", now) == datetime.datetime(2026, 10, 3, 23, 30, tzinfo=UTC)
    assert evolve.resume_time("try later", now) == now + datetime.timedelta(hours=3)


def test_a_limited_call_raises_rate_limited():
    with tempfile.TemporaryDirectory() as d:
        fake = os.path.join(d, "claude.py")
        with open(fake, "w") as f:
            f.write("import sys\nsys.stdin.read()\nprint(%r)\nsys.exit(1)\n" % LIMIT_JSON)
        exe = os.path.join(d, "claude.cmd" if os.name == "nt" else "claude")
        with open(exe, "w") as f:
            f.write(('@"%s" "%s" %%*\n' if os.name == "nt" else '#!/bin/sh\nexec "%s" "%s" "$@"\n') % (sys.executable, fake))
        os.chmod(exe, 0o755)
        os.environ["RUNNER_TEMP"] = d
        try:
            evolve.ask_claude("prompt", exe)
        except evolve.RateLimited as e:
            assert e.until > datetime.datetime.now(UTC)
        else:
            raise AssertionError("expected RateLimited")


def test_limit_failures_are_forgotten_and_the_slot_given_back():
    st = {"code_failures": [{"gen": 150, "summary": "(no usable patch)", "why": 'claude failed: ..."api_error_status":429,"result":"You\'ve hit your weekly limit'},
                            {"gen": 151, "summary": "(no usable patch)", "why": "compile errors: x"}],
          "evolve_log": [{"gen": 150, "mode": "edit", "result": "no usable patch"},
                         {"gen": 151, "mode": "edit", "result": "no usable patch"},
                         {"gen": 152, "mode": "edit", "gid": "g9", "result": "racing"}],
          "evolve_days": {"2026-10-04": 3}}
    assert evolve.forget_limit_failures(st)
    assert [c["gen"] for c in st["code_failures"]] == [151]
    assert [e["gen"] for e in st["evolve_log"]] == [151, 152]
    assert not evolve.forget_limit_failures(st)
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "state.json")
        common.write_json(p, st)
        evolve.pause_for_limit(p, "2026-10-04", evolve.RateLimited("limit", datetime.datetime(2026, 10, 5, 3, 0, tzinfo=UTC)))
        st = common.read_json(p)
        assert st["evolve_days"]["2026-10-04"] == 2 and st["evolve_paused_until"].startswith("2026-10-05T03:00")


def _game(d, gen, slot, rows):
    g = os.path.join(d, "data", "gen-%05d" % gen)
    os.makedirs(g, exist_ok=True)
    with gzip.open(os.path.join(g, "eval-g1-%d.jsonl.gz" % slot), "wt", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")


def test_patterns_count_thrash_refusals_and_deaths_in_first_lives_only():
    feats = common.load_features()

    def x(hp, food):
        v = [0.0] * len(feats)
        v[feats.index("hp")], v[feats.index("food")], v[feats.index("underground")] = hp / 20, food / 20, 1
        return v
    rows = [{"k": "d", "gs": 0, "a": "smelt:iron_ingot", "x": x(20, 20)}, {"k": "d", "gs": 5, "a": "smelt:cooked_beef", "x": x(20, 20)},
            {"k": "d", "gs": 10, "a": "smelt:iron_ingot", "x": x(20, 20)},
            {"k": "s", "gs": 20, "a": "collect:log", "ok": False, "c": "NOT_FOUND", "sec": 3},
            {"k": "s", "gs": 25, "a": "collect:log", "ok": False, "c": "NOT_FOUND", "sec": 3},
            {"k": "d", "gs": 90, "a": "retreat", "u": True, "x": x(3, 14)},
            {"k": "death", "gs": 100, "detail": "arrow:skeleton"},
            {"k": "s", "gs": 200, "a": "shore", "ok": False, "c": "TIMEOUT", "sec": 90}]
    with tempfile.TemporaryDirectory() as d:
        with open(os.path.join(d, "history.jsonl"), "w") as f:
            f.write(json.dumps({"gen": 1, "tasks": ["spawn"]}) + "\n")
        _game(d, 1, 0, rows)
        a = patterns.analyse(patterns.load(d, 5, "spawn"))
        assert a["games"] == 1 and a["died"] == 1 and a["life_s"] == 100
        assert a["flips"][("smelt:cooked_beef", "smelt:iron_ingot")] == 1
        assert a["repeats"]["collect:log NOT_FOUND"] == 1 and "shore" not in a["secs"]  # after the death
        assert a["causes"]["arrow:skeleton"] == 1 and a["last_pick"]["retreat"] == 1
        text = "\n".join(patterns.report(a))
        assert "median health 3/20" in text and "hunger 14/20" in text
        assert patterns.load(d, 5, "nether") == []


def test_compact_rows_keep_what_led_to_a_death():
    import loop
    t0 = 1791119311000
    raw = [{"event": "autopilot_on", "t": t0},
           {"layer": "tactician", "gs": 0, "x": [0.0] * len(common.load_features()), "options": ["fortress find"],
            "idx": 0, "choice": "fortress find", "t": t0},
           {"event": "reflex", "detail": "lava beside us", "t": t0 + 15000},
           {"event": "skill_end", "skill": "fortress find", "ok": False, "code": "DIED", "detail": "died", "seconds": 18, "t": t0 + 18000},
           {"event": "death", "detail": "lava", "t": t0 + 18100},
           {"layer": "tactician", "gs": 30, "x": [0.0] * len(common.load_features()), "options": ["collect log:3"],
            "idx": 0, "choice": "collect log:3", "t": t0 + 30000}]
    with tempfile.TemporaryDirectory() as d:
        run = os.path.join(d, "trial-eval-g1-0")
        os.makedirs(run)
        with open(os.path.join(run, "run-2026-10-04.jsonl"), "w") as f:
            f.write("\n".join(json.dumps(r) for r in raw) + "\n")
        loop.save_training_rows(d, 1, "eval-g1-0", run)
        rows = [json.loads(l) for l in gzip.open(os.path.join(d, "data", "gen-00001", "eval-g1-0.jsonl.gz"), "rt")]
        ev = [r for r in rows if r["k"] == "e"]
        assert ev == [{"k": "e", "gs": 15.0, "e": "reflex", "t": "lava beside us"}], ev
        s = next(r for r in rows if r["k"] == "s")
        assert s["c"] == "DIED" and s["t"] == "died"
        with open(os.path.join(d, "history.jsonl"), "w") as f:
            f.write(json.dumps({"gen": 1, "tasks": ["nether"]}) + "\n")
        a = patterns.analyse(patterns.load(d, 5, "nether"))
        assert a["before"]["reflex: lava beside us"] == 1, a["before"]
        assert any("lava beside us" in line for line in patterns.report(a))


def test_late_practice_rotates_past_the_frontier_and_is_reported():
    import random
    import bank
    st = {"bank": {"nether": [{"asset": "nether-1.zip", "gen": 1}]},
          "stage_stats": {"kit": [[1, 1]] * 8, "lava": [[1, 1]] * 8, "diamond": [[1, 1]] * 8, "nether": [[1, 0]] * 8}}
    stages = [bank.late_task(st, random.Random(1), g)["stage"] for g in range(4)]
    assert set(stages) == {"eyes", "end"} and all(s in ("eyes", "end") for s in stages), stages
    t = bank.late_task(st, random.Random(1), 0)
    assert t["late"] and t["kind"] == "scenario" and t["synthetic"]
    with tempfile.TemporaryDirectory() as d:
        with open(os.path.join(d, "history.jsonl"), "w") as f:
            f.write(json.dumps({"gen": 1, "tasks": ["spawn"], "data_tasks": ["end*", "spawn"]}) + "\n")
        g = os.path.join(d, "data", "gen-00001")
        os.makedirs(g)
        for name in ("data-g1-0", "data-g1-1", "eval-g1-0"):
            with gzip.open(os.path.join(g, name + ".jsonl.gz"), "wt", encoding="utf-8") as f:
                f.write(json.dumps({"k": "s", "gs": 5, "a": "dragon_fight", "ok": False, "c": "TIMEOUT", "sec": 60}) + "\n")
        assert len(patterns.load(d, 5, "data:end")) == 1
        assert len(patterns.load(d, 5, None)) == 1  # all stages: scored games only
        assert "[end practice" in patterns.text(d, 5)


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
