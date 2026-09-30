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


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
