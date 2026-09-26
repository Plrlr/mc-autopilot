# Lessons from trial runs

What the improvement loop has learned, so a fresh session doesn't rediscover it. Update it in
the same commit as the fix. docs/batches.md has the numbers per batch; this file has the why.
(The mod's own mc-autopilot/lessons.json is the per-machine tally of failure codes; this file is
the curated version for people and Claude sessions.)

## How to run a cycle

1. Make one focused change. Check it cheaply first: a task test for a single skill
   (`scripts/cycle -s '[]' -x '[{"seed":"a","id":"x","task":"<skill> <arg>","give":"<items>","minutes":"3"}]'`)
   or a staged scenario (`-x '[{"seed":"a","scenario":"cast"}]'`).
2. Full batch: `scripts/cycle` (4 benchmark seeds). It prints the one-page summary; read only
   that unless it points at something (then the run's jsonl, then screenshots).
3. Keep or revert by the loop rules in CLAUDE.md; add the note to docs/batches.md.

Cloud sessions can't build locally (the network blocks Fabric's and Mojang's hosts); the
workflow's unit-tests job compiles and tests in about a minute, the trial jobs take ~14 min for
10 game minutes (CI plays at ~0.75x speed).

## Game and API facts learned the hard way

- **Baritone owns hotbar slot 0.** With allowInventory on, its inventory helper swaps the best
  pickaxe back into slot 0 every tick. Anything we swap into slot 0 is undone before the click:
  every "couldn't place the crafting_table" in batches 1-3 held the pickaxe. Mc.holdItem uses an
  empty slot 1-7.
- **Baritone's legit branch mining doesn't go down by itself.** From the surface it "runs away"
  at the starting height (230 blocks at y 60 in one run). collect staircases to the ore's Y first.
- **Explores walk 120 blocks; stations are remembered within 64.** Without picking the table and
  furnace back up, every smelt crafted a new furnace (8 cobblestone).
- **Gravel drops flint 10% of the time.** Count gravel mined as progress.
- **Furnace fuel:** load fuel for the whole batch (1 plank = 1.5 items); one plank for five
  mutton stalled a furnace.
- **Forced goals:** a goal decision still pending overwrote forceGoal one tick later.
- **WorldMemory:** scan nearest-first and only remember water/lava sources open to the air; an
  ocean's water used the whole raycast budget before a pool 6 blocks away was checked.
- **Mobs you can see but can't reach** (across water, down a hole) cost 45 s timeouts in a loop
  until attack gave up after 8 s without getting closer.
- **Portal casting works block by block** (batch 6: lava into the frame spot, water beside it,
  the lava turned to obsidian). The water must be scooped from a spot whose line of sight
  doesn't cross the spot that just became obsidian.
- **WorldMemory's close scan reaches only 16 blocks.** Lava pools 20-50 blocks away were walked
  past; a surface-only scan to 48 blocks (top block of each column, line of sight) finds them.
- **Fight or flee needs one threshold.** Reflex fighting above 6 health and the planner fleeing
  at 8 flipped every decision at 7-8 health and got the bot killed (batch 6). Mobs marked
  unreachable still have to be fought when they're within 4 blocks.
- **CI rendering:** Minecraft 26.3 finds no GLX visual under Xvfb even with Mesa; it runs on
  Vulkan through lavapipe (mesa-vulkan-drivers). A game that can't open a window hangs rather
  than exits, hence the workflow's watchdog.

## Open problems (next candidates)

- Portal casting (cast scenario): scoop line of sight fixed in batch 7; the full 10-block frame
  and lighting are still untested. Lava bucket fills failed once in batch 5 (logging added).
- Lava is rare on the surface: if the far scan isn't enough, look underground (caves near y -54).
- Smelting 13 iron takes ~130 s of standing still; do nearby work while the furnace runs.
- Deaths: 7 in batch 6's four 15-minute runs (arrows, zombies, lava, fire), several while
  retreating. Night starts at ~10.75 game minutes; a bed or underground work is still missing.
