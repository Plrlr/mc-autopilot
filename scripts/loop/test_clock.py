#!/usr/bin/env python3
"""Self-test for clock.py and the death times the scorer reads: python scripts/loop/test_clock.py."""
import datetime
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import clock  # noqa: E402
import common  # noqa: E402

# 2026-10-04 13:08:31 UTC, the start of gen176 eval-g159-2.
T0 = 1791119311000


def stamp(ms, tz_hours=0):
    """A test-log time prefix for wall ms in a machine's local time zone."""
    t = datetime.datetime.fromtimestamp(ms / 1000.0, datetime.timezone(datetime.timedelta(hours=tz_hours)))
    return t.strftime("[%H:%M:%S]")


def progress(ms, gs, deaths=0, tz=0):
    return "%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] natural %ds: brain rules, goal x, " \
           "doing y, milestone 7/13, deaths %d" % (stamp(ms, tz), gs, deaths)


def speed_line(ms, gs, wall_s, tz=0):
    return "%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] SPEED %.3f game s per wall s " \
           "(%d game s in %d wall s), mod 0.6 ms per tick" % (stamp(ms, tz), gs / wall_s, gs, wall_s)


def start_line(ms, tz=0):
    return "%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] start at 0, 70, 0" % stamp(ms, tz)


def gen176_like(tz=0):
    """The audit's case: a decision at gs 11, a five-minute fortress fight with no decision, the
    progress lines every 30 game s (deaths 0 at 300), the death, the next decision at gs 307."""
    rows = [{"event": "autopilot_on", "t": T0}, {"gs": 0, "t": T0 + 20, "choice": "fortress find"},
            {"gs": 11, "t": T0 + 14413, "choice": "fortress blazes:8"},
            {"event": "death", "detail": "mob:wither_skeleton", "t": T0 + 361346},
            {"gs": 307, "t": T0 + 366209, "choice": "attack wither_skeleton"},
            {"gs": 600, "t": T0 + 660000, "choice": "explore any"}]
    log = [start_line(T0, tz)]
    # The game here ran at ~0.85x: game second g at wall T0 + g / 0.85 s (300 s at +353 s).
    for g in range(30, 301, 30):
        log.append(progress(T0 + int(g / 0.85 * 1000), g, 0, tz))
    log.append(progress(T0 + 390000, 330, 1, tz))
    log.append(speed_line(T0 + 700000, 620, 700, tz))
    return rows, log


def test_sparse_anchors_place_the_death_after_the_last_progress_line():
    rows, log = gen176_like()
    c = clock.Clock(rows, log)
    gs = c.game_second(T0 + 361346)
    assert 300 <= gs <= 307, gs  # the old lookup said 11
    # Without the test log only the decisions bracket it: still between them, not at 11.
    gs2 = clock.Clock(rows).game_second(T0 + 361346)
    assert 11 < gs2 < 307, gs2


def test_any_time_zone_and_the_date_are_worked_out():
    for tz in (-4, 0, 5.5, 9):
        rows, log = gen176_like(tz)
        gs = clock.Clock(rows, log).game_second(T0 + 361346)
        assert 300 <= gs <= 307, (tz, gs)


def test_variable_speed_between_anchors_is_followed():
    # 0-100 game s in 100 wall s, then 100-150 in 100 wall s (half speed): wall +150 s is gs 125.
    rows = [{"event": "autopilot_on", "t": T0}, {"gs": 0, "t": T0}, {"gs": 100, "t": T0 + 100000},
            {"gs": 150, "t": T0 + 200000}]
    c = clock.Clock(rows)
    assert abs(c.game_second(T0 + 150000) - 125) < 0.01
    assert abs(c.game_second(T0 + 50000) - 50) < 0.01


def test_terminal_death_is_bounded_by_the_end_of_the_run():
    rows, log = gen176_like()
    rows = rows[:4]  # no decision after the death
    log = [l for l in log if "330s" not in l]
    c = clock.Clock(rows, log)
    gs = c.game_second(T0 + 361346)
    assert 300 <= gs <= 620, gs
    # After the last anchor with no end line: the recent speed (~0.85x), not wall seconds as game seconds.
    rows2 = [{"event": "autopilot_on", "t": T0}, {"gs": 0, "t": T0}, {"gs": 85, "t": T0 + 100000},
             {"gs": 170, "t": T0 + 200000}]
    gs2 = clock.Clock(rows2).game_second(T0 + 300000)
    assert abs(gs2 - 255) < 1, gs2
    # The SPEED line's game seconds are the most a run can have lasted.
    end = [speed_line(T0 + 210000, 178, 210)]
    gs3 = clock.Clock(rows2, end + [start_line(T0)]).game_second(T0 + 400000)
    assert gs3 == 178, gs3


def test_missing_clock_is_unknown_not_zero():
    assert clock.Clock([{"event": "death", "t": T0}]).game_second(T0) is None
    assert clock.Clock([]).game_second(T0) is None


def test_sessions_restart_the_clock():
    # Two sessions in one day's log (the laptop): the second one's death is read on its own clock.
    rows = [{"event": "autopilot_on", "t": T0}, {"gs": 0, "t": T0}, {"gs": 500, "t": T0 + 500000},
            {"event": "autopilot_off", "t": T0 + 510000},
            {"event": "autopilot_on", "t": T0 + 900000}, {"gs": 0, "t": T0 + 900000},
            {"event": "death", "t": T0 + 940000}, {"gs": 60, "t": T0 + 960000}]
    c = clock.Clock(rows)
    assert abs(c.game_second(T0 + 940000) - 40) < 0.5
    assert abs(c.game_second(T0 + 250000) - 250) < 0.5


def test_midnight_rollover_in_the_test_log():
    t0 = 1791158390000  # 2026-10-04 23:59:50 UTC
    rows = [{"event": "autopilot_on", "t": t0}, {"gs": 0, "t": t0}, {"gs": 400, "t": t0 + 400000}]
    log = [start_line(t0)] + [progress(t0 + g * 1000, g) for g in range(30, 391, 30)]
    c = clock.Clock(rows, log)
    assert abs(c.game_second(t0 + 200000) - 200) < 1.5


def test_multiple_lives_score_only_the_first():
    rows, log = gen176_like()
    rows.insert(5, {"event": "death", "detail": "lava", "t": T0 + 640000})
    with tempfile.TemporaryDirectory() as d:
        logs = os.path.join(d, "build", "run", "clientGameTest", "mc-autopilot", "logs")
        os.makedirs(logs)
        with open(os.path.join(logs, "run-2026-10-04.jsonl"), "w") as f:
            f.write("\n".join(json.dumps(r) for r in rows) + "\n")
        final = "[13:20:11] [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] FINAL nether: brain rules, " \
                "goal x, doing y, milestone 7/13, deaths 2, dimension overworld, milestone times m7@5s, checkpoints none"
        with open(os.path.join(d, "autopilot-test.log"), "w") as f:
            f.write("\n".join(log + [final]) + "\n")
        r = common.summarizer().read_run(d)
    assert len(r["death_times"]) == 2
    first, second = r["death_times"]
    assert 300 <= first <= 307 and 570 <= second <= 600, r["death_times"]
    # The first death sets the score: the life lived ~300 of 2400 s, not 11.
    s = common.score_run(r, 2400, skip_before=10)
    assert s > -1 + 2.0 * (290 - 1) / 2390 - 0.01, s


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
