#!/usr/bin/env python3
"""Self-test for the across-lives score (score v5, 2026-11-09): python scripts/loop/test_score_lives.py."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

T = 1200  # a 20-minute game


def run(milestones=(), checkpoints=None, death_times=(), deaths=None):
    n = len(death_times) if deaths is None else deaths
    return {"milestones": [list(m) for m in milestones], "checkpoints": dict(checkpoints or {}),
            "deaths": [("mob:zombie", "x")] * n, "death_times": list(death_times)}


def test_progress_after_a_death_scores():
    before = common.score_run(run([(1, 100)], death_times=[300]), T)
    after = common.score_run(run([(1, 100), (4, 900)], death_times=[300]), T)
    # Iron (2 points) at 900 s of 1200: 2 x (1 + 0.5 x 0.25) after the respawn.
    assert abs(after - before - 2 * 1.125) < 0.002, (before, after)


def test_two_deaths_cost_more_than_one_and_cap_at_five():
    one = common.score_run(run([(1, 100)], death_times=[300]), T)
    two = common.score_run(run([(1, 100)], death_times=[300, 600]), T)
    assert abs(one - two - common.DEATH_PENALTY) < 0.002, (one, two)
    five = common.score_run(run([(1, 100)], death_times=[300, 400, 500, 600, 700]), T)
    nine = common.score_run(run([(1, 100)], death_times=[300, 400, 500, 600, 700, 800, 900, 1000, 1100]), T)
    assert five == nine and abs(one - five - 4 * common.DEATH_PENALTY) < 0.002, (five, nine)


def test_same_progress_with_fewer_deaths_wins():
    ms = [(1, 100), (2, 200), (4, 700)]
    assert common.score_run(run(ms, death_times=[500]), T) > common.score_run(run(ms, death_times=[500, 800]), T)
    assert common.score_run(run(ms), T) > common.score_run(run(ms, death_times=[1100]), T)


def test_staged_start_ignores_starting_gear_in_any_life():
    r = run([(4, 3), (7, 2000)], {"two_buckets": 5, "portal_lit": 1500}, death_times=[1000])
    s = common.score_run(r, 2400, skip_before=10)
    # m7 (3) at 2000 s and portal_lit (3) at 1500 s, both after the death; m4 and the buckets were carried in.
    want = 3 * (1 + 0.5 * (1 - 2000 / 2400)) + 3 * (1 + 0.5 * (1 - 1500 / 2400)) + 2.0 * 990 / 2390 - 1.0
    assert abs(s - round(want, 3)) < 0.002, (s, want)


def test_no_death_run_scores_what_v4_gave():
    r = run([(1, 60), (2, 300), (4, 600)], {"two_buckets": 900})
    # By hand, v4: 1 x 1.475 + 1 x 1.375 + 2 x 1.25 + 0.5 x 1.125 + 2 (lived the whole run) = 7.9125.
    assert abs(common.score_run(r, T) - 7.9125) <= 0.0006, common.score_run(r, T)


def test_death_without_a_time_ends_the_first_life_at_the_start():
    r = run([(1, 100)], deaths=1)
    assert common.score_run(r, T) == round(1 * (1 + 0.5 * (1 - 100 / T)) + 0 - 1.0, 3)


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
