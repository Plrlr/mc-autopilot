#!/usr/bin/env python3
"""Self-test for skillstats.py: python scripts/loop/test_skillstats.py (no pytest needed)."""
import gzip
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skillstats  # noqa: E402

FEATS = ["hp", "night", "underground", "nether", "end"]


def x(night=0, under=0, nether=0):
    return [1.0, night, under, nether, 0]


def write(rows):
    fd, path = tempfile.mkstemp(suffix=".jsonl.gz")
    os.close(fd)
    with gzip.open(path, "wt", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")
    return path


def test_counts_and_contexts():
    rows = [
        {"k": "d", "gs": 0, "x": x(), "a": "build_portal"},
        {"k": "s", "gs": 30, "a": "build_portal", "ok": False, "c": "PLACE_FAILED", "sec": 30},
        {"k": "d", "gs": 40, "x": x(night=1), "a": "build_portal"},
        {"k": "s", "gs": 100, "a": "build_portal", "ok": True, "sec": 60},
        {"k": "d", "gs": 110, "x": x(night=1, under=1), "a": "build_portal"},
        {"k": "s", "gs": 130, "a": "build_portal", "ok": False, "c": "DIED", "sec": 20},
        {"k": "s", "gs": 135, "a": "build_portal", "ok": False, "c": "INTERRUPTED", "sec": 5},
        {"k": "d", "gs": 140, "x": x(nether=1), "a": "fortress:find"},
        {"k": "s", "gs": 150, "a": "fortress:find", "ok": True, "sec": 10},
    ]
    path = write(rows)
    try:
        s = skillstats.train_skill_stats([path], FEATS)
    finally:
        os.remove(path)
    bp = s["build_portal"]
    assert (bp["n"], bp["ok"], bp["died"]) == (3, 1, 1), bp
    assert bp["sec"] == 60 and bp["codes"] == {"PLACE_FAILED": 1, "DIED": 1}, bp
    assert s["build_portal@overworld"]["n"] == 3
    # 2 night tries: under MIN_TRIES, so left out; the overall entry still has them.
    assert "build_portal@night" not in s
    assert "fortress:find@nether" not in s  # 1 try
    assert s["fortress:find"]["ok"] == 1


def test_state_is_taken_at_the_skill_start():
    # The skill ran from gs 10 to 70; the decision at 50 (underground) came after it started.
    rows = [{"k": "d", "gs": 5, "x": x()}, {"k": "d", "gs": 50, "x": x(under=1)}]
    rows += [{"k": "s", "gs": 70, "a": "collect:log", "ok": True, "sec": 60}] * 3
    path = write(rows)
    try:
        s = skillstats.train_skill_stats([path], FEATS)
    finally:
        os.remove(path)
    assert "collect:log@under" not in s and s["collect:log@overworld"]["n"] == 3


if __name__ == "__main__":
    test_counts_and_contexts()
    test_state_is_taken_at_the_skill_start()
    print("skillstats ok")
