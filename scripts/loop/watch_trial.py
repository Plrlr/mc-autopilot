"""Bound a cloud trial and its shutdown; a stuck game must not hold the generation open."""
import argparse
import os
import re
import signal
import subprocess
import time


REPORT = re.compile(r"\[autopilot-test\] (?:start at|STAGE start|FIGHT \d+|\S+ \d+s:|SPEED|FINAL)")


def stop(process, grace=10):
    """The launcher gets its own session. Escalate if TERM is ignored, and bound both waits."""
    def send(sig):
        try:
            if os.name == "posix":
                os.killpg(process.pid, sig)
            elif sig == signal.SIGTERM:
                process.terminate()
            else:
                process.kill()
        except ProcessLookupError:
            pass

    send(signal.SIGTERM)
    try:
        process.wait(timeout=grace)
    except subprocess.TimeoutExpired:
        send(signal.SIGKILL if os.name == "posix" else signal.SIGTERM)
        process.kill()
        process.wait(timeout=grace)
    if os.name == "posix":
        # The launcher may exit on TERM while one of its children ignores it.
        send(signal.SIGKILL)


def watch(command, log, minutes, poll=15, startup=900, stalled=300, closed=120, grace=10):
    start = time.monotonic()
    last_report, final_at, seen = start, None, False
    limit = min(minutes * 4 * 60 + 900, 140 * 60)
    with open(log, "w", encoding="utf-8") as output, open(log, encoding="utf-8", errors="replace") as reader:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            while process.poll() is None:
                now = time.monotonic()
                for line in reader.readlines():
                    if REPORT.search(line):
                        seen, last_report = True, now
                    if "[autopilot-test] FINAL" in line and final_at is None:
                        final_at = now
                why = None
                if now - start > limit:
                    why = "trial exceeded its wall-time limit"
                elif final_at is not None and now - final_at > closed:
                    why = "game did not close after its final report"
                elif not seen and now - start > startup:
                    why = "no test report during startup"
                elif seen and final_at is None and now - last_report > stalled:
                    why = "no game progress report for five minutes"
                if why:
                    print("Trial watchdog: " + why, flush=True)
                    stop(process, grace)
                    break
                time.sleep(poll)
            # A game's ordinary nonzero shutdown is judged by FINAL in the action's last step.
            return process.wait(timeout=grace)
        finally:
            if process.poll() is None:
                stop(process, grace)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--minutes", type=int, required=True)
    ap.add_argument("--log", required=True)
    ap.add_argument("command", nargs=argparse.REMAINDER)
    a = ap.parse_args()
    command = a.command[1:] if a.command[:1] == ["--"] else a.command
    if not command or a.minutes <= 0:
        ap.error("a positive duration and trial command are required")
    watch(command, a.log, a.minutes)


if __name__ == "__main__":
    main()
