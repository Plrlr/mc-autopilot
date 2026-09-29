#!/usr/bin/env python3
"""Self-test for metrics.py: python scripts/loop/test_metrics.py (no pytest needed)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import metrics  # noqa: E402


def end(skill, ok, code=None):
    return {"event": "skill_end", "skill": skill, "ok": ok, "code": code}


def test_success_rate_counts_matching_skills_but_not_interruptions():
    rows = [end("explore any", True), end("explore:cow", False, "UNREACHABLE"), end("explore any", False, "INTERRUPTED"),
            end("explorer", True), end("build_portal", True), end("cast_portal", False, "NO_ROOM")]
    assert metrics.tally(rows, "ok:explore") == (1, 2)
    assert metrics.tally(rows, "ok:build_portal|cast_portal") == (1, 2)


def test_deaths_per_minute():
    rows = [{"gs": 10}, {"event": "death", "gs": 100}, {"event": "death"}, {"gs": 600}]
    assert metrics.tally(rows, "deaths") == (2, 10.0)


def test_z_signs():
    assert metrics.z("ok:explore", (60, 100), (40, 100)) > 2.5
    assert metrics.z("ok:explore", (40, 100), (60, 100)) < -2.5
    assert metrics.z("ok:explore", (0, 0), (5, 10)) == 0.0
    # Fewer deaths in the same game time is better.
    assert metrics.z("deaths", (10, 300), (30, 300)) > 2.5
    assert metrics.z("deaths", (30, 300), (10, 300)) < -2.5


def _race(on, off, diffs):
    import loop
    s = dict(loop.DEFAULT_SETTINGS)
    g = {"id": "g2", "note": "suggested: x 1", "pairs": [[1, "s", d] for d in diffs], "metric": "ok:explore",
         "metric_tally": {"on": on, "off": off}, "status": "contender", "genes": {}, "mutated": []}
    st = {"settings": s, "champion": "g1", "credit": {}, "sigma": {},
          "genomes": {"g1": {"id": "g1", "note": "champ", "pairs": [], "status": "champion", "genes": {}}, "g2": g}}
    out = loop.race(st, {}, ["g2"], "g1", 5)
    return st, out


def test_metric_crowns_a_clear_fix_despite_a_noisy_game_score():
    st, out = _race([90, 150], [50, 150], [3.0, -4.0, 1.0, -2.0])
    assert st["champion"] == "g2", out


def test_metric_drops_a_change_that_hurts_the_game():
    st, out = _race([90, 150], [50, 150], [-3.0, -4.0, -3.5, -2.8])
    assert st["genomes"]["g2"]["status"] == "rejected", out


def test_metric_waits_for_enough_tries():
    st, out = _race([9, 15], [5, 15], [1.0, 0.5])
    assert st["champion"] == "g1" and st["genomes"]["g2"]["status"] == "contender", out


def test_defending_champion_cannot_reuse_its_old_metric():
    import loop
    st, _ = _race([90, 150], [50, 150], [3.0, -4.0, 1.0, -2.0])
    # This champion's evidence was collected against g1, not the next champion.
    st["genomes"]["g3"] = dict(st["genomes"]["g1"], id="g3", status="contender",
                               pairs=[[6, "s", 2.0]] * 8)
    loop.race(st, {}, ["g3"], "g2", 6)
    old = st["genomes"]["g2"]
    assert old["pairs"] == [] and "metric_tally" not in old and "metric_z" not in old
    loop.race(st, {}, ["g2"], "g3", 7)
    assert st["champion"] == "g3"


def test_state_migration_resets_all_race_evidence():
    import tempfile
    import common
    import loop
    for score_version, evidence_version in [(common.SCORE_VERSION - 1, 1), (common.SCORE_VERSION, 0)]:
        with tempfile.TemporaryDirectory() as d:
            st, _ = _race([90, 150], [50, 150], [1, -1, 1, -1])
            st.update(score_version=score_version, race_evidence_version=evidence_version)
            common.write_json(os.path.join(d, "state.json"), st)
            loaded = loop.load_state(d, {})
            assert loaded["score_version"] == common.SCORE_VERSION
            assert loaded["race_evidence_version"] == 1
            for g in loaded["genomes"].values():
                assert g["pairs"] == [] and "metric_tally" not in g and "metric_z" not in g


def test_metric_without_new_pairs_never_crowns():
    st, _ = _race([95, 100], [20, 100], [])
    assert st["champion"] == "g1"


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
