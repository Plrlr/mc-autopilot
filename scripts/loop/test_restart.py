"""Regression: hourly no-ops must not suppress recovery of a broken chain."""
import datetime
import json
import os
import tempfile

import common
import restart


def test_recovery_uses_only_generation_activity():
    now = datetime.datetime.fromisoformat("2026-09-29T20:00:00+00:00")
    with tempfile.TemporaryDirectory() as d:
        assert not restart.recent(d, now)
        common.write_json(os.path.join(d, "state.json"), {"pending": {"started": "2026-09-29T19:00:00+00:00"}})
        assert restart.recent(d, now)
        # Repeated schedule no-ops don't write state or history, so the grace period expires.
        assert not restart.recent(d, now + datetime.timedelta(hours=1))
        common.write_json(os.path.join(d, "state.json"), {"pending": None})
        with open(os.path.join(d, "history.jsonl"), "w", encoding="utf-8") as f:
            f.write(json.dumps({"time": "2026-09-29T19:30:00+00:00"}) + "\n")
        assert restart.recent(d, now)
        assert not restart.recent(d, now + datetime.timedelta(hours=2))


if __name__ == "__main__":
    test_recovery_uses_only_generation_activity()
    print("restart ok")
