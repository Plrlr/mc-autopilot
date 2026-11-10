#!/usr/bin/env python3
"""Self-test for marathon chains (2026-11-09: legs of one game, each resumed from the last one's
save; loop.combine_legs): python scripts/loop/test_marathon.py (no pytest needed)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import loop  # noqa: E402

LEG = 7200  # 120 game minutes
SAVE = LEG - 30


def info(leg, legs=3, last=False):
    return {"chain": "c1", "leg": leg, "legs": legs, "genome": "g9", "minutes": 120, "save_at": SAVE, "last": last}


def summary(milestones=(), checkpoints=None, death_times=(), start="spawn", reached=()):
    return {"milestones": [list(m) for m in milestones], "checkpoints": dict(checkpoints or {}),
            "deaths": [("lava", "x")] * len(death_times), "death_times": list(death_times)}, start, list(reached)


def add(st, leg, s, **kw):
    r, start, reached = s
    loop.add_marathon_leg(st, info(leg, **kw), r, start, reached, LEG)


LEG1 = summary([(1, 60), (2, 300), (4, 900)], {"two_buckets": 2000}, death_times=[1000, 3000], reached=["kit"])
# Leg 2 starts with the gear (reported in its first seconds), dies once after its save (a fork).
LEG2 = summary([(1, 2), (2, 2), (4, 3)], {"two_buckets": 4, "at_depth": 1500}, death_times=[7190], start="kit", reached=["diamond"])
LEG3 = summary([(1, 2), (2, 2), (4, 3), (7, 4000)], {"portal_lit": 3500}, start="diamond", reached=["nether"])


def test_three_legs_count_progress_after_deaths_once():
    st = {}
    add(st, 1, LEG1)
    add(st, 2, LEG2)
    add(st, 3, LEG3)
    c = st["marathons"]["c1"]["summary"]
    assert c["status"] == "complete" and c["legs"] == 3, c
    assert c["deaths"] == 2  # leg 2's death came after its save: the chain went on from before it
    assert c["furthest"] == "nether"
    ms = dict((m, t) for m, t in c["milestones"])
    assert ms == {1: 60, 2: 300, 4: 900, 7: 2 * SAVE + 4000}, ms
    assert c["checkpoints"] == {"two_buckets": 2000, "at_depth": SAVE + 1500, "portal_lit": 2 * SAVE + 3500}
    whole = {"milestones": c["milestones"], "checkpoints": c["checkpoints"], "deaths": [None] * 2,
             "death_times": [1000, 3000]}
    assert c["score"] == common.score_run(whole, 3 * LEG)
    assert st["marathon_latest"]["chain"] == "c1" and st["marathon_latest"]["genome"] == "g9"
    assert abs(c["game_hours"] - round((2 * SAVE + LEG) / 3600, 2)) < 0.01


def test_out_of_order_and_duplicate_legs():
    st = {}
    add(st, 3, LEG3)
    add(st, 1, LEG1)
    assert st["marathons"]["c1"]["summary"]["status"] == "incomplete" and "marathon_latest" not in st
    add(st, 2, LEG2)
    add(st, 2, LEG2)
    ref = {}
    for k, s in ((1, LEG1), (2, LEG2), (3, LEG3)):
        add(ref, k, s)
    assert st["marathons"]["c1"]["summary"] == ref["marathons"]["c1"]["summary"]


def test_missing_middle_leg_is_incomplete_not_scored():
    st = {}
    add(st, 1, LEG1)
    add(st, 3, LEG3)
    c = st["marathons"]["c1"]["summary"]
    assert c["status"] == "incomplete" and c["score"] is None, c
    assert "marathon_latest" not in st


def test_chain_ended_early_by_its_last_leg():
    st = {}
    add(st, 1, LEG1)
    add(st, 2, LEG2, last=True)  # no save: the chain stops at leg 2 of 3
    c = st["marathons"]["c1"]["summary"]
    assert c["status"] == "complete" and c["legs"] == 2 and c["deaths"] == 3, c  # leg 2 is the end: its death counts
    assert c["score"] is not None


def test_running_chain_has_no_score_yet():
    st = {}
    add(st, 1, LEG1)
    assert st["marathons"]["c1"]["summary"]["status"] == "running"
    assert st["marathons"]["c1"]["summary"]["score"] is None


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
