"""One game clock for every logged event: wall time (ms) -> game second.

Deaths, milestones, checkpoints and skill ends are logged with wall time only; the game second
(client ticks / 20 since the autopilot turned on) is known only at clock anchors. Two kinds exist
without touching the frozen log code:
  - decision rows in run-*.jsonl: {"t": wall ms, "gs": game second}, exact to a second;
  - the test's progress lines in autopilot-test.log: "[HH:MM:SS] ... <scenario> 300s: ..." printed
    when the game clock first passes 300 s (checked once a wall second), plus the SPEED line at the
    end ("... (2400 game s in 2451 wall s)").
Decisions are sparse while one long skill runs: gen176 eval-g159-2 decided at gs 11, then fought
at the fortress for five minutes and died; the old step lookup ("the last anchor before") put the
death at 11 s, though the progress line said 0 deaths at 300 s and the next decision was at 307.

So: merge both kinds of anchors and interpolate between the two around an event, which follows
the game's own speed in that stretch (the cloud plays at ~0.6-1.0x and it varies). Past the last
anchor, extrapolate at the recent speed, never past the run's end. Anchors of one session only:
gs restarts at 0 when the autopilot is turned on again (laptop logs hold several a day).
"""
import bisect
import re
import statistics

# "[13:14:26] [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] natural 300s: brain ..."
PROGRESS = re.compile(r"^\[(\d\d):(\d\d):(\d\d)\].*\[autopilot-test\] \S+ (\d+)s: ")
SPEED = re.compile(r"^\[(\d\d):(\d\d):(\d\d)\].*\[autopilot-test\] SPEED [\d.]+ game s per wall s \((\d+) game s in")
START = re.compile(r"^\[(\d\d):(\d\d):(\d\d)\].*\[autopilot-test\] start at ")
DAY = 86400


def log_marks(lines):
    """(seconds of the day, game second or None, kind) from a test log, in order. Kinds: "progress"
    (game second >= n), "end" (the SPEED line's game seconds), "start" (just before the autopilot
    turns on). Log times are the machine's local time to the second; their date and time zone are
    worked out against the decision rows (calibrate)."""
    out = []
    for line in lines:
        for kind, rx in (("progress", PROGRESS), ("end", SPEED), ("start", START)):
            m = rx.match(line)
            if m:
                h, mi, s = int(m.group(1)), int(m.group(2)), int(m.group(3))
                gs = int(m.group(4)) if kind != "start" else None
                out.append((h * 3600 + mi * 60 + s, gs, kind))
                break
    return out


def interp(anchors, t):
    """Game second at wall ms t from sorted (wall ms, gs) anchors of one session; None without any.
    Between two anchors: linear (the local game speed). Before the first or after the last: the
    speed of the nearest stretch (1.0 if unknown), clamped so time never runs backwards."""
    if not anchors:
        return None
    walls = [w for w, _ in anchors]
    i = bisect.bisect_right(walls, t)
    if 0 < i < len(anchors):
        (w0, g0), (w1, g1) = anchors[i - 1], anchors[i]
        return g0 + (g1 - g0) * (t - w0) / max(1.0, w1 - w0)
    speed = recent_speed(anchors, last=(i == len(anchors)))
    if i == 0:
        w1, g1 = anchors[0]
        return max(0.0, g1 - (w1 - t) / 1000.0 * speed)
    w0, g0 = anchors[-1]
    return g0 + (t - w0) / 1000.0 * speed


def recent_speed(anchors, last=True, span_ms=120000):
    """Game seconds per wall second over the last (or first) ~2 wall minutes of anchors."""
    if len(anchors) < 2:
        return 1.0
    seq = anchors if last else anchors[::-1]
    w_end, g_end = seq[-1]
    for w, g in reversed(seq[:-1]):
        if abs(w_end - w) >= span_ms:
            break
    dw = abs(w_end - w) / 1000.0
    if dw <= 0:
        return 1.0
    return max(0.0, min(2.0, abs(g_end - g) / dw))


