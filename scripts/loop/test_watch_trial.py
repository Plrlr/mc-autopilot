"""A hung or TERM-resistant game must release the generation, including after FINAL."""
import os
import pathlib
import signal
import subprocess
import sys
import tempfile
import time

import watch_trial


def trial(source):
    with tempfile.TemporaryDirectory() as d:
        log = str(pathlib.Path(d) / "trial.log")
        started = time.monotonic()
        result = watch_trial.watch([sys.executable, "-u", "-c", source], log, 1,
                                   poll=0.02, startup=2, stalled=0.2, closed=0.2, grace=0.2)
        assert time.monotonic() - started < 5
        return result, pathlib.Path(log).read_text()


def test_stalled_game_is_killed_even_when_it_ignores_term():
    if os.name != "posix":
        return
    result, log = trial('import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); '
                        'print("[autopilot-test] STAGE start combat",flush=True); time.sleep(30)')
    assert result == -signal.SIGKILL and "STAGE" in log


def test_final_report_does_not_allow_shutdown_to_hang():
    result, log = trial('import time; print("[autopilot-test] FINAL done",flush=True); time.sleep(30)')
    assert result != 0 and "FINAL" in log


def test_missing_start_report_is_bounded():
    result, log = trial('import time; time.sleep(30)')
    assert result != 0 and not log


def test_completed_process_keeps_its_log_and_exit_code():
    result, log = trial('print("[autopilot-test] FINAL done")')
    assert result == 0 and "FINAL" in log


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
