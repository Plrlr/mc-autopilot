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

- **The slow motion was the test framework (2026-09-27).** Fabric's client game tests run client,
  server and test thread in lockstep (a phaser plus a semaphore per tick), so every tick costs
  client + server time: 0.65x on the cloud and on the 8-thread laptop alike, the server "15000 ms
  behind" every 30 s. Free run (gametest FreeRun.java) steps aside during play: 0.965x, 1 lag
  warning instead of ~50, server 2-7 ms/tick, our mod under 1 ms/tick. Stepping aside must wake
  the client and server once (they're parked on their semaphores), or the game freezes.
- **Checkpoints in 26.3:** the player lives in players/data/<uuid>.dat, not level.dat; save it
  with PlayerList.saveAll() before zipping or a restore gives a fresh player.
- **Fragmented work:** with 20 s heartbeats, generation 1 split each iron trip into ~9 pieces
  (157 collect raw_iron, 738 furnace checks). focus.commit (gene) finishes the running task unless
  the top option is an emergency.

- **Cloud speed (2026-09-27, run 36323172968, 8 game minutes each):** plain drawing plays at
  0.98x real time on GitHub's machines. A 10 fps cap drops it to exactly 0.50x: in 26.3 the
  client runs one tick per frame at most, so the cap caps the game. Lithium/FerriteCore/Sodium
  showed no gain under the cap (untested uncapped). `/tick rate` above 20 speeds the world but
  not the player, so it would distort play; not used.

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

- **Noise:** the same commit twice (batches 8a/8b, 8 seeds x 20 min) gave iron tools 8/8 vs
  4/8; the difference was deaths (19 vs 24, most while retreating). Judge small changes over
  two batches or more seeds, and cut deaths first.

- **Caves:** bots got lost underground (collect log at y 17, surface skills from a mine). The
  memory keeps where we last saw the sky; goto surface walks back; a minute underground with
  nothing gained puts it first.
- **Smelt jobs:** keep a job through an interrupted collect trip (a creeper cost 12 ingots in a
  laptop run); time jobs by the wall clock, not tickCount (resets on respawn).
- **The way down a mine is one way.** Baritone digs straight down and drops up to 3 blocks;
  climbing back needs a pickaxe or blocks to pillar. Empty-handed after a death, a trip back to
  a deep death spot stranded the bot at y 2 (batch 11). Any skill that can fail the same way
  again needs a back-off, or the rules pick it forever.
- **A skill's "ok" must be checked from the world.** retreat said "got away" whenever Baritone
  stopped, even with no path and no step taken (99 times in one run, batch 11).
- **Creepers only light their fuse with line of sight**, so one behind a wall is no threat.
  Perception sees mobs within 6 blocks through walls (so zombies around a corner count).
- **A sticky rung only guarantees what Progress checks.** Iron tools counts as reached with the
  pickaxe alone, so the shield and iron sword on that rung were never made until the portal step
  asked for them (batch 11: zero shields in 8 runs). Check the logs for a feature actually
  running, not just the code for it existing.
- **Running is what killed.** Batch 12 (flee only when outnumbered, creeper reflex at 7) cut
  deaths from 2.6 to 1.1 per run on the same code otherwise. A lone monster is fought.
- **Lava is below y -55, not on the surface.** 0 of 8 natural runs saw a surface pool in batch
  12; surface explores turned back at open water. Cave air that deep is lava.
- **In a fortress, never tunnel.** Anchors inside walls made Baritone dig under the fortress
  (y 41), where no blaze can be seen; stand on floors and bridge tops.
- **Route changes ride alone.** Armor before the portal went into batch 11 with the death fixes,
  doubled the first iron trip to 26 ore, and hid what the death fixes did to iron times.

- **Water refusals need a route out.** In batch 36422415520, collect log refused water 32 times,
  crafting had no room 31 times, shelter had no safe ground 27 times, and explore turned from
  open water 45 times. Two runs spent over 1,000 seconds traveling without stone tools. A shared
  shore step is queued behind `move.shore_first`; the loop must judge whether it improves progress.

- **Close retreat candidate (generations 18-28, not yet raced):** 216 of 537 deaths (40%) were
  during retreat, about 3.3 deaths per 20-minute game; median death around 12 minutes, the first
  night. The reported causes were mobs 50%, skeleton arrows 29%, creepers 10%. g23 tried
  suppressing retreat at two call sites; g15 broadened attack's unreachable-target exception;
  both lost. Gene combat.no_close_retreat instead shares the escape decision across callers:
  shelter when already eligible, otherwise fight within four blocks, with nearby creepers
  taking priority. Combat reflexes can reconsider when a pursuer catches up or a creeper
  approaches. Defaults stay unchanged; the queued paired-seed race must judge the result.

- **Survival candidate (generations 30-37, not yet raced):** 152 of 162 runs died; 87% of deaths
  came from mobs, arrows or creepers, often during retreat, attack or shelter-heal at about 7 health.
  Most deaths were underground. `survival.danger_v2` gives planner, reflexes and retreat one
  terrain-aware verdict; its default stays off until paired-seed results justify promotion.

- **Combat tactics need separate races.** The one-life loop gives no credit for progress after
  death, and generations 30-37 lost 152 of 162 runs. Mob, arrow, and creeper deaths dominated.
  The attack cooldown, footwork, sprint hit, creeper exchange, skeleton approach, and target
  choice now have separate suggestions so their survival effect can be measured. Targeting may
  use our own observed swings as an estimate of prior damage, but never a mob's hidden health.

- **Zombie packs in the combat drill (gens 57-58):** three zombies killed the bot in 19 of 40
  fights and caused 19 of 32 drill deaths; single zombies killed it in 2 of 33. The attack skill
  waits 50 ticks to collect drops after a kill even when another zombie is close. The candidate
  `combat.zombie_pack` keeps space during weapon cooldown, handles a closer zombie only when the
  current target leaves reach, and delays drop collection until the pack is clear. It still needs
  the paired drill race before being judged effective.

## Open problems (next candidates)

- **Food and iron preparation (generations 30-37, not yet raced):** 152 of 162 runs died, with
  food inventory near zero and hunger around 15/20 at the last decision. Health cannot regenerate
  below 18 hunger. The FOOD rung is optional and upkeep only hunts animals already nearby, so the
  bot can enter a mine without meals. Separate genes now race eating below 18, an eight-meal early
  stock, staged iron armor and sword, and a shield after the first iron.

- Portal casting (cast scenario): batch 8a's cast on seed a built, lit and entered a portal (3:13);
  seed b stalled on the scoop (refill fallback added for batch 10). Batch 7 never compiled, so
  nothing was measured there. Lava bucket fills failed once in batch 5 (logging added).
- Cast sight retries: batch 17's natural-f placed obsidian but lost sight of the next frame cell
  six times. A bucket aim checked from the center of a planned stand can be blocked where Baritone
  actually stops. The queued `portal.retry_cast_view` candidate excludes that stand after a failed
  aim and tries another; its effect still needs the paired-seed race.
- Lava is rare on the surface: if the far scan isn't enough, look underground (caves near y -54).
- Smelting 13 iron takes ~130 s of standing still; do nearby work while the furnace runs.
- Deaths: 7 in batch 6's four 15-minute runs (arrows, zombies, lava, fire), several while
  retreating. Night starts at ~10.75 game minutes; a bed or underground work is still missing.
- (2026-09-29, the diamond route) A deep lava lake hardened with water is one layer of obsidian
  over more lava: mined, the block drops into the lava and burns. Obsidian made a block at a time
  in a dug pit (lava bucket in, water poured beside it) lies on solid ground. Lava poured into a
  pit that still has water running into it hardens as it lands: mine it at once.
- (2026-09-29) The fall clutch pours the water bucket; a bucket can be lost that way, so the
  diamond route re-checks its two buckets every step. Deep work shows only stone for minutes
  between diamonds: that must not count as "lost underground" or the bot climbs back up.
