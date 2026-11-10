#!/usr/bin/env python3
"""Self-test for the bank's reconcile (2026-11-10). python scripts/loop/test_bank.py (no pytest needed)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bank  # noqa: E402


def _state():
    return {"bank": {"nether": [{"asset": "nether-a.zip", "gen": 1}, {"asset": "nether-gone.zip", "gen": 2}],
                     "kit": [{"asset": "kit-gone.zip", "gen": 3}]}}


def test_entries_whose_file_is_gone_are_dropped():
    st = _state()
    assert bank.reconcile(st, {"nether-a.zip", "other.zip"}) == 2
    assert [c["asset"] for c in st["bank"]["nether"]] == ["nether-a.zip"]
    assert st["bank"]["kit"] == []


def test_a_failed_or_empty_listing_drops_nothing():
    for names in (None, set()):
        st = _state()
        assert bank.reconcile(st, names) == 0
        assert len(st["bank"]["nether"]) == 2 and len(st["bank"]["kit"]) == 1


def test_a_stage_left_empty_falls_back_to_its_scenario():
    import random
    st = _state()
    bank.reconcile(st, {"nether-a.zip"})
    assert bank.pick_stage(st, random.Random(1), "nether")["asset"] == "nether-a.zip"
    st["bank"]["nether"] = []
    t = bank.pick_stage(st, random.Random(1), "nether")
    assert t is None or t["kind"] == "scenario"  # never a start that isn't there


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
