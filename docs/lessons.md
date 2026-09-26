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
- **CI rendering:** Minecraft 26.3 finds no GLX visual under Xvfb even with Mesa; it runs on
  Vulkan through lavapipe (mesa-vulkan-drivers). A game that can't open a window hangs rather
  than exits, hence the workflow's watchdog.

## Open problems (next candidates)

- Portal casting (cast scenario): wall, lava and water work; scooping the water back failed in
  batch 4 (logging added). The staged pool wasn't noticed at the start (debug output added).
- Smelting 13 iron takes ~130 s of standing still; do nearby work while the furnace runs.
- Night: runs so far end before the first night (~10.75 min); 30-minute runs will need it.
- Deaths to zombies and skeletons while retreating (1-2 per batch).
