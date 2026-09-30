"""Validate an interrupted generation before scoring its existing artifacts; never propose twice."""
import argparse
import datetime
import json
import subprocess

import common


def plan(state, run):
    pending = state.get("pending")
    if not pending or pending["gen"] != state["gen"]:
        raise ValueError("there is no matching pending generation to recover")
    if run["status"] != "completed":
        raise ValueError("the source workflow must finish or be cancelled before recovery")
    if run["head_sha"] != pending["sha"] or run["head_branch"] != "main":
        raise ValueError("source workflow does not match the pending generation's code")
    started = datetime.datetime.fromisoformat(pending["started"])
    created = datetime.datetime.fromisoformat(run["created_at"].replace("Z", "+00:00"))
    if not 0 <= (started - created).total_seconds() <= 600:
        raise ValueError("source workflow does not match the pending generation's start")
    if not pending.get("runs"):
        raise ValueError("pending generation contains no trials")
    return {"gen": pending["gen"], "runs": [{"name": "recover-existing-artifacts"}]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    ap.add_argument("--run", type=int, required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    repo = subprocess.run(["gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"],
                          capture_output=True, text=True, check=True, timeout=60).stdout.strip()
    run = subprocess.run(["gh", "api", "repos/%s/actions/runs/%d" % (repo, a.run)],
                         capture_output=True, text=True, check=True, timeout=60)
    state = common.read_json(a.state + "/state.json")
    common.write_json(a.out, plan(state, json.loads(run.stdout)))
    print("Recovering generation %d from workflow %d; no games will be replayed" % (state["gen"], a.run))


if __name__ == "__main__":
    main()
