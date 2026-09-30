#!/usr/bin/env python3
"""Self-test for the fair-play gate: python scripts/loop/test_fairplay.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fairplay  # noqa: E402

GOOD = '''package io.github.plrlr.autopilot.skills.evolved;

import io.github.plrlr.autopilot.plan.Option;
import io.github.plrlr.autopilot.skills.Fail;
import io.github.plrlr.autopilot.state.WorldMemory;
import net.minecraft.core.BlockPos;

import java.util.Set;

/** Collect refused in water ("dug from dry ground"): swim to seen land first. */
public class LeaveWater extends EvolvedSkill {
	private BlockPos land;

	public static Option offer(Context c) {
		// "level" and Mc in a comment are fine: comments aren't code.
		if (!Player.inWater() || !"WRONG_PLACE".equals(c.lastCode())) return null;
		return new Option("leave_water", null, "in water: " + c.lastSkill() + " needs dry ground");
	}

	@Override
	public String name() { return "leave_water"; }

	@Override
	protected void start() {
		WorldMemory m = memory;
		land = m.nearestLand(Set.of());
		if (land == null) { fail(Fail.NOT_FOUND, "no dry ground seen"); return; }
		Player.walkNear(land, 1);
	}

	@Override
	protected void tick() {
		if (!Player.inWater() && Player.onGround()) { done("on dry ground at " + Player.feet()); return; }
		Player.press(Player.Key.JUMP, Player.eyesInWater());
		if (!Player.walking()) Player.walkNear(land, 1);
	}
}
'''


def bad(src, skill="leave_water"):
    return fairplay.check_source(src, "LeaveWater.java", skill)


def test_a_fair_skill_passes():
    assert bad(GOOD) == [], bad(GOOD)


def test_cheats_are_caught():
    cases = {
        "raw game access": GOOD.replace("Player.inWater() && Player.onGround()", "Mc.player().isInWater()"),
        "a unicode-escaped name": GOOD.replace("Player.inWater() && Player.onGround()", "\\u004Dc.player().isInWater()"),
        "an escape in a comment": GOOD.replace("// \"level\" and Mc", "// \\u000A Mc.player();"),
        "a non-ASCII name": GOOD.replace("private BlockPos land;", "private BlockPos länd;"),
        "reading any block": GOOD.replace("WorldMemory m = memory;", "WorldMemory m = memory; var b = m.hashCode() > 0 ? null : level;"),
        "x-ray by import": GOOD.replace("import java.util.Set;", "import java.util.Set;\nimport net.minecraft.client.Minecraft;"),
        "fully qualified bypass": GOOD.replace("WorldMemory m = memory;", "WorldMemory m = memory; var mc = net.minecraft.client.Minecraft.getInstance();"),
        "Baritone search": GOOD.replace("Player.walkNear(land, 1);\n\t}", "Bari.get().getMineProcess();\n\t}"),
        "a thread": GOOD.replace("WorldMemory m = memory;", "WorldMemory m = memory; new Thread(() -> {}).start();"),
        "reflection": GOOD.replace("WorldMemory m = memory;", "WorldMemory m = memory; getClass().getDeclaredFields();"),
        "a command": GOOD.replace("WorldMemory m = memory;", 'WorldMemory m = memory; String s = "x"; s.sendChat();'),
        "a wildcard import": GOOD.replace("import java.util.Set;", "import java.util.*;"),
        "java.util.concurrent": GOOD.replace("import java.util.Set;", "import java.util.Set;\nimport java.util.concurrent.Executors;"),
        "wrong package": GOOD.replace("skills.evolved;", "skills;"),
        "wrong base class": GOOD.replace("extends EvolvedSkill", "extends Object"),
        "no offer": GOOD.replace("public static Option offer(Context c)", "static Option when(Context c)"),
        "name mismatch": GOOD.replace('return "leave_water"', 'return "collect"'),
        "genes": GOOD.replace("WorldMemory m = memory;", 'WorldMemory m = memory; Tune.get("reflex.creeper_dist");'),
        "memory writes": GOOD.replace("WorldMemory m = memory;", 'WorldMemory m = memory; memory.remember("log", land, "oak_log");'),
    }
    for name, src in cases.items():
        assert src != GOOD, name
        assert bad(src), "not caught: " + name


def test_strings_and_comments_are_not_code():
    src = GOOD.replace('"no dry ground seen"', '"no level ground seen, Mc"')
    assert bad(src) == [], bad(src)


def test_gene_list_is_append_only_and_valid():
    e = {"name": "evolved.leave_water", "kind": "BOOL", "def": 0, "skill": "leave_water", "why": "x", "gen": 86}
    assert fairplay.check_gene_list([], [e]) == []
    assert fairplay.check_gene_list([e], [e, dict(e, name="evolved.dig_out", skill="dig_out")]) == []
    assert fairplay.check_gene_list([e], [])  # removed
    assert fairplay.check_gene_list([e], [dict(e, why="changed")])  # edited
    assert fairplay.check_gene_list([], [dict(e, kind="REAL")])
    assert fairplay.check_gene_list([], [dict(e, **{"def": 1})])
    assert fairplay.check_gene_list([], [dict(e, name="evolved.X")])
    assert fairplay.check_gene_list([], [dict(e, skill="other")])
    assert fairplay.check_gene_list([], [e, e])
    assert fairplay.check_gene_list([], [e], known={"evolved.leave_water"})


def test_patch_paths():
    d = fairplay.EVOLVED_DIR
    ok = [("A", d + "LeaveWater.java"), ("M", fairplay.REGISTRY), ("M", fairplay.GENES_FILE)]
    assert fairplay.check_paths(ok) == []
    assert fairplay.check_paths([("M", d + "Player.java")])
    assert fairplay.check_paths([("A", d + "Player.java")])
    assert fairplay.check_paths([("M", "mod/src/main/java/io/github/plrlr/autopilot/Tune.java")])
    assert fairplay.check_paths([("A", d + "sub/X.java")])
    assert fairplay.check_paths([("M", "scripts/loop/common.py")])


def test_registry_line():
    line = fairplay.registry_line("leave_water", "LeaveWater", 'swim to "seen" land\\n')
    assert line == '\t\tadd(new Entry("evolved.leave_water", "leave_water", LeaveWater::new, LeaveWater::offer, "swim to seen landn"));', line


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
