"""Recovery cannot score another run's artifacts or increment the generation."""
import copy

import recover


def test_recovery_matches_the_pending_generation_without_mutating_it():
    state = {"gen": 87, "pending": {"gen": 87, "sha": "old", "started": "2026-09-30T14:19:48+00:00", "runs": ["eval-g56-0"]}}
    run = {"status": "completed", "head_sha": "old", "head_branch": "main", "created_at": "2026-09-30T14:19:29Z"}
    before = copy.deepcopy(state)
    assert recover.plan(state, run)["gen"] == 87
    assert state == before
    for change in ({"status": "in_progress"}, {"head_sha": "new"}, {"head_branch": "other"},
                   {"created_at": "2026-09-30T12:00:00Z"}, {"created_at": "2026-09-30T15:00:00Z"}):
        try:
            recover.plan(state, dict(run, **change))
            raise AssertionError("unrelated workflow accepted: " + str(change))
        except ValueError:
            pass
    for bad in ({"gen": 87}, {"gen": 88, "pending": state["pending"]}):
        try:
            recover.plan(bad, run)
            raise AssertionError("unrelated state accepted")
        except ValueError:
            pass


if __name__ == "__main__":
    test_recovery_matches_the_pending_generation_without_mutating_it()
    print("recovery ok")
