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


def _race(worlds, diffs, **extra):
    """worlds: [h_on, n_on, h_off, n_off] per paired world; diffs: whole-game score differences."""
    import loop
    s = dict(loop.DEFAULT_SETTINGS)
    g = {"id": "g2", "note": "suggested: x 1", "pairs": [[1, "s", d] for d in diffs], "metric": "ok:explore",
         "metric_worlds": [[1, "s"] + w for w in worlds], "status": "contender", "genes": {}, "mutated": []}
    g.update(extra)
    st = {"settings": s, "champion": "g1", "credit": {}, "sigma": {},
          "genomes": {"g1": {"id": "g1", "note": "champ", "pairs": [], "status": "champion", "genes": {}}, "g2": g}}
    out = loop.race(st, {}, ["g2"], "g1", 5)
    return st, out


def test_metric_crowns_a_clear_fix_despite_a_noisy_game_score():
    st, out = _race([[9, 15, 5, 15], [8, 14, 5, 15]] * 4, [3.0, -4.0, 1.0, -2.0] * 2)
    assert st["champion"] == "g2", out


def test_metric_drops_a_change_that_hurts_the_game():
    st, out = _race([[9, 15, 5, 15]] * 8, [-3.0, -4.0, -3.5, -2.8] * 2)
    assert st["genomes"]["g2"]["status"] == "rejected", out


def test_metric_waits_for_enough_worlds():
    st, out = _race([[9, 15, 5, 15], [9, 15, 5, 15]], [1.0, 0.5])
    assert st["champion"] == "g1" and st["genomes"]["g2"]["status"] == "contender", out


def test_one_world_of_many_tries_cannot_crown():
    # Pooled, 40/60 + 7 vs 20/60 + 7 is z ~3.4; world by world it is one good world of eight.
    st, out = _race([[40, 60, 20, 60]] + [[1, 2, 1, 2]] * 7, [0.5] * 8)
    assert st["champion"] == "g1" and st["genomes"]["g2"]["metric_z"] < 2.5, out


def test_defending_champion_cannot_reuse_its_old_metric():
    import loop
    st, _ = _race([[9, 15, 5, 15]] * 8, [3.0, -4.0, 1.0, -2.0] * 2)
    # This champion's evidence was collected against g1, not the next champion.
    st["genomes"]["g3"] = dict(st["genomes"]["g1"], id="g3", status="contender",
                               pairs=[[6, "s", 2.0]] * 8)
    loop.race(st, {}, ["g3"], "g2", 6)
    old = st["genomes"]["g2"]
    assert old["pairs"] == [] and "metric_worlds" not in old and "metric_z" not in old
    loop.race(st, {}, ["g2"], "g3", 7)
    assert st["champion"] == "g3"


def test_state_migration_resets_all_race_evidence():
    import tempfile
    import common
    import loop
    for score_version, evidence_version in [(common.SCORE_VERSION - 1, 2), (common.SCORE_VERSION, 1)]:
        with tempfile.TemporaryDirectory() as d:
            st, _ = _race([[1, 2, 1, 2]] * 4, [1, -1, 1, -1])
            st.update(score_version=score_version, race_evidence_version=evidence_version)
            common.write_json(os.path.join(d, "state.json"), st)
            loaded = loop.load_state(d, {})
            assert loaded["score_version"] == common.SCORE_VERSION
            assert loaded["race_evidence_version"] == 2
            for g in loaded["genomes"].values():
                assert g["pairs"] == [] and "metric_worlds" not in g and "metric_z" not in g and "exposure" not in g


def test_metric_without_new_pairs_never_crowns():
    st, _ = _race([[95, 100, 20, 100]], [])
    assert st["champion"] == "g1"


