#!/usr/bin/env python3
"""Self-test for drill.py: python scripts/loop/test_drill.py (no pytest needed)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import drill  # noqa: E402


def test_each_idea_drills_where_it_acts():
    assert drill.kind({"portal.carve_search": 1, "_stages": ["lava"]}) == "lava"
    assert drill.kind({"skill.cast_portal": 1, "_metric": "ok:build_portal|cast_portal"}) == "lava"
    assert drill.kind({"combat.backstep": 1, "_metric": "deaths"}) == "combat"
    assert drill.kind({"nav.surface_v2": 1, "_metric": "ok:goto"}) == "natural"
    assert drill.metric_of({"x": 1, "_stages": ["lava"]}) == "ok:build_portal|cast_portal"


def test_verdict_needs_evidence_to_fail():
    assert drill.verdict(-2.1, False, drill.enough("ok:goto", (0, 1), (8, 9))) == "PASS"
    assert drill.verdict(-2.1, False, drill.enough("ok:goto", (2, 10), (8, 10))) == "FAIL"
    assert drill.verdict(0.5, True, True) == "FAIL"  # the idea's side never finished a game
    assert drill.verdict(-3.0, False, drill.enough("deaths", (6, 30), (0, 30))) == "FAIL"


def test_a_failed_idea_is_not_drilled_again():
    import json
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        base, head = os.path.join(d, "base.json"), os.path.join(d, "head.json")
        json.dump({"suggest": []}, open(base, "w"))
        json.dump({"suggest": [{"a.b": 1, "_drill": "fail"}, {"c.d": 1}]}, open(head, "w"))
        assert drill.new_ideas(base, head) == [{"c.d": 1}]


def test_an_idea_that_never_ran_is_inconclusive():
    assert drill.verdict(2.4, False, True, exposed=False) == "INCONCLUSIVE"
    assert drill.verdict(-3.0, False, True, exposed=False) == "INCONCLUSIVE"
    assert drill.verdict(0.0, True, True, exposed=False) == "FAIL"  # a crash is still a fail
    # Worse pooled, but better in most worlds: one bad world is not clear harm.
    assert drill.verdict(-2.0, False, True, exposed=True, worse_worlds=False) == "PASS"


def _game(d, name, rows):
    import json
    run = os.path.join(d, name)
    os.makedirs(run)
    with open(os.path.join(run, "run-2026-10-04.jsonl"), "w") as f:
        f.write("\n".join(json.dumps(r) for r in rows) + "\n")
    with open(os.path.join(run, "autopilot-test.log"), "w") as f:
        f.write("[12:00:00] [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] FINAL natural: brain rules, "
                "goal x, doing y, milestone 1/13, deaths 0, dimension overworld, milestone times none, checkpoints none\n")


def _shore(ok, n):
    return [{"event": "skill_end", "skill": "shore", "ok": ok, "code": None if ok else "TIMEOUT", "t": 1000 * i}
            for i in range(n)]


def _report(d, idea):
    import json
    from types import SimpleNamespace
    return drill.cmd_report(SimpleNamespace(idea=json.dumps(idea), batch=d, json=None, base=None, head=None))[0]


def test_report_of_g164_shape_is_inconclusive_not_pass():
    """g164's drill: shore did better on its side (12/13 vs 8/16) but swim_out was never offered."""
    import tempfile
    idea = {"evolved.swim_out": 1, "_metric": "ok:shore"}
    with tempfile.TemporaryDirectory() as d:
        for i in range(3):
            _game(d, "trial-drill-0-off-%d" % i, _shore(False, 4) + _shore(True, 1))
            _game(d, "trial-drill-0-on-%d" % i, _shore(True, 4) + [{"options": ["shore"], "choice": "shore"}])
        v = _report(d, idea)
        assert v["verdict"] == "INCONCLUSIVE" and v["acted_games"] == 0, v
    with tempfile.TemporaryDirectory() as d:
        # The same games, but the skill was offered, chosen and started: judged on its metric.
        for i in range(3):
            _game(d, "trial-drill-0-off-%d" % i, _shore(False, 4) + _shore(True, 1))
            _game(d, "trial-drill-0-on-%d" % i, [{"options": ["swim_out", "shore"], "choice": "swim_out"},
                                                 {"event": "skill_start", "skill": "swim_out"}] + _shore(True, 4))
        v = _report(d, idea)
        assert v["verdict"] == "PASS" and v["acted_games"] == 3 and v["exposure"]["starts"] == 3, v


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
