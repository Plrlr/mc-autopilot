"""Hourly recovery uses persisted generation activity, never skipped workflow runs."""
import datetime
import json
import os
import sys

import common


def recent(state_dir, now=None, grace_seconds=5400):
    now = now or datetime.datetime.now(datetime.timezone.utc)
    state = common.read_json(os.path.join(state_dir, "state.json"), {})
    times = [(state.get("pending") or {}).get("started")]
    history = os.path.join(state_dir, "history.jsonl")
    if os.path.exists(history):
        with open(history, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    times.append(json.loads(line).get("time"))
    for value in times:
        if value:
            when = datetime.datetime.fromisoformat(value)
            if 0 <= (now - when).total_seconds() < grace_seconds:
                return True
    return False


if __name__ == "__main__":
    sys.exit(0 if recent(sys.argv[1]) else 1)
