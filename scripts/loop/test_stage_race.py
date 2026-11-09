#!/usr/bin/env python3
"""Self-test for the stage race (2026-11-09): guard pairs, the guard verdict, the max_pairs exit,
"only" focus tasks and "_minutes". python scripts/loop/test_stage_race.py (no pytest needed)."""
import argparse
import json
import os
import random
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import loop  # noqa: E402

# g475's 16 paired differences (state.json, 2026-11-09): +0.97, t 0.82, sd ~4.7. Dropped at max_pairs.
G475 = [2.207, 7.94, 0.933, 0.475, -2.214, 0.311, -0.032, 0.017, -8.758, 13.906, 0.001, 0.082, 1.617,
        -1.048, -0.647, 0.751]


def _state(g, **settings):
    s = dict(loop.DEFAULT_SETTINGS)
    s.update(settings)
    champ = {"id": "g1", "note": "champ", "pairs": [], "status": "champion", "genes": {}}
    return {"settings": s, "champion": "g1", "credit": {"x": {"n": 0, "sum": 0.0}}, "sigma": {"x": 0.15},
            "genomes": {"g1": champ, "g2": g}}


def _genome(diffs, guard=None, focus=None, note="suggested: x 1", **extra):
    g = {"id": "g2", "note": note, "pairs": [[1, "s", d] for d in diffs], "status": "contender",
         "genes": {"x": 1}, "mutated": ["x"]}
    if focus:
        g["focus"] = focus
    if guard is not None:
        g["guard_pairs"] = [[1, "s", d] for d in guard]
    g.update(extra)
    return g


def _race(g, **settings):
    st = _state(g, **settings)
    out = loop.race(st, {}, ["g2"], "g1", 5)
    return st, out


STRONG = [2.0, 2.5, 1.5, 3.0, 2.2, 1.8, 2.6, 2.1, 2.4]


def test_focused_spawn_pairs_go_to_guard_and_focus_pairs_judge():
    g = {"focus": ["nether"]}
    assert loop.pair_list(g, {"kind": "natural", "stage": "spawn"}) == "guard_pairs"
    assert loop.pair_list(g, {"kind": "start", "stage": "nether"}) == "pairs"
    assert loop.pair_list(g, {"kind": "combat", "stage": "combat"}) is None
    assert loop.pair_list(g, {"kind": "start", "stage": "lava"}) is None
    # Unfocused genomes: every pair judges them, as before.
    for t in ({"kind": "natural", "stage": "spawn"}, {"kind": "combat", "stage": "combat"}):
        assert loop.pair_list({}, t) == "pairs"


def test_focused_cannot_be_crowned_before_enough_spawn_pairs():
    st, out = _race(_genome(STRONG, guard=[0.1, -0.2, 0.3], focus=["nether"]))
    assert st["champion"] == "g1" and st["genomes"]["g2"]["status"] == "contender", out
    assert loop.guard_verdict(st["settings"], st["genomes"]["g2"]) == "wait"


def test_focused_at_its_pair_limit_waits_for_its_spawn_pairs():
    st, out = _race(_genome(STRONG * 2, guard=[0.1], focus=["nether"]))
    g = st["genomes"]["g2"]
    assert g["status"] == "contender" and not g.get("extended") and out == [], out


def test_focused_crowned_with_strong_focus_pairs_and_neutral_guard():
    st, out = _race(_genome(STRONG, guard=[0.5, -0.8, 0.3, -0.1, 0.2], focus=["nether"]))
    assert st["champion"] == "g2", out


def test_focused_rejected_when_it_breaks_the_early_game():
    st, out = _race(_genome(STRONG, guard=[-1.5, -2.0, -1.8, -2.2, -1.6, -1.7], focus=["nether"]))
    g = st["genomes"]["g2"]
    assert st["champion"] == "g1" and g["status"] == "rejected", out
    assert "breaks the early game" in g["dropped_why"] and "spawn seeds" in out[0] and "nether seeds" in out[0], out
    assert st["credit"]["x"]["sum"] < 0  # the gene's credit follows the spawn harm, not the focus gain


def test_unfocused_genomes_race_as_before():
    st, out = _race(_genome(STRONG))
    assert st["champion"] == "g2", out
    st, out = _race(_genome([-1.0, -2.0, -1.5, -0.5], note="a 1"))
    assert st["genomes"]["g2"]["status"] == "rejected" and "dropped" in out[0], out
    assert loop.guard_verdict(st["settings"], st["genomes"]["g2"]) == "ok"


def test_g475_ends_unproven_not_dropped():
    st, out = _race(_genome(G475, note="brain.situations 1, reflex.burning_hp 10, skill.blaze_farm 1"), max_pairs=16)
    g = st["genomes"]["g2"]
    assert g["status"] == "rejected" and "unproven" in out[0] and "dropped" not in out[0], out
    assert "not told apart from noise" in g["inconclusive"] and "+0.97 over 16 seeds, t 0.8" in g["inconclusive"], g
    assert "dropped_why" not in g


