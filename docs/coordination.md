# Coordination: cloud, laptop and reviewer sessions

Three sessions work at once. Each writes only in its own section below.

**Lanes**
- Cloud (`claude/autopilot-trial-runs-gdcq8y`): the early game (wood, iron, lit portal), the cave
  fix, and the trial loop. Only the cloud runs `scripts/cycle`; it merges the other branches
  before each batch.
- Laptop (`laptop/opus-and-tooling`, and branches for new work): the Nether stage, finding a
  fortress and getting blaze rods, in new Nether skill files, tested with local portal/nether
  scenario runs.
- Reviewer: small fixes only in files nobody has claimed; bigger changes go to the owner as a
  proposal in its section.

**Claims.** Before editing a file, add `CLAIM <path> - <who>` under Claims; remove it after
pushing. Pull before each task.

## Claims

- CLAIM mod/src/main/java/io/github/plrlr/autopilot/Autopilot.java - cloud (cave escape)
- CLAIM mod/src/main/java/io/github/plrlr/autopilot/plan/Planner.java - cloud (cave escape)
- CLAIM mod/src/main/java/io/github/plrlr/autopilot/skills/MoveSkills.java - cloud (goto surface)
- CLAIM mod/src/main/java/io/github/plrlr/autopilot/state/Breadcrumbs.java - cloud (new)
- CLAIM mod/src/main/java/io/github/plrlr/autopilot/skills/CastPortal.java - cloud (portal)

## Cloud

Updated 2026-09-26 23:55 UTC. Latest pushed commit on my branch: see `git log`.

**Already done on my branch, please build on it instead of redoing it** (base your branch on
`claude/autopilot-trial-runs-gdcq8y`, or merge it in first):
- Your task 2 is mostly done: `scripts/summarize_batch` (commit a68c313) counts runs that miss a
  milestone as the full run length in medians (marked `*`), shows success rates next to each
  median, has a portal-path table from the mod's `checkpoint` log events and the FINAL line's
  `checkpoints ...` list, and a deaths-by-cause section. What's still open there, if you want it:
  a check that the time breakdown adds up (skill seconds vs run length; "idle" is often 0 because
  overlapping skills double count), and reading several batch folders at once to compare them.
- Your task 3 partly exists: `mod/src/test/java/.../plan/PlanLogicTest.java` (explore targets,
  tiers, goal-need order, optional rungs), `log/CheckpointsTest`, `log/LessonsTest`,
  `brains/StrategistRulesTest`, `skills/SkillArgsTest`, `skills/CastGeometryTest`. Still missing:
  planner option order (urgent before upkeep before goal step) and the tech tree (every recipe's
  ingredients are craftable, smeltable, minable or a mob drop; tool tiers for mining). New test
  files only, please; tell me here if a test needs a small hook in main code and I'll add it.
- docs/laptop-tasks.md was my earlier brief for you; your task list replaces it.

**Log events you can use** (run-*.jsonl): `skill_start {skill, trigger}`, `skill_end {skill, ok,
code, detail, seconds}` (code = skills/Fail), `death {detail = cause}`, `milestone {detail = "N at S s"}`,
`checkpoint {detail = name}`, strategist lines `{layer: strategist, brain, choice, reason,
latency_ms, tokens_in, tokens_out}`, tactician lines with `options`. The test's FINAL line has
`milestone times m1@..s ...` and `checkpoints name@..s ...`. Game logs (trial.log) have `[cast]`,
`[bucket]`, `[station]` lines.

**Requests for the laptop** (after your list, if time allows):
1. In the local Opus run, note Opus calls used, their latency, and quote the goals and reasons
   it picked where they differ from the rules'.
2. Watch the staged cast once: `.\scripts\local-trial.ps1 -Scenario cast -Seed a -Minutes 10`.
   Say step by step what happens (wall, each lava and water pour, scoop, frame, lighting) and
   include the `[cast]`/`[bucket]` lines. This is the current blocker for reaching the Nether.
3. Always note the commit you tested.

**Menu note for the Nether work:** the tactician menu (skills/Skills.java) is at its 20-skill
limit. New Nether skills can take arguments on existing names or replace a rarely used one;
propose which here and I'll wire the menu (I own Skills.java and Planner's goal steps; you own
your new skill files and can propose the BLAZE_RODS step logic).

**What I'm doing now:** the portal cast fix (cast scenario), noise-twin batches 8a/8b (same
commit twice, 8 seeds x 20 min), then smelting-while-mining, hiding underground and the new
first wood trip get their first batch.

## Laptop

(The laptop session writes here.)
