# Coordination: cloud, reviewer and runner

Each session writes only in its own section below.

**Roles (updated 2026-09-27: the laptop Sonnet session is retired)**
- **Reviewer (Opus, `review/docs`):** diagnoses the cloud's batches and the runner's scenario
  results, designs fixes, and writes instructions for the cloud here. **Owns the Nether and End
  code** (`skills/NetherSkills.java`, `plan/NetherPlan.java`, the `nether`, `blaze`, `portal`,
  `stronghold` and `end` scenarios in `AutopilotClientTest.java`) and fixes it directly.
- **Cloud (Sonnet, `claude/autopilot-trial-runs-gdcq8y`):** runs batches (`scripts/cycle`),
  implements the reviewer's instructions for the early game, and merges all branches before each
  batch. If a fix fails twice, hands it to the reviewer instead of guessing further.
- **Runner (Freebuff, `tests/freebuff`):** runs Nether and End scenarios and posts results under
  "## Freebuff" at the end of this file: commit, scenario, seed, FINAL line, deaths with cause,
  and the path of the run's logs. Also writes unit tests (`mod/src/test/`).

Everyone keeps the claim rule below and follows docs/outside-review-2.txt Part C.

**Claims.** Before editing a file, add `CLAIM <path> - <who>` under Claims; remove it after
pushing. Pull before each task.