def test_promising_genome_extended_once_only():
    rng = random.Random(3)
    diffs = [0.9 + rng.gauss(0, 2.0) for _ in range(16)]
    diffs = [d - common.mean(diffs) + 0.9 for d in diffs]  # mean 0.9
    sd = common.stdev(diffs)
    # Scale the spread so t is ~1.5: t = mean / (sd / 4).
    diffs = [0.9 + (d - 0.9) * (0.9 * 4 / 1.5) / sd for d in diffs]
    assert abs(common.paired_t(diffs) - 1.5) < 0.01
    st, out = _race(_genome(diffs), max_pairs=16)
    g = st["genomes"]["g2"]
    assert g["status"] == "contender" and g["extended"] and g["max_pairs"] == 24 and "keeps racing" in out[0], out
    g["pairs"] += [[2, "s", d] for d in [1.0, -1.0] * 4]  # still promising at 24, but no second extension
    out = loop.race(st, {}, ["g2"], "g1", 6)
    assert common.paired_t([x[2] for x in g["pairs"]]) >= 1.0, out
    assert g["status"] == "rejected" and "unproven" in out[0] and g["max_pairs"] == 24, out
    # A new race (a new champion or score version) gives the genome back its own limit.
    loop.reset_race(g)
    assert "extended" not in g and "max_pairs" not in g and "guard_pairs" not in g


def test_extension_restores_a_suggestions_own_limit():
    g = _genome([1.0] * 3, max_pairs=40, extended=True, max_pairs_before=40, guard_pairs=[[1, "s", 0]])
    g["max_pairs"] = 48
    loop.reset_race(g)
    assert g["max_pairs"] == 40 and "extended" not in g and "max_pairs_before" not in g and "guard_pairs" not in g


def test_losing_at_pair_limit_is_dropped_with_a_reason():
    st, out = _race(_genome([0.5, -0.9] * 8, note="a 1", drop_t=-5), max_pairs=16)
    g = st["genomes"]["g2"]
    assert g["status"] == "rejected" and "dropped" in out[0] and g["dropped_why"], out


def _propose(state_dir, out_path, minutes=None, suggest=None):
    genes = common.load_genes()
    st = loop.load_state(state_dir, genes)
    champ = st["champion"]
    foc = loop.new_genome("g5", champ, {}, 0, [], "suggested: route.barter 1")
    foc.update(status="contender", focus=["nether"], priority=True)
    if minutes:
        foc["focus_minutes"] = minutes
    other = loop.new_genome("g6", champ, {}, 0, [], "suggested: y 1")
    other["status"] = "contender"
    st["genomes"]["g6"] = other
    if not suggest:
        st["genomes"]["g5"] = foc
    st["bank"] = {"nether": [{"asset": "nether-gen1-x.zip"}]}
    st["next_id"] = 7
    common.write_json(os.path.join(state_dir, "state.json"), st)
    override = {"suggest": suggest or [], "seeds_per_gen": 2, "stage_seeds": 0, "combat_seeds": 0, "data_runs": 0,
                "max_genomes": 3, "focus_stage_seeds": 3, "max_jobs": 100, "max_gens_per_day": 1000}
    a = argparse.Namespace(state=state_dir, sha="abc1234", out=out_path, override=json.dumps(override))
    loop.cmd_propose(a)
    return common.read_json(out_path), common.read_json(os.path.join(state_dir, "state.json"))


def test_only_tasks_are_played_by_the_named_genomes():
    with tempfile.TemporaryDirectory() as d:
        out, st = _propose(d, os.path.join(d, "runs.json"))
    p = st["pending"]
    assert p["lineup"][0] == "g0" and set(p["lineup"]) == {"g0", "g5", "g6"}, p["lineup"]
    focus = [i for i, t in enumerate(p["tasks"]) if t["stage"] == "nether"]
    assert len(focus) == 3 and all(p["tasks"][i]["only"] == ["g0", "g5"] for i in focus)
    names = {r["name"] for r in out["runs"]}
    for i in focus:
        assert "eval-g0-%d" % i in names and "eval-g5-%d" % i in names and "eval-g6-%d" % i not in names
    assert len(out["runs"]) == 3 * 2 + 2 * 3 and p["runs"] == [r["name"] for r in out["runs"]]
    assert all(r["minutes"] == str(st["settings"]["minutes"]) for r in out["runs"])


def test_focus_minutes_apply_to_focus_tasks_and_are_capped():
    with tempfile.TemporaryDirectory() as d:
        out, st = _propose(d, os.path.join(d, "runs.json"), minutes=90)
    by = {r["name"]: r for r in out["runs"]}
    for i, t in enumerate(st["pending"]["tasks"]):
        want = "60" if t["stage"] == "nether" else str(st["settings"]["minutes"])
        assert by["eval-g0-%d" % i]["minutes"] == want, (i, t)
    # A queued idea's "_minutes" is capped when it enters the race.
    with tempfile.TemporaryDirectory() as d:
        sug = [{"route.barter": 1, "_stages": ["nether"], "_minutes": 90, "_priority": True}]
        out, st = _propose(d, os.path.join(d, "runs.json"), suggest=sug)
    g = st["genomes"]["g7"]
    assert g["focus"] == ["nether"] and g["focus_minutes"] == 60, g
    assert all(r["minutes"] == "60" for r in out["runs"] if r["name"].startswith("eval-g7-")
               and st["pending"]["tasks"][int(r["name"].split("-")[2])]["stage"] == "nether")


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
