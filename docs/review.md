# Review notes

Written by the review/docs session (see docs/coordination.md, "Review"). Findings are ranked by
likely impact on trial results. Line numbers refer to the commit named in each section.
Owners: **cloud** = `claude/autopilot-trial-runs-gdcq8y` (mod code, workflow, cycle),
**laptop** = `laptop/opus-and-tooling` (local runs, tooling).

## 1. Last 15 commits (cloud up to f7df235, laptop up to 9151eeb)

Reviewed: fc5502f..f7df235 on the cloud branch (15 commits) and d3ea19a and 9151eeb, the two
laptop-only commits. Nothing here has been run; the findings come from reading the code.

### R1. Hiding turns off every combat reflex, even when the wall-in failed (cloud, high)
- `mod/.../Autopilot.java:443`: `hiding` is true for the whole `shelter heal` skill, and while it
  is true the hostile branch is skipped completely, including the creeper retreat and the
  point-blank fight.
- `mod/.../skills/NightSkills.java:189`: WALL_IN moves on to HEAL after 30 tries, or as soon as
  `holdItem(throwaway)` fails, even with a gap left open. A zombie standing in that gap is the
  usual reason a placement fails.
- Result: the bot stands still for up to 50 s with an open side, looking straight up to eat
  (`setXRot(-90)`), while the reflexes that would fight back are switched off. A creeper next to
  the hiding spot is ignored too. The trigger is health <= 8 with a mob within 3.5 blocks
  (`Autopilot.java:452`), so this is exactly the case where a mob is already next to us.
- Why it matters: deaths are the biggest source of noise (batches 8a/8b: 19 and 24 deaths, iron
  8/8 vs 4/8 on the same commit). 00fb412 is meant to cut deaths but can add a new kind.
- Fix: only count as hiding once WALL_IN found no gap (closed in). Otherwise let the reflexes
  run as usual. Keep the creeper retreat even while hiding. Abort HEAL if health drops while in
  it (something reaches us). Task test: `shelter heal` with `-PtestGive="cobblestone 16"` next
  to a zombie that's been summoned in, in a tunnel.

### R2. Side work asks for far too much stone (cloud, medium)
- `mod/.../plan/Planner.java:309`: `collect stone:<Mc.count("stone") + BLOCKS_NEEDED - throwaway>`.
  `collect item:n` means *n more* (`CollectSkill.java:93`: `now - before >= want`), so the
  stone we already carry is counted twice. With 30 stone in the bag the bot goes to mine 30+
  more while the furnace cooks, and there's no 64 cap here.
- Fix: `"stone:" + (CastPortal.BLOCKS_NEEDED - Mc.count("throwaway"))`. A unit test for
  sideWork's arguments would catch this kind of bug.

### R3. Five untested behavior changes will land in one batch (cloud, loop rules, high)
- a68c313 (first wood trip), 39195b7 (dragon check), 00fb412 (hide and heal, shield), 8b9843f
  (smelt while mining) and 8bb11ae (cast refill) have not been in any batch yet. Batch 8a already
  bundled four changes with a new seed set and run length (4→8 seeds, 10/15→20 min), so it has no
  comparable baseline.
- Why it matters: CLAUDE.md's loop rules call for one focused change per cycle, and a change is
  accepted only if the target stage improves. The 8a/8b twins show iron tools can swing 8/8 ↔ 4/8
  from noise alone, so a bundled batch can't show which change helped or hurt.
- Fix: check hide/heal and smelt-while-mining with task tests first (minutes each), then batch
  them separately, or at least as noise twins. Treat 8a+8b together as the new baseline and
  say so in docs/batches.md.

### R4. A leftover smelt job can loop or starve the iron count (cloud, medium)
- `mod/.../skills/SmeltSkill.java:127-145`: a normal smelt that opens the furnace where a job is
  still cooking takes the job's output (line 128) and counts the job's remaining ingredients as
  `alreadyIn`. If `alreadyIn >= want` it loads none of our raw iron, overwrites the job with the
  remaining count, and ends as done. Raw iron is still in the bag, so the planner offers the
  same smelt again: a loop of short trips until the slot drains.
- `Planner.java:326` subtracts `SmeltSkill.pending` from the iron still needed, so a stale job
  (its output already taken by another smelt) makes the planner mine too little iron until a
  collect trip fails and clears it.
- `SmeltSkill.java:27`: readiness uses `player.tickCount`, which restarts at 0 after death or a
  dimension change, so a job looks unready for much longer than it really is.
