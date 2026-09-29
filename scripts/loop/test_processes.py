"""Optional bank and code-merge failures must not strand a generation."""
import os
import subprocess
import tempfile
from types import SimpleNamespace
from unittest.mock import patch

import bank
import common
import loop


def test_bank_timeout_is_a_failed_transfer():
    with patch.object(subprocess, "run", side_effect=subprocess.TimeoutExpired("gh", 180)) as run:
        assert bank.gh("release", "view", "checkpoints").returncode != 0
        assert run.call_args.kwargs["timeout"] == 180


def test_failed_deletions_stay_indexed_for_retry():
    st = {"bank": {"lava": [{"asset": "new.zip", "gen": 2}, {"asset": "old.zip", "gen": 1}]}}
    with patch.object(bank, "gh", return_value=SimpleNamespace(returncode=124)):
        bank.prune(st, keep=1)
        assert len(st["bank"]["lava"]) == 2
    with patch.object(bank, "gh", return_value=SimpleNamespace(returncode=0)):
        bank.prune(st, keep=1)
        assert [c["asset"] for c in st["bank"]["lava"]] == ["new.zip"]


def test_merge_timeout_keeps_the_champion_on_its_branch():
    code = {"sha": "abc", "branch": "loop/fix", "summary": "fix", "genome": "g1"}
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "state.json")
        common.write_json(path, {"merge": code, "genomes": {"g1": {"code": code}}})
        with patch.object(subprocess, "run", side_effect=subprocess.TimeoutExpired("git", 120)) as run:
            loop.cmd_merge(SimpleNamespace(state=d))
            assert all(c.kwargs["timeout"] == 120 for c in run.call_args_list)
        st = common.read_json(path)
        assert st["genomes"]["g1"]["code"] == code
        assert st["genomes"]["g1"]["merge_failed"] and st["merge"] is None


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
