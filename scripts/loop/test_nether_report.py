#!/usr/bin/env python3
"""Self-test for nether_report.py and the Nether drill: python scripts/loop/test_nether_report.py."""
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import drill  # noqa: E402
import metrics  # noqa: E402
import nether_report  # noqa: E402

T0 = 1791119311000
F = common.load_features()


def x(rods=0, fort=0):
    v = [0.0] * len(F)
    v[F.index("rods")] = rods / 6.0
    v[F.index("fortress_known")] = fort
    return v


def stamp(ms):
    import datetime
    return datetime.datetime.fromtimestamp(ms / 1000.0, datetime.timezone.utc).strftime("[%H:%M:%S]")


def game(root, name, rows, log, final_dim="the_nether", deaths=0, scenario="natural"):
    d = os.path.join(root, name)
    logs = os.path.join(d, "build", "run", "clientGameTest", "mc-autopilot", "logs")
    os.makedirs(logs)
    with open(os.path.join(logs, "run-2026-10-04.jsonl"), "w") as f:
        f.write("\n".join(json.dumps(r) for r in rows) + "\n")
    lines = ["%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] start at 0, 70, 0" % stamp(T0)]
    for ms, gs, dn, inv in log:
        lines.append("%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] nether %ds: brain rules, goal "
                     "blaze_rods, doing fortress blazes:8, milestone 7/13, deaths %d" % (stamp(ms), gs, dn))
        lines.append("%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test]    at 1 70 1 hp 20 food 20 inv {%s}"
                     % (stamp(ms), ", ".join("%s=%d" % kv for kv in inv.items())))
    lines.append("%s [Render thread/INFO] (Minecraft) [STDOUT]: [autopilot-test] FINAL %s: brain rules, goal x, "
                 "doing y, milestone 7/13, deaths %d, dimension %s, milestone times none, checkpoints none"
                 % (stamp(T0 + 700000), scenario, deaths, final_dim))
    with open(os.path.join(d, "autopilot-test.log"), "w") as f:
        f.write("\n".join(lines) + "\n")
    return d


def test_first_life_rods_fight_and_tunneling():
    with tempfile.TemporaryDirectory() as root:
        rows = [{"event": "autopilot_on", "t": T0}, {"gs": 0, "t": T0, "x": x(0, 1), "choice": "fortress blazes:8"},
                {"event": "skill_end", "skill": "fortress blazes:8", "ok": False, "seconds": 120.0, "t": T0 + 120000},
                {"gs": 120, "t": T0 + 120000, "x": x(2, 1), "choice": "fortress blazes:8"},
                {"event": "death", "detail": "mob:wither_skeleton", "t": T0 + 200000},
                {"gs": 210, "t": T0 + 210000, "x": x(5, 1), "choice": "explore any"}]
        log = [(T0 + 30000, 30, 0, {"nether_bricks": 7}), (T0 + 150000, 150, 0, {"blaze_rod": 3}),
               (T0 + 240000, 240, 1, {"blaze_rod": 6})]
        game(root, "trial-natural-drill-0-on-0", rows, log, deaths=1)
        o = nether_report.outcome(os.path.join(root, "trial-natural-drill-0-on-0"))
        assert o["rods_first_life"] == 3 and o["fortress_known"] and o["fortress_fight_s"] == 120, o
        assert o["bricks_dug"] == 7 and o["first_death"] == "mob:wither_skeleton" and not o["safe_return"], o
        assert 190 <= o["first_death_s"] <= 210 and not o["staged"], o
        assert metrics.tally(nether_report._rows(os.path.join(root, "trial-natural-drill-0-on-0")), "rods") == (2, 1)


def test_safe_return_needs_life_overworld_and_a_rod():
    with tempfile.TemporaryDirectory() as root:
        rows = [{"event": "autopilot_on", "t": T0}, {"gs": 0, "t": T0, "x": x(0, 1), "choice": "fortress blazes:8"}]
        game(root, "trial-drill-0-off-0", rows, [(T0 + 30000, 30, 0, {"blaze_rod": 2})],
             final_dim="overworld", scenario="nether_fortress")
        o = nether_report.outcome(os.path.join(root, "trial-drill-0-off-0"))
        assert o["safe_return"] and o["staged"] and o["rods_first_life"] == 2, o
        assert "staged" in nether_report.table(root)


def test_nether_ideas_drill_on_saved_nether_starts():
    assert drill.kind({"nether.piglin_threat": 1, "_stages": ["nether"], "_metric": "deaths"}) == "nether"
    st = {"bank": {"nether": [{"asset": "nether-gen%d.zip" % g, "gen": g} for g in range(160, 176)]},
          "champion": "g1", "genomes": {"g1": {"genes": {}}}}
    starts = drill.lava_starts(st, 3, "nether", "nether")
    assert len(starts) == 3 and all(int(s[10:13]) >= 166 for s in starts), starts  # the newest saves



def test_a_rod_counts_from_the_milestone_too():
    """A rod picked up just before dying never shows in a decision row: milestone 8 still counts it."""
    rows = [{"x": x(0, 1)}, {"event": "milestone", "detail": "8 at 300 s"}, {"event": "death"}]
    assert metrics.tally(rows, "rods") == (1, 1)
    assert metrics.tally([{"event": "death"}, {"event": "milestone", "detail": "8 at 400 s"}], "rods") == (0, 1)

if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