- Fix: a normal smelt should avoid a furnace with a job (`Station` already skips job furnaces
  when picking up, `Station.java:38`, but FIND doesn't), or merge into the job and add the output
  it took to `made`. Store `readyAt` in level game time (`level.getGameTime()`), not player ticks.

### R5. Some checkpoints are the bot's own report (cloud, loop rules, low-medium)
- `obsidian_placed` and `frame_complete` are marked inside the skills (`CastPortal.java:174`,
  `:247`, `:409`; `PortalSkills.java:138`). CLAUDE.md: "Verify milestones from world state, never
  from the bot's own report." `two_buckets`, `flint_and_steel`, `lava_seen` and `portal_lit` do
  follow that rule (`Autopilot.java:710`).
- These skill-side marks don't write a `checkpoint` event to the jsonl log. Only the FINAL
  line has them, so a run that crashes before FINAL loses them. `Autopilot.checkpoints()`'s own
  `obsidian_placed` uses `PortalSkills.placedFrameObsidian()`, which is 0 for a cast frame
  (`frameOrigin` is only set by build_portal).
- Fix: in `checkpoints()`, count obsidian blocks in the known frame cells (cast and build) and
  mark `frame_complete` when all 10 are obsidian.

### R6. Batch medians count a crashed run as a fast failure (cloud, low-medium)
- `scripts/summarize_batch:129`: runs that miss a milestone count as `r["length"]`, which is the
  last 30 s progress report. A run that crashes at 2:30 counts as "failed at 2:30" and pulls the
  median *down*: the same bias the change was meant to remove.
- `scripts/summarize_batch:90-92`: `_doing` is never cleared at `skill_end`, so a death while
  idle (between skills or during a reflex) is blamed on the last skill that ran.
- Fix: count failed runs as the configured minutes (e.g. a `-m` argument, or `minutes` from the
  run name / workflow input), and mark crashed runs apart. Set `_doing = "idle"` on `skill_end`.

### R7. The docs no longer match the loop setup (cloud, low)
- CLAUDE.md still says "Keep 4 fixed benchmark seeds (a, b, c, d)"; the workflow and `scripts/cycle`
  now use a-h × 20 min. The "add 1-2 fresh seeds every ~5 batches" holdout was folded into the
  benchmark, so there's no fresh seed left to catch overfitting.
- docs/lessons.md cites "batch 7" (scoop line of sight), but docs/batches.md has no batch 7 row.
- docs/lessons.md open problems: "a bed ... is still missing" (bed and sleep code have existed
  since 69329c2; hiding came in 00fb412) and "smelting ... standing still" (8b9843f) are stale.
- CLAUDE.md: "Cap the cycles per day and log what each cycle cost": docs/batches.md has no cost
  or duration column.
- Fix: update CLAUDE.md's seed rule, add the batch 7 row or reword the lesson, add a runner-
  minutes column.

### R8. Local runs may not be comparable to cloud runs (laptop, low)
- `scripts/local-trial.ps1` runs at real speed on a real GPU. The cloud runs at ~0.75x under
  lavapipe. Milestone times are game ticks, so speed alone is fine, but the client's render and
  simulation distance decide which chunks are loaded. WorldMemory's scans and Baritone's surface
  search both depend on loaded chunks. If the local test profile has a different render distance
  than CI, seed a locally and seed a in the cloud find lava and trees differently.
- `Get-Command python` also matches the Windows Store alias stub, which opens the Store instead
  of running; test `python --version` instead.
- Fix: print render/simulation distance in the FINAL line and keep them the same in both places.

### Checked and fine
- Fair play: `WorldMemory.scanFar` (`WorldMemory.java:163`) reads the top block of each column
  but only records it after a line-of-sight check (`:182`); the same gate as the close scan.
  `Perception` counts mobs within 6 blocks behind walls (like hearing them); farther ones need
  line of sight.
- `Progress` dragon check (39195b7): `end_portal` blocks in the End appear only after the kill,
  and WorldMemory is filtered by dimension, so the stronghold's portal can't trigger it.
- `Goal.needs` in a fixed order (f3f5a88) removes a real source of run-to-run variation.
- The first wood trip (a68c313) adds 2 logs on top of what's needed, not 2 in total, so the first
  pickaxe isn't short.
- Push safety for this worktree: `review/docs` tracks `origin/claude/autopilot-trial-runs-gdcq8y`;
  the review session pushes with an explicit `origin review/docs` so it never pushes to the
  cloud's branch.