def test_rare_skill_can_win_at_its_pair_limit():
    import loop
    n = loop.DEFAULT_SETTINGS["max_pairs"]
    # A hunt that finishes once per world: 6 worlds where both sides tried, the rest one side only.
    st, _ = _race([[1, 1, 0, 1]] * 6 + [[1, 1, 0, 0]] * (n - 6), [1, -1] * (n // 2))
    assert st["champion"] == "g2"


def test_rare_skill_final_look_keeps_evidence_and_safety_bars():
    import loop
    s = dict(loop.DEFAULT_SETTINGS, max_pairs=16)
    good = [[1, 2, 1, 1, 0, 1]] * 6 + [[1, 2, 1, 1, 0, 0]] * 10
    g = {"pairs": [[1, "s", 0]] * 16, "metric": "ok:diamond_hunt", "metric_worlds": good}
    assert loop.metric_verdict(s, g, -2) == "drop"  # the game says it hurts
    g["metric_worlds"] = [[1, 2, 1, 1, 0, 1]] * 4 + [[1, 2, 1, 1, 0, 0]] * 12
    assert loop.metric_verdict(s, g, 0) == "drop"  # four worlds can not decide, even at the end
    g.update(metric_worlds=good, max_pairs=40)
    assert loop.metric_verdict(s, g, 0) == "wait"  # each suggestion keeps its own deadline


def test_death_rates_compare_world_by_world():
    assert metrics.world_diffs("deaths", [[0, 40, 2, 40], [1, 40, 1, 40], [3, 40, 0, 40]]) == [0.05, 0.0, -0.075]
    assert metrics.world_diffs("ok:x", [[1, 2, 0, 2], [3, 3, 0, 0]]) == [0.5]  # no tries on one side: skipped


def test_exposure_counts_offers_choices_starts_and_gene_events():
    rows = [{"options": ["attack zombie", "swim_out", "shore"], "choice": "attack zombie"},
            {"options": ["swim_out", "shore"], "choice": "swim_out"},
            {"event": "skill_start", "skill": "swim_out"}, {"event": "skill_end", "skill": "swim_out", "ok": True},
            {"event": "skill_start", "skill": "swim_out"}, {"event": "skill_end", "skill": "swim_out", "code": "INTERRUPTED"},
            {"event": "gene", "detail": "nether.x on: no food"}, {"event": "gene", "detail": "nether.x off: no food"},
            {"event": "gene", "detail": "nether.xy on: other"}]
    ex = metrics.exposure(rows, {"skill": "swim_out"})
    assert (ex["offers"], ex["choices"], ex["starts"], ex["ends"], ex["ok"]) == (2, 1, 2, 1, 1), ex
    assert metrics.acted({"skill": "swim_out"}, ex)
    ex = metrics.exposure(rows, {"gene": "nether.x"})
    assert (ex["on"], ex["off"]) == (1, 1) and metrics.acted({"gene": "nether.x"}, ex)
    assert not metrics.acted({"skill": "dig"}, metrics.exposure(rows, {"skill": "dig"}))
    assert metrics.exposure_spec({"evolved.swim_out": 1, "_metric": "ok:shore"}) == {"skill": "swim_out"}
    assert metrics.exposure_spec({"nether.x": 1, "_exposure": {"gene": "nether.x"}}) == {"gene": "nether.x"}
    assert metrics.exposure_spec({"combat.y": 1}) is None


def test_resolve_counts_one_episode_per_failure_and_who_fixed_it():
    def e(skill, ok, code=None, t=0):
        return {"event": "skill_end", "skill": skill, "ok": ok, "code": code, "t": t}
    m = "resolve:shore:TIMEOUT|STUCK+swim_out"
    # Two timeouts in a row are one episode; swim_out fixes it.
    assert metrics.tally([e("shore", False, "TIMEOUT", 0), e("shore", False, "TIMEOUT", 60000),
                          e("swim_out", True, None, 90000)], m) == (1, 1)
    # Too late, or a death first: not fixed. Another code does not open an episode.
    assert metrics.tally([e("shore", False, "STUCK", 0), e("shore", True, None, 200000)], m) == (0, 1)
    assert metrics.tally([e("shore", False, "STUCK", 0), {"event": "death", "t": 5000},
                          e("shore", True, None, 9000)], m) == (0, 1)
    assert metrics.tally([e("shore", False, "NO_ROOM", 0), e("shore", True, None, 1000)], m) == (0, 0)
    # Without the new skill (the champion's side) shore itself can still fix it.
    assert metrics.tally([e("shore", False, "TIMEOUT", 0), e("shore", True, None, 30000)], m) == (1, 1)


def test_an_idea_that_never_acted_leaves_inconclusive_without_credit():
    st, out = _race([[3, 5, 3, 5]] * 8, [0.4] * 8, exposure_spec={"skill": "swim_out"},
                    exposure={"games": 8, "acted": 0}, mutated=["x"])
    st["credit"]["x"] = {"n": 0, "sum": 0.0}
    g = st["genomes"]["g2"]
    assert g["status"] == "rejected" and "acted in 0 of 8" in g["inconclusive"], out
    assert st["credit"]["x"]["n"] == 0
    assert "inconclusive" in out[0]


def test_thin_exposure_cannot_win_yet():
    st, out = _race([[9, 15, 5, 15]] * 8, [1.0] * 8, exposure_spec={"gene": "x"}, exposure={"games": 8, "acted": 1})
    assert st["champion"] == "g1" and st["genomes"]["g2"]["status"] == "contender", out
    st, out = _race([[9, 15, 5, 15]] * 8, [1.0] * 8, exposure_spec={"gene": "x"}, exposure={"games": 8, "acted": 3})
    assert st["champion"] == "g2", out


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