def sessions(rows):
    """Split log rows into sessions at each autopilot_on: gs restarts there."""
    out, cur = [], []
    for o in rows:
        if o.get("event") == "autopilot_on" and cur:
            out.append(cur)
            cur = []
        cur.append(o)
    if cur:
        out.append(cur)
    return out


def calibrate(marks, decisions, start_wall=None):
    """Epoch ms of each log mark, or [] when they can't be placed. The local midnight (date and
    time zone) is the median of what each mark implies, rounded to 15 minutes (every real time
    zone is a multiple of that), so a few badly placed estimates can't move it."""
    if not marks:
        return []
    guesses = []
    for sod, gs, kind in marks:
        if kind == "start" and start_wall is not None:
            guesses.append(start_wall / 1000.0 - sod)
        elif kind == "progress" and len(decisions) >= 2 and decisions[0][1] <= gs <= decisions[-1][1]:
            # The wall time of that game second by the decisions alone: rough, but well within 7.5 min.
            gw = [(g, w) for w, g in decisions]
            j = bisect.bisect_left([g for g, _ in gw], gs)
            j = min(max(j, 1), len(gw) - 1)
            (g0, w0), (g1, w1) = gw[j - 1], gw[j]
            w = w0 + (w1 - w0) * (gs - g0) / max(1, g1 - g0)
            guesses.append(w / 1000.0 - sod)
    if not guesses:
        return []
    base = round(statistics.median(guesses) / 900.0) * 900.0
    out, day, prev = [], 0, None
    for sod, gs, kind in marks:
        if prev is not None and sod < prev - DAY / 2:
            day += 1  # past midnight
        prev = sod
        out.append(((base + day * DAY + sod + 0.5) * 1000.0, gs, kind))  # +0.5: the log truncates to the second
    return out


class Clock:
    """Wall ms -> game second for one run: its jsonl rows (in file order) and its test log lines."""

    def __init__(self, rows, log_lines=()):
        self.parts = []  # one per session: first and last wall ms, anchors, the game second it ended at
        marks = log_marks(log_lines)
        for part in sessions(rows):
            walls = [float(o["t"]) for o in part if "t" in o]
            if not walls:
                continue
            decisions = sorted((float(o["t"]), float(o["gs"])) for o in part if "t" in o and "gs" in o)
            on = next((float(o["t"]) for o in part if o.get("event") == "autopilot_on" and "t" in o), None)
            self.parts.append({"first": min(walls), "last": max(walls), "decisions": decisions, "on": on, "end": None})
        # The test log belongs to the session it overlaps (a test has one; laptop logs have none).
        for p in self.parts:
            placed = calibrate(marks, p["decisions"], p["on"]) if marks else []
            if placed and p["first"] - 600000 <= placed[0][0] <= p["last"] + 600000:
                p["marks"] = placed
                marks = []
            else:
                p["marks"] = []
        for p in self.parts:
            anchors = list(p["decisions"])
            for w, gs, kind in p["marks"]:
                if gs is not None:
                    anchors.append((w, float(gs)))
                    if kind == "end":
                        p["end"] = float(gs)
            anchors.sort()
            # One clock runs forward: drop an anchor that would make it run backwards (a progress
            # line's game second is a lower bound by up to a second, the log truncates wall time).
            mono = []
            for w, g in anchors:
                if mono and g < mono[-1][1]:
                    continue
                mono.append((w, g))
            p["anchors"] = mono

    def session(self, t):
        """The session holding wall ms t: the latest one that started at or before it."""
        best = None
        for p in self.parts:
            if p["first"] <= t or best is None:
                best = p
        return best

    def game_second(self, t):
        """Game second at wall ms t, or None when the run has no clock at all."""
        if t is None:
            return None
        p = self.session(float(t))
        gs = interp(p["anchors"], float(t)) if p else None
        if gs is None:
            return None
        if p["end"] is not None:
            gs = min(gs, p["end"])
        return gs
