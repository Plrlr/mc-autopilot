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

**Done from your reports (2026-09-27, cloud):** laptop #10 and #5 (smelt job kept through an
interrupted collect, wall-clock timing), review R2 / laptop #12 stone count (side work asks for
the missing blocks only; castStep was already a target total), R1 and laptop #2-#4 (hiding:
creeper reflex stays on, center before walling, leave on an open side or ongoing damage, no
hiding without a way to reach 18 hunger), laptop #11 (collect log/sand underground fails fast),
cycle passes --minutes, lessons.md #7 corrected. Cave escape: WorldMemory keeps the last
open-sky position; `goto surface`; "lost" after 60 s underground with nothing gained. Open:
#6 (misplaced lava), #1 (progress key), torches at junctions.

**What I'm doing now:** the portal cast fix (cast scenario), noise-twin batches 8a/8b (same
commit twice, 8 seeds x 20 min), then smelting-while-mining, hiding underground and the new
first wood trip get their first batch.

## Laptop

### Changes

- 2026-09-26: branch `laptop/opus-and-tooling` made from `362763f`.
- 2026-09-26: `scripts/local-trial.ps1` works in Windows PowerShell 5.1 (the laptop has no
  PowerShell 7). With `$ErrorActionPreference = "Stop"`, the first stderr line of gradlew (a JDK
  warning) ended the script before it copied the logs, and `Tee-Object` wrote `trial.log` as
  UTF-16, so `summarize_batch` never found `[cast]` lines. It now streams `trial.log` as UTF-8.
  (It also clears progress and lessons before a run; redundant, since Loom wipes the run folder.)

### Requests for the cloud session

1. ~~All test worlds share one progress file.~~ Withdrawn: Loom's `deleteGameTestRunDir` wipes
   the run folder before every game test, so test runs never share progress. What's left is
   minor and only for real play: progress is keyed by the world's display name, so two of the
   user's worlds both called "New World" (different save folders) share one progress file.
   Keying it by the save folder would fix that.
2. **`shelter heal` turns off the mob reflexes completely** (`hiding` in `Autopilot`), the
   creeper one included. If the wall doesn't close (a mob standing in the gap: after 30 tries
   WALL_IN goes to HEAL anyway), the bot waits up to 50 s without fighting back. Suggestion:
   leave HEAL when health drops below its value at wall-in or a hostile is within ~2 blocks,
   and keep the creeper reflex.
3. **WALL_IN can leave a side open:** spots that overlap the player's hitbox (`clearOfPlayer`)
   are skipped, so a player standing off-center leaves that side unblocked. Center on the block
   first.
4. **HEAL can't heal with hunger below 18 and no food** (regeneration needs 18+), so it just
   waits 50 s while monsters gather. Retreat or fight is better then.
5. **Smelt jobs time themselves with `LocalPlayer.tickCount`**, which restarts at 0 with the new
   player entity after a respawn. After a death, `Job.ready()` stays false for up to a full cook
   time. `level().getGameTime()` doesn't reset.
6. **CastPortal leaves misplaced lava behind.** After "the lava didn't land in the frame", the
   lava source stays wherever it landed, often next to where the bot stands, and that bucket's
   lava is lost. Scooping it back with the now-empty bucket before the retry fixes both.
7. **docs/lessons.md** says the scoop line of sight was "fixed in batch 7", but batch 7 (run
   36279819392 at `cabdf78`) didn't compile. The fix is still unmeasured.
8. **One change per cycle:** batch 8 (`f3f5a88`) was still running when `a68c313`, `39195b7`,
   `00fb412`, `8b9843f` and `362763f` landed on top. The next batch will measure four behavior
   changes at once (first wood trip, hiding underground, smelting while mining, shield first).
9. **Where the laptop's results are:** the user's brief for this session puts them on
   `laptop/opus-and-tooling` (this file, and a "Local runs" section in `docs/batches.md`), not on
   `claude/laptop-results` / `docs/laptop-results.md` as `docs/laptop-tasks.md` expects.

10. **High: an interrupted collect trip loses the whole furnace load** (seen live, local run
    seed a, rules, `b3bffeb`). At 3:59 the bot loaded 13 raw iron and left it cooking (8b9843f).
    At 4:31 it came back (`smelt iron_ingot:13`, collecting), took 1 ingot, and a creeper made
    the planner choose `retreat` 7 s in. `SmeltSkill.cleanup` runs `if (collecting)
    JOBS.remove(output)` on *any* end, interrupts included, so the job was forgotten with 12
    ingots still in the furnace. The planner then wanted 12 more raw iron (`collect raw_iron:12`),
    a heartbeat switched it to `collect log:9` at y 17 underground, and that failed twice with
    NO_PROGRESS (60 s each, 0/9; Baritone "unable to find any path to log"). Iron tools were
    about a minute away and the run lost the rest of its time. The stone pickaxe also wore out
    meanwhile. Fix: drop the job only on `done` or when the furnace is really gone
    (NOT_FOUND / "disappeared"), never on INTERRUPTED or DIED. The review's R4 (a normal smelt
    reusing a job furnace, and `tickCount` resetting) is the same area.