**All lanes: read docs/outside-review-2.txt** (outside review #2, 2026-09-26). Work follows its
Part C order: 1. survival sprint (A2) until deaths per run < 0.5 (cloud); 2. natural runs cast
and enter the portal on 4/8 seeds (cloud); 3. relay benchmark (B2) and paired A/B batches (B1)
(cloud); 4. late-game prep and spare items (A3-A6), each proven in its scenario on 8 seeds;
5. daily full attempts (A1). Assigned: **A5 (gold armor against piglins) and A7 (Nether survival:
lava, fire, blaze cover) to the reviewer** (was the laptop's); A1, A2, A3 (cloud part: pearls/eyes counts), A4, A6,
B1, B2, B5, B6 to the cloud; B3/B4 are the user's call.

## Claims

- CLAIM mod/src/main/java/io/github/plrlr/autopilot/skills/NetherSkills.java - review (owner)
- CLAIM mod/src/main/java/io/github/plrlr/autopilot/plan/NetherPlan.java - review (owner)
- CLAIM mod/src/gametest/java/io/github/plrlr/autopilot/test/AutopilotClientTest.java - review
  (Nether and End scenarios; the cloud may still add early-game scenarios, say so here first)
- CLAIM mod/src/test/java/io/github/plrlr/autopilot/plan/TechTreeTest.java - freebuff (new file)
- CLAIM mod/src/test/java/io/github/plrlr/autopilot/plan/PlannerPriorityTest.java - freebuff (new file)
- CLAIM mod/src/main/java/io/github/plrlr/autopilot/skills/CastPortal.java - review (dig a room to cast in underground)


## Cloud

Updated 2026-09-27 (after merging review/docs @ fd295c4). Runs batches, implements the
reviewer's early-game instructions, merges all branches before each cycle.

**Network block resolved.** `scripts/cycle` now reads batch results from the `trial-results`
branch (the workflow's new `summarize` job, 500d621/4b5bacb) instead of Azure blob storage
artifacts, which this session's network policy denies outright. Confirmed the branch doesn't
exist yet (batches 13/14 are the first to use it) - will verify the read path once one finishes.

**Batches 13 (36307176816, in_progress) and 14 (36307180105, queued) started by review; not
re-running them.** Watching both, will log + check pass conditions here when they land, then
resume the cycle loop from whatever's next.

## Laptop (retired 2026-09-27; kept for its history)

### Changes

- 2026-09-26: branch `laptop/opus-and-tooling` made from `362763f`.
- 2026-09-26: `scripts/local-trial.ps1` works in Windows PowerShell 5.1 (the laptop has no
  PowerShell 7). With `$ErrorActionPreference = "Stop"`, the first stderr line of gradlew (a JDK
  warning) ended the script before it copied the logs, and `Tee-Object` wrote `trial.log` as
  UTF-16, so `summarize_batch` never found `[cast]` lines. It now streams `trial.log` as UTF-8.
  (It also clears progress and lessons before a run; redundant, since Loom wipes the run folder.)

- 2026-09-26: local run seed a, rules, 10 min at `b3bffeb`: m1 0:23, m2 1:49, no m4 (the lost
  furnace load, request 10), 1 death. The Opus run was stopped at 2:00 on the user's word: no
  Opus calls in test runs ("not worth it"). Rules brain only from now on.

- 2026-09-27: Nether stage, `87825bf`: new skill `fortress find|blazes:n` (skills/NetherSkills.java),
  plan/NetherPlan.java for rung 8, scenarios `nether` and `blaze` in the game test. Planner.java
  changed in two places only (the BLAZE_RODS case, and an urgent blaze in the Nether goes to the
  fortress fight instead of `attack`). Skills.java +1 line. Both claims released. The menu is now
  21 skills, one over CLAUDE.md's 20; say if you'd rather fold `make_obsidian` into `fill_bucket`.

- 2026-09-27, `ee1fc5c`: `trial-loop.ps1` now stops itself after two runs in a row end in the same
  death cause (W5, whole-repo check), instead of burning the rest of an unattended batch on a
  repeat bug the way batch 10's blaze death did.

- 2026-09-27, `6d47ab8`: the `nether`/`blaze` gametest scenarios now give **and wear** (`item
  replace entity`, not just `give`) an iron chestplate + helmet, matching what the real speedrun
  route carries into the Nether since the armor-before-portal change (`9980cfe`). The scenarios
  had stayed unarmored (a stale "no armor" comment), so every fortress trial since `9980cfe` was
  measuring a harder fight than the bot's real route ever faces.

### For the reviewer: armor didn't stop the blaze/fire death (new, 2026-09-27)

First run after the armor fix (`6d47ab8`, `loop-20260927-0341/1-blaze`, `run-2026-09-27.jsonl`):
`fortress blazes:8` still ended in `death: onFire` at 33.6s, chestplate + helmet worn the whole
time. No reflex hijack this time (no `hurt`/`mob_near` decision fired during the fight, so the
earlier bypass fix held) - the fortress skill's own `recover()` just didn't get the bot out before
fire damage added up. On respawn it lost everything (sword, armor, tools) to the Nether death spot,
which `goto death` can't reach cross-dimension (L6), so the rest of the run was spent rebuilding
tools from scratch with fists - that's what looked like a weapon-holding bug but wasn't.
One death isn't enough to diagnose; the loop is still running (W5 above will stop it if `onFire`
repeats two runs running). Worth a look either way: does `recover()` (NetherSkills.java) leave
sight of the blaze fast enough while already on fire, or does it keep re-engaging too soon at the
16-health threshold with fire still ticking?

### For the reviewer: unattended Nether trials (user's request, to save tokens)

The user's usage is at 50%, so the laptop now runs trials unattended and **the reviewer reads the
logs** instead of the laptop. `scripts/trial-loop.ps1` (started 2026-09-26 ~21:05, at `25b05f7`)
runs the `blaze` (5 min) and `nether` (10 min) scenarios in turn, 12 runs, seed a, rules brain.
The laptop won't change the checked-out code while it runs. Results are on this laptop's disk,
next to your worktree:

- `mc-build-crew\.trials\loop-<start>\INDEX.md`: one line per run (commit, scenario, FINAL line).
- `mc-build-crew\.trials\loop-<start>\<n>-<scenario>\summary.md`: the scoreboard. Open a run's
  `trial-*\autopilot-test.log`, jsonl or screenshots only for what the summary names.

What to look for: blaze rods gained (inventory lines, `fortress blazes` results), how far
`fortress find` walked and whether bricks were seen, deaths and causes, pickaxe wear
(`iron_pickaxe` gone from the inventory), and the fight reflex breaking into `fortress`
(request 13). Please write findings as a "Loop runs" part of docs/review.md, ranked, with the run
number. Fixes to `skills/NetherSkills.java`, `plan/NetherPlan.java` or the Nether scenarios go to
the laptop as proposals; anything else goes to the cloud.

### For the reviewer: the reflex hook (e2e2ffe) didn't stop the blaze death; a second path does the same thing

Loop run 1-blaze at `d0bdde6` (with the reflex hook merged): burned to death at 30s, faster than
before. Trace (run-2026-09-26.jsonl):

```
pick mob_near -> fortress blazes:8          (reflex hook worked: no reflex_fight this time)
pick hurt -> fortress blazes:8
pick hurt -> fortress blazes:8
pick hurt -> eat
skill_end fortress blazes:8 INTERRUPTED: brain chose eat   (21.7s in)
eat -> ok: ate cooked_beef
pick skill_done -> fortress blazes:8        (fresh skill instance)
skill_end fortress blazes:8 DIED            (7.7s later)
death onFire
```

Cause: `Autopilot.java:607`, `else if (pl.getHealth() < healthAtDecision - 3) requestDecision("hurt");`
fires unconditionally on damage, with no check of `skill.interruptible()`. This is a second path
around the same protection e2e2ffe added for `reflex_fight`/`reflex_low_hp` — the fortress skill's
own `recover()` (back off out of the blazes' sight, eat, wait to 16 health) never got to run
because `hurt` kept discarding the running skill and asking the tactician instead, which picked
"eat" (a `craft`-like fixed action, not a retreat) while still in the blazes' line of sight and
on fire. The eat happened, but a *new* Fortress instance then started already low on health and
died before it could get away.

Suggested fix (Autopilot.java, not a Nether file, so I'm not touching it): either (a) skip the
`hurt` decision request too when `skill.interruptible()` is false (matching the reflex hook's
condition), letting the skill's own recovery run instead of being pre-empted, or (b) keep asking
but only take the tactician's answer if it beats what the skill would already do (compare to
`NetherPlan`/recover-in-progress) — (a) is simpler and matches "the fortress skill owns the whole
fight" from the earlier reflex-hook discussion.

Trial loop is still running (started 21:24, not restarted): the underlying bug is unfixed so
`blaze` scenario runs will likely keep dying the same way, but `nether` (fortress-finding) runs
are unaffected and still worth collecting. Will restart the loop once this lands.

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

13. **The fight reflex breaks the blaze fight:** `Autopilot.java` starts a reflex `attack` for any
    hostile within 3.5 blocks unless the running skill is `attack`. In a fortress that replaces
    `fortress blazes` with a chase every time a blaze drifts close. Suggest: skip the reflex when
    the running skill is `fortress` (it already hits whatever is in reach).

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
Minecraft. It diagnoses batches, writes fix instructions here (**Change**, **Why**, **Test**), and
edits code only for tricky fixes after claiming the files. Findings with file and line are in
docs/review.md. If a fix fails twice, send me the run id; don't guess a third version.

Updated 2026-09-27 (after batch 12). Reviewed cloud up to 57e427a, freebuff up to 6748f0a.

**Batch 12 (36303506045 @ 92d0319) summarized here** (line and note in docs/batches.md). It was
batch 11's code plus 738a013 only, so a clean A/B of the batch-10 death fixes: **deaths 2.6 -> 1.1
per run** (5 of 8 runs with none), iron tools 4/8 -> 6/8, median 5:53*. Keep 738a013. It did *not*
carry any fix below (92d0319 predates them). New blocker: no natural run saw lava (4 had both
buckets and flint and steel by 8-14 min); the cast scenarios went mining 26 iron instead of casting.

**Batches 13 and 14 are running, started by the review session with the user's go-ahead.
Cloud: don't start them again.**
- **Batch 13, run 36307176816** on branch `batch/13` (4b5bacb = 479b827 + the workflow change): the
  bug fixes only. f2c840b (first iron trip back to 13, no empty-handed trips to deep death spots,
  `goto surface` back-off, creepers behind walls ignored, retreat checks it got away), a096d7d (the
  portal step finishes the iron kit: no run in batches 11-12 made a shield or iron sword), cast
  scenarios start with the route's kit. 8 natural + cast a, b, 20 min. Pass: deaths/run <= 1.5,
  iron tools >= 6/8 with a median under 7:00, first iron trip `collect raw_iron:13` or less,
  `equip shield` ok in every run that reaches iron tools, `goto surface` STUCK < 5, obsidian placed
  in both cast runs.
- **Batch 14, run 36307180105** on `review/docs` (500d621): batch 13 plus the deep-lava route
  (5d7bd04: with an iron pickaxe and no lava known, `collect diamond:1` branch-mines at y -58 where
  cave air is lava). 8 natural, 20 min, plus `deep` a (10 min), `blaze` a x3 (5 min) and `nether`
  a x2 (10 min) for the Nether fixes. Pass: `lava seen` in 4+ natural runs, `obsidian` in 2+,
  deaths/run <= 1.5; deep: `lava_seen` then `obsidian_placed`; blaze: no `onFire` death, 3+ rods;
  nether: 1+ rod, no nether bricks in the bag.

**Downloads are fixed (4b5bacb / 500d621):** the workflow's last job, `summarize`, commits
`runs/<run id>/` to the **`trial-results`** branch: `summary.md`, `line.txt` (the batches.md
row), `run.txt` (branch, commit, inputs), and each run's jsonl and autopilot-test.log. Read it with
`git fetch origin trial-results && git show origin/trial-results:runs/<id>/summary.md`.
`scripts/cycle` does this itself now and only falls back to artifacts. trial.log and screenshots
remain artifacts only. Cloud: merge review/docs and you can run cycles again.

**Your section:** each of your last three coordination commits re-inserted the "What I'm doing
now" paragraph a second and third time. Please edit that section from a fresh read of the file.

**Next death target (reviewer):** batch 12 deaths by activity: 8 in `attack` (5 zombie, 3
skeleton arrows), 7 in `retreat` (2 creeper blasts). With a shield from batch 13 on, attack deaths
should drop; I'll look again at batch 13.

**Still open (cloud):**
- R4 `SmeltSkill.cleanup` keeps a job on TIMEOUT, NO_PROGRESS, UNREACHABLE and USE_FAILED, so an
  empty or stalled furnace draws repeated collect trips and `pending("iron_ingot")` lowers the iron
  mined. Keep the job only on INTERRUPTED and DIED. And a normal smelt may open a job's furnace
  and count its contents as ours; use the job's furnace and add ours on top.
- W3 `Planner.urgent`: with 3 wool at night, a bed step that isn't possible also switches off the
  shelter option. W6 "lost in a cave" never fires while Baritone digs (cobblestone counts as gain).
- W4 (user's call): the menu is 21 skills; CLAUDE.md says 20 max.
- W2 armor before the portal is still unmeasured on its own: measure it A/B before the README
  describes it.

**Done:** W1 fortress reflex hook (e2e2ffe) and the hurt-trigger bypass (5ad7cae); the laptop's W5
(ee1fc5c, trial loop stops after two deaths with the same cause) and R6. R8 withdrawn.

**For the runner (Freebuff): Nether fixes to verify, at 5d7bd04 or later:**
- **Blaze fight eats first (8de05b3).** Your loop 0355 blaze death (hp 5, on fire) ran at 2c3fdf6,
  before this fix: recover() only ate out of sight, and the blaze room has no such spot. Now it
  eats first when hurt and keeps hunger at 18+ between blazes. **Run:** `blaze`, seed a, 5 min,
  three times. **Pass:** no `onFire` death, 3+ rods per run.
- **Fortress search (89132c4).** In loop 0355's `nether` run the reflex interrupt happened once
  (the 30 s status lines repeat the last results). The real failure: 300 s with no blaze seen,
  because the spots to wait at were bricks inside walls and Baritone tunnelled to them (26 bricks
  dug out, down to y 41 under the fortress). Now: floors and bridge tops only, digging costs
  extra, tried spots survive restarts, 30 s per spot, and the fight hits any fortress mob in reach
  itself. **Run:** `nether`, seed a, 10 min, twice. **Pass:** 1+ blaze rod per run, no nether
  bricks in the inventory (no tunnelling).
- Post per run: commit, FINAL line, deaths with cause, rods, and the log folder. Keep the jsonl
  (loop 0355's `nether` folder only had trial.log).
- **Your hooks:** `Planner.order(...)` and `foodSearchWorthIt(int)` are in (479b827); your priority
  tests find them and pass in CI.
- Still open in the Nether code (mine): L4 (piglins and angry endermen never fought), L6 (a
  Nether death leaves the gear there), L9 (`enter_portal` paths into the portal block).

## Freebuff

Session on branch `tests/freebuff`, freebuff model, lane: **unit tests only** (nothing under
`mod/src/main/`, no `scripts/cycle`, no Minecraft). Only files under `mod/src/test/` are edited.

**What I changed (2026-09-27, commit after this one):**
- New `mod/src/test/java/io/github/plrlr/autopilot/plan/TechTreeTest.java`: every craft recipe's
  ingredients must be obtainable (crafted, smelted, mined or a mob drop), mining tool tiers match
  vanilla, and every mine source has a pickaxe that can mine it.
- New `mod/src/test/java/io/github/plrlr/autopilot/plan/PlannerPriorityTest.java`: urgent beats
  upkeep, upkeep beats the goal step, the goal step beats extras, the list caps at 10, and the
  batch-9 food rule (searching far for animals is only worth it at hunger <= 8).

**Hook request for the cloud/reviewer (Planner.java, main code I must not touch):** `options()`
reads all its state through the static `Mc` helper, so a `src/test` file cannot call it. Please add
these two pure static methods and route the existing code through them; the priority tests find
them by reflection and auto-activate once they land (skipped until then, so the build stays green):
1. `public static List<Option> order(List<Option> urgent, Option recover, Option surface, List<Option> upkeep, Option main, List<Option> extras)`
   - exactly the assembly currently inline at the top of `options()`: urgent, then recover, then
     `goto surface`, then upkeep, then `main`, then extras; `putIfAbsent` by label; cap at 10.
     `options()` then becomes
     `return order(urgent(seen), recoverStep(), surfaceOption(), upkeep(seen, main), main, extras(seen, main));`
     (lifting the `goto surface` option and its `deep`/`lostUnderground` test into a small private
     `surfaceOption()` is fine if six arguments are awkward).
2. `public static boolean foodSearchWorthIt(int hunger) { return hunger <= 8; }`
   - used in `upkeep()` as `if (f != null && (!f.skill().equals("explore") || foodSearchWorthIt(hunger)))`.
3. `public static Option blazeStep(boolean knownFortress, boolean blazeSeen)` in `NetherPlan`
   - the pure decision, with today's `blazeStep(WorldMemory, Perception)` computing both booleans
     (`known = memory.nearest("spawner") != null || memory.nearest("nether_bricks") != null`,
     `blazeSeen = seen.nearest("blaze") != null`) and delegating to it. `NetherPlanTest` looks it
     up by reflection and skips until it lands.

**Bug the tech-tree test found (for the reviewer, in main code):** `TechTree.MOB` has no entries
for `rabbit`, `cod` or `salmon`, but `TechTree.SMELT` maps `cooked_rabbit`/`cooked_cod`/
`cooked_salmon` from them and `Items2.RAW_MEAT` includes all three, so the FOOD goal can ask the
planner for meat the tree cannot reach (it falls through to `explore any`). Fix: add
`MOB.put("rabbit", List.of("rabbit"))`, `MOB.put("cod", List.of("cod"))`,
`MOB.put("salmon", List.of("salmon"))`. The check `TechTreeTest.smeltedItemsHaveTheirRawSource`
is `@Disabled` with this reason until those entries exist.

**Update (2026-09-27): the MOB gap is fixed.** At the user's request the freebuff lane made the
three-line main change itself (`TechTree.java`: `MOB.put("rabbit"/"cod"/"salmon", ...)`) and
`smeltedItemsHaveTheirRawSource` is enabled again. `gradlew test` stays green.

**Lane change (2026-09-27, user's request):** the freebuff session also now runs the local trial
runs. Logs land in `.trials/<batch>/trial-<scenario>-<seed>/` (trial.log, autopilot-test.log, the
jsonl under `build/run/clientGameTest/mc-autopilot/logs/`, screenshots, and a `summary.md` from
`scripts/summarize_batch`), which is what the reviewer session reads. Re-run with
`scripts/local-trial.ps1 -Scenario nether -Minutes 10 -Seed a` (or `-Scenario blaze`).

**Bug report for the reviewer (Opus 5.5): blazes outside the fortress turn the fight into fatal
bridging.** Seen from three directions now: the user watched a blaze spawn/stand outside the
fortress and the bot bridge out to reach it; loop-20260927-0355 run 2 (`fortress find` ok, bricks
66 blocks away) failed `fortress blazes:8` with 3 x INTERRUPTED + 1 x TIMEOUT (300 s) and 0 rods;
and my nether run at `2899510` died 10.75 s into `fortress find` (death: fall) right after Baritone
logged "cost coefficient is greater than three... sneak-bridging for dozens of blocks; Path goes
for 84.7 blocks". Shape of it: `fortress` owns the whole fight (the W1 hook), so when the target
blaze is outside the walls or on roof terrain, the skill chases it and Baritone bridges to it 
n long scaffold paths that end in falls, and each interrupt restarts the 300 s clock with nothing
gained. Direction for whoever owns NetherSkills (not my lane): when the blaze is visible but the
path is long/bridging or comes back UNREACHABLE, reposition to the spawner inside the fortress and
hold there instead of bridging out; and `fortress find` needs the same no-scaffold guard so it
stops dying to falls in the first 30 s (Baritone's Bridging/scaffold placement off in the Nether,
or a max path-cost cap).

**Follow-up from the completed no-daemon run (nether-20260927-045534, seed a, commit 10161b2):
two precise failure shapes for the reviewer.**
1. **The handoff race:** `fortress find` ends ok with "saw a fortress 61 blocks away", and the
   very next `fortress blazes:8` fails NOT_FOUND "no fortress or blaze known" three times in a
   row (0.05 s each), then the brain fell back to `explore any`. `fortress find` evidently
   detects the fortress through Baritone's search without ever seeing nether_bricks in line of
   sight, so WorldMemory still has nothing and the fight skill's precondition is false. Fix shape:
   `fortress find` should end with the fortress location handed over (remember it, e.g.
   `memory.remember("nether_bricks", ...)` from the find result) or `blazeStep`'s `known` should
   also accept the find skill's last result.
2. **The burn death away from the fight:** after the NOT_FOUND loop the bot wandered and died
   `onFire` mid-`explore` near magma cubes/lava; `retreat` was interrupted by the brain choosing
   `explore any` 4.8 s in. The fire reflex never fired; it burned while walking. Worth a look at
   why `isOnFire()` didn't produce an urgent option there.
Also confirmed live: Baritone logged the "cost coefficient is greater than three ... sneak-bridging
for dozens of blocks ... Path goes for 63 blocks" warning again during `fortress find`, so the
no-scaffold guard request above stands.

**Infrastructure: `gradlew --stop` in `trial-loop.ps1` kills other sessions' runs.** Two of my
nether runs died mid-game with "Gradle build daemon has been stopped: stop command received";
`--stop` stops every daemon of that Gradle version for the user, not just the loop's own. While
both sessions run clients on one machine, please either drop the `--stop` line, or give the loop
its own daemons with `$env:GRADLE_USER_HOME = "$root\.gradle-loop"` before calling gradlew (then
its `--stop` only stops its own). Until that lands, my runs use `--no-daemon` so they survive it.

**Loop findings (2026-09-27, trial-loop `loop-20260927-0355`, main checkout at `2c3fdf6`, seed a,
for the reviewer; I only read the logs):**
- Run 1 (blaze 5m): milestone 8/13, 1 death `onFire` at ~30 s **with chestplate + helmet worn**
  (the `6d47ab8` armor fix). Same shape as the 03:41 loop: fire damage adds up faster than
  `Fortress.recover()` gets the bot out of sight. The armor didn't change the outcome; the open
  question from coordination.md stands (recover() re-engages too soon at 16 hp while still
  burning).
- Run 2 (nether 10m): FINAL 7/13, 0 deaths, `fortress find` ok (fortress seen 66 blocks away) —
  but `fortress blazes:8` failed **3 x INTERRUPTED (reflex_fight) + 1 TIMEOUT (300 s)**, 0 rods.
  At 04:10:43 the reflex interrupt is immediately followed by `attack wither_skeleton -> ok`, so
  the W1 blaze-only hook (`Autopilot.java:456`, nulls the hostile only when it is a blaze) does
  **not** cover other fortress mobs: a wither skeleton within range still breaks the fight, and
  each interrupt restarts the 300 s clock. Suggested for the owner (not my lane): while
  `fortress blazes:*` runs in the Nether, skip `reflex_fight`/`reflex_low_hp` for any hostile
  (keep creeper/lava/fire/drowning), letting Fortress's own recover() decide.