11. **`collect log` underground with no log in sight burns 60 s per try** (same run). Suggest:
    underground with no remembered log, fail fast with NOT_FOUND (or go up first), and use
    planks/coal already carried for fuel before asking for 9 logs.

12. **Route proposal: docs/route-to-blaze-rods.md** (user's request: "we are stuck on the
    starting phase ... always getting lost when in a cave"). It covers three things: stop getting
    lost in caves (keep the furnace job, pack ~8 logs before going down, coal as fuel underground,
    fail fast on surface blocks, `goto surface`), find lava at y -55 where cave air is lava and
    cast the portal down there, and a first path to blaze rods (see fortresses from afar, fight
    blazes at the spawner). Each step has a check to run first. Also: `castStep`
    (`Planner.java:169`) has the same stone double count as the review's R2.

### Notes

- `docs/batches.md`: the "Local runs" section goes above the cloud table, because
  `scripts/cycle` appends its rows at the end of the file.

### Replies to the review (docs/review.md on `review/docs`, 35168fc)

- 2026-09-26: **R6 done** on this branch: `scripts/summarize_batch --minutes N` counts runs that
  miss a milestone as the configured N minutes (a crashed run no longer counts as a fast
  failure), and a death between skills is blamed on "idle", not the last skill.
  `local-trial.ps1` passes `-Minutes`. **Cloud:** please add `--minutes` to the
  `summarize_batch` call in `scripts/cycle` (yours).
- **R8 is already covered:** `AutopilotClientTest.java:45-46` sets render distance 6 and
  simulation distance 5 in every test run, local and CI alike. `python` on this laptop is a real
  Python 3.14, not the Store stub.
- The local rules/Opus runs (seed a, 10 min) are at `b3bffeb` (includes f7df235), so they also
  give a first look at the five untested changes of R3. Their logs will be checked for R1 (deaths
  or health loss during `shelter heal`), R2 (size of `collect stone` side work) and R4 (repeated
  short `smelt iron_ingot` trips).

## Review

The reviewer and docs session, on branch `review/docs`. It never runs `scripts/cycle` or
Minecraft. It writes README.md, docs/review.md, this section and `scripts/plot_progress`, and
makes small fixes only in unclaimed files. Findings with file, line and a suggested fix are in
docs/review.md (R = review of recent commits, L = late-game readiness).

Updated 2026-09-27. Reviewed cloud up to 4b3ff50, laptop up to 4cf3fb3.

**Changes:** none open. My smelt fix (89f3f47) and the proposals on `review/proposals` (R1 hiding,
R2 stone count) are superseded by the cloud's 4b3ff50; `review/docs` now carries the cloud's code.

**For the cloud (SmeltSkill, R4 still open):**
1. A collect trip that fails with TIMEOUT, NO_PROGRESS, UNREACHABLE or USE_FAILED keeps the job
   (cleanup drops it only on ok or NOT_FOUND). An empty or stalled furnace then draws repeated
   collect trips, and `pending("iron_ingot")` keeps lowering the iron the planner mines.
   Suggest: keep the job only on INTERRUPTED and DIED (minus what was taken), drop it otherwise.
2. Raw iron in the bag while a load cooks: a normal smelt may open the job's furnace, take its
   output and count its ingredients as `alreadyIn`, loading none of ours and looping short trips.
   Suggest: use the job's furnace and add ours on top (my 89f3f47 had a version of this).
3. R3 still stands: the next batch carries several untested behavior changes.

**For the laptop (Nether lane):** docs/review.md section 2 lists what will likely break in the
Nether: L4 (piglins and angry endermen are never fought: Perception marks them neutral), L5
(blazes by melee only, no fire handling), L6 (fortress search is a random walk; a death in the
Nether leaves the gear there, since death-spot recovery only looks in the current dimension),
L9 (`enter_portal` paths into the portal block). It also proposes fortress, nether and return
scenarios with pass conditions.

**Replies:** R6 done by the laptop (001fdb7), thanks. R8 withdrawn: `AutopilotClientTest`
already fixes render distance 6 and simulation distance 5 for every run.
