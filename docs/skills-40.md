# The 40 skills: from surviving the first night to killing the dragon

Written 2026-09-28 by Claude from the loop's data (generations 41-46: 120 games, 281 deaths) for
Codex to build. Each skill below is a class in `mod/.../autopilot/skills/`, on the menu in
`Skills.MENU`, with a route spec in `SkillSpecs` (what it needs and gives), behind its own gene,
with a drill that runs it alone. Brain v2's strategist (docs/brain-v2.md) picks between routes
by the learned success rate and time of each spec's key, so an honest spec is how a skill gets used.

## What the data says (why these 40)

| Stage (loop stage starts) | Success | What goes wrong |
|---|---|---|
| spawn → iron kit | 12/40 reach the kit | 93 of 120 games die; median first death at 578 s |
| lava start → portal lit | 1/28 | portal drill: sees lava 26/26, places obsidian 1/26 |
| nether start → fortress / rods | 0/33 | |
| eyes → stronghold | 0/30 | |
| end start → dragon | 0/18 | |

- Deaths (281): mob 140, arrow 72, creeper 30, fall 13, lava 11, fire 5, trident 4.
- **Action just before death: `retreat` 101**, attack:zombie 84, attack:skeleton 29, attack:spider 15.
  Running away with a mob 3-6 blocks behind is the single biggest killer.
- Thrash: `smelt ↔ retreat` every 4-6 s for 100+ s (a mob near the furnace); `explore:cow` ×10 in a row.
- Death spirals: after dying at night the bot respawns bare, fights zombies and dies again (7 deaths
  in 3 minutes in one game). A death also drops the portal kit, so a lava start that dies once
  goes back to punching trees.
- Lava starts wander off to mine diamonds or logs instead of casting, or die in lava 2 s after
  starting `build_portal`.

## Rules for every skill (from AGENTS.md and CLAUDE.md: read them)

1. **One gene per skill**, `skill.<name>` BOOL, default 0 = the old behavior, in `Tune.java`
   (adding genes is the one edit allowed there). Reworks of an existing skill keep the old code
   path when the gene is off.
2. **Register** in `Skills.MENU` with a one-line help, and **add its `SkillSpec`** in
   `SkillSpecs` (stage block, honest priors, its gene). Keys must match what gets logged
   (`SkillSpec.keyOf`). `SkillSpecsTest` checks both.
3. **Planner hooks go in small new classes**, not in Planner.java's body: `plan/SurvivalPlan.java`
   (wave 1), `plan/PortalPlan.java` (wave 2), `NetherPlan.java` (exists; wave 3),
   `plan/StrongholdPlan.java` (wave 4), `plan/EndPlan.java` (wave 5). Planner.java and
   Autopilot.java get one-line calls into them, each behind the skill's gene. Claude is editing
   the planner's `options()` and the brain at the same time: keep your edits there minimal.
4. **Fair play**: only the player's controls; only blocks and mobs in sight (WorldMemory,
   Perception). No commands, no hidden-block reads, no seed tricks. Baritone's legitMine stays on.
   Placing and breaking blocks, buckets, beds exploding in the End: all vanilla, all fine.
5. **Never block the render thread.** A skill's `tick()` does a little work and returns.
6. **Fail with a code** (`Fail`), never loop silently: every wait has a timeout, every retry a cap.
   A skill that can't start says why (NEED_ITEM, NOT_FOUND, NO_ROOM...) at once.
7. **`Facts.report("name")`** when a skill learns something the planner needs (a ruined portal
   seen, the stronghold triangulated, crystals down); `Facts.retract` when it stops being true.
8. **A unit test for every piece of pure logic** (geometry, target choice, state machines fed
   fake inputs), in `mod/src/test/...`. Game-facing code is tested by its drill.
9. **A drill**: a `-PtestTask="<skill> <arg>" -PtestGive="<items>"` line (see
   AutopilotClientTest), or a staged `-PtestScenario` added to `stage()` for skills that need a
   place (a lava pool, a fortress, the End). Test-world commands are allowed in the harness only.
10. Minecraft 26.x ships with Mojang names. Read the Fabric/Baritone sources before using a
    method. Don't guess.

## Wave 1: stay alive (the biggest lever: 93 of 120 games die)

### 1. `kite`: NEW, any stage, gene `skill.kite`
- Why: 101 of 281 deaths came right after `retreat`. A mob within 6 blocks follows a fleeing
  player and hits its back.
- Does: walk *backwards* facing the nearest melee mob, shield up (off hand) while the sword
  recharges, step in and strike when it's in reach and the attack is charged, then back off again.
  It picks a backing direction with solid, lava-free, drop-free ground (check the next 2 blocks
  each tick) and away from other hostiles. It ends when the mob is dead, or when it's more than 12
  blocks away and not following. If the backing path is blocked it fights in place.
- Hook: `Planner.escape` / `escapeChoices` and the reflex's "retreat" choice use `kite` instead
  of `retreat` for zombies, husks, drowned, spiders, piglins and endermen within 6 blocks.
  Retreat stays for creepers (see 4) and when nothing is within 6.
- Spec: `kite` gives fact `threat_cleared`; prior 12 s, 0.7.
- Drill: combat scenario (it measures every fight); `-PtestTask="kite"` with a summoned zombie in
  a new `-PtestScenario=kite` arena if the combat drill doesn't show it clearly.

### 2. `pillar`: NEW, overworld, gene `skill.pillar`
- Why: most mob deaths are zombie-type melee. A player 3 blocks up a pillar can't be reached by
  them and can hit down at their heads.
- Does: look straight down, jump, place a block under the feet at the top of the jump; repeat to
  2-3 blocks. Needs 3+ throwaway blocks, head room (no block within 3 above) and no skeleton in
  sight (skeletons shoot a pillared player). At the top: hit mobs that come in reach (crits
  when falling isn't possible, so plain charged hits), eat if hurt, wait for daylight or for the
  mobs to leave, then dig back down (break the blocks under us one by one, not jump).
- Hook: an escape choice when 2+ melee mobs are within 6 and no skeleton/creeper is in sight;
  night on the surface with no bed and no shelter possible.
- Spec: needs throwaway 3; makes `threat_cleared`; prior 40 s, 0.6.
- Drill: `-PtestTask="pillar" -PtestGive="cobblestone 16"` at night with 2 zombies (new scenario `pillar`).

### 3. `block_arrows`: NEW, any stage, gene `skill.block_arrows`
- Why: 72 arrow deaths. Walking up to a skeleton without a raised shield takes every arrow.
- Does: with a shield: hold use (raise it) facing the skeleton and walk to it; lower it only to
  strike when in reach and charged, then raise again. Without a shield: approach in a zig-zag
  from cover to cover (a block or tree between us and it), or break line of sight and let the
  planner go elsewhere. Never turn our back on a skeleton that can see us.
- Hook: replaces `attack skeleton|stray|bogged` when the gene is on and we hold a shield; the
  no-shield path replaces `retreat` from skeletons.
- Spec: makes `threat_cleared`; prior 20 s, 0.6.
- Drill: the combat drill's skeleton rounds (score: damage taken per skeleton kill).

### 4. `creeper_defuse`: REWORK (creeper handling in CombatSkills/reflex), gene `skill.creeper_defuse`
- Why: 30 `explosion.player` deaths.
- Does: a creeper within 7 blocks: if it's swelling (the fuse is visible: `getSwelling`) or within
  3, sprint straight away (3+ blocks in 1.5 s clears the blast). Else with a sword: sprint-hit
  (knock-back), step back 3, wait for it to come again, hit again. Put a block between us and it
  when cornered. Never mine or eat with a creeper within 5.
- Hook: the reflex's creeper branch (`reflex_creeper`) calls it instead of attack/retreat.
- Spec: makes `threat_cleared`; prior 10 s, 0.7.
- Drill: combat drill creeper rounds.

### 5. `panic_box`: NEW, any stage except the End, gene `skill.panic_box`
- Why: `shelter heal` is underground-only and slow to seal; the bot dies on the surface at 5 hp.
- Does: in under 1.5 s: place blocks on the 4 sides at feet and head height where open, and one
  above the head (8 + 1 blocks; skip sides already solid). Then eat to full and wait until health
  ≥ 16 and no hostile has been in sight for 5 s; open one side facing away from the last threat.
  Creeper in sight: don't box (blast), use `creeper_defuse`.
- Hook: escape choice at low health when outnumbered, on any ground, when 9+ throwaway blocks
  are carried. `Planner.canHide` counts it.
- Spec: needs throwaway 9; makes `threat_cleared`; prior 45 s, 0.7.
- Drill: `-PtestTask="panic_box" -PtestGive="cobblestone 16,cooked_beef 4"` with the player at 6 hp.

### 6. `respawn_reset`: NEW, overworld, gene `skill.respawn_reset`
- Why: death spirals (7 deaths in 3 minutes after one night death).
- Does: right after a respawn: if night, `panic_box` with dirt (dig 9 dirt first; bare hands
  work) or dig 3 down and cover, and wait for day; if day, fast wood tools and a stone sword
  before anything else, then judge the death spot: go back (`recover_items`) only if it's
  within 300 blocks, died less than 4 game minutes ago (items despawn at 5), and it's day.
- Hook: `Autopilot.handleDeath` sets a flag the planner's first option reads (one line).
- Spec: facts `overworld`; makes `safe_restart`; prior 60 s, 0.7.
- Drill: natural runs (deaths per game after the first death is the measure).

### 7. `recover_items`: REWORK of `goto death`, gene `skill.recover_items`
- Why: `goto:death` was chosen 273 times in 120 games and led to 3 deaths; the bot walks back
  into whatever killed it.
- Does: path to 16 blocks from the death spot, stop, look around (Perception): clear or avoid the
  mobs there first (light the spot with torches if it's dark), then walk in and pick up everything
  in 8 blocks; give up past 4:30 game minutes after the death (despawn at 5:00).
- Spec: makes `items_recovered`; prior 90 s, 0.5.
- Drill: natural runs; unit test for the despawn-timer logic.

### 8. `make_bed`: NEW, overworld, gene `skill.make_bed`
- Why: a bed skips the night (most deaths are at night) and moves the respawn point next to
  the work, which ends the long walk back.
- Does: find sheep (Perception/explore), kill 3 of one color (or shear them if we have shears),
  pick up the wool, craft a bed (3 wool of one color + 3 planks) at a table. `make_bed:n` makes
  n beds (the End's bed route needs 5+; see 38).
- Spec: gives bed 1; prior 120 s, 0.5.
- Drill: `-PtestTask="make_bed" -PtestGive="oak_planks 8,crafting_table,iron_sword"` in a
  plains seed with sheep (or a scenario that summons 3 white sheep).

### 9. `food_secure`: REWORK of the food search, gene `skill.food_secure`
- Why: `explore:cow` ran 10 times in a row in some games; food search is open-ended.
- Does: a ladder, stopping at the first that works: animals in sight → animals remembered (add a
  small last-seen animal memory: position and time, in the skill's own class) → sweet berries,
  apples (break oak/dark oak leaves in sight: they drop apples) → fish (skill 10) if a rod is
  carried → bread from hay bales in a village in sight. Cooks what it gets. Ends with food ≥ n.
- Spec: gives cooked 6; prior 150 s, 0.6.
- Drill: natural runs on seeds with few animals; `-PtestTask="food_secure 6"`.

### 10. `fish`: NEW, overworld, gene `skill.fish`
- Why: water is near almost every spawn; fishing is safe food with no hunting.
- Does: craft a rod (3 sticks + 2 string) if string is carried; stand at water 2+ deep, cast, watch
  the bobber (its entity is visible), reel in when it dips, repeat until n fish; cook them.
- Spec: needs fishing_rod 1 (or string 2); facts water_known; gives cooked 4; prior 180 s, 0.6.
- Drill: `-PtestTask="fish 3" -PtestGive="fishing_rod"` at a water scenario.

### 11. `secure_camp`: NEW, overworld, gene `skill.secure_camp`
- Why: the `smelt ↔ retreat` thrash: a mob comes to the furnace, we run, come back, run.
- Does: before long work in one place (smelting, the cast portal, crafting runs) at night or
  underground: torches on the ground every ~5 blocks within 8 blocks (block light ≥ 8 everywhere
  walkable), and a 1-block fence of blocks around the work spot's open sides if mobs are in sight.
- Hook: the planner offers it before `smelt` / `build_portal` when the spot is dark or mobs were
  seen near it in the last minute.
- Spec: needs torch 6; makes `camp_safe`; prior 40 s, 0.8.
- Drill: `-PtestTask="secure_camp" -PtestGive="torch 16,cobblestone 32"` at night.

### 12. `stair_down`: NEW, any stage, gene `skill.stair_down`
- Why: 13 fall deaths and 11 lava deaths; Baritone digs straight down or drops.
- Does: dig a 1-wide staircase down to Y (argument), one step at a time: check the block below
  the next step is solid and not lava (a player looks before stepping), place a block over any
  fluid seen, torch every 8 steps. Used by diamond hunting, lava hunting and the stronghold dig.
- Spec: makes `at_depth`; prior 90 s, 0.8.
- Drill: `-PtestTask="stair_down -50" -PtestGive="stone_pickaxe,cobblestone 32,torch 16"`.

## Wave 2: the portal (1 of 28 lava starts light one)

### 13. `lava_scout`: NEW, overworld, gene `skill.lava_scout`
- Why: the cast fails on pools it shouldn't try (too small, no bank to stand on, next to a drop).
- Does: find lava pools (surface pools and cave lakes in sight), and rate each: number of source
  blocks in sight (≥ 10 for a cast), flat dry bank within 3 blocks, no big drop next to it.
  Remembers the best one and reports `Facts.report("castable_lava")`.
- Spec: makes `castable_lava`; prior 200 s, 0.5.
- Drill: the `cast` scenario (report the rating it gives the staged pool).

### 14. `cast_portal`: REWORK of `CastPortal` as its own skill, gene `skill.cast_portal`
- Why: the portal drill places obsidian in 1 of 26 tries. This is the frontier.
- Does: the speedrunner's mold method, made robust: pick a flat site 3-6 blocks from the pool
  bank; build a mold of throwaway blocks (the wall behind the frame and the cells' sides) so each
  frame cell is a cup whose lava can't flow; for each cell: fetch lava (one bucket), pour it into
  the cell, pour water so it touches that lava source and nothing else, verify the cell is now
  obsidian (look at it), scoop the water back. After each step **verify, and on a failure try the
  next vantage point, then the next cell**. Never stand where lava can flow to our feet. Keep the
  site across retries (a half-built frame is progress). Light it with flint and steel and verify
  the portal block appeared; report `portal_known`.
- Log every step (`CastPortal.log` style) so drills show where it breaks.
- Spec: needs bucket 2, water_bucket 1, flint_and_steel 1, throwaway 40; facts castable_lava (or
  lava_pool); makes portal_known; prior 480 s, 0.15.
- Drill: `-PtestScenario=cast` (exists) and the loop's portal drill. Target: frame complete in
  most cast drills.

### 15. `ruined_portal`: NEW, overworld, gene `skill.ruined_portal`
- Why: ruined portals are common, visible from afar (obsidian, crying obsidian, netherrack, gold
  blocks) and come with a chest (often flint and steel, obsidian, gold) and most of a frame.
- Does: when one is in sight (a block memory group for crying_obsidian/netherrack near
  obsidian), go there, open and loot the chest, then either complete the frame (count the
  missing cells; place carried obsidian or cast the missing ones with `cast_portal`'s per-cell
  routine) or mine its obsidian with a diamond pickaxe. Light it.
- Spec: facts ruined_portal_known; makes portal_known; prior 300 s, 0.3.
- Drill: a staged scenario that places a vanilla ruined portal with `/place structure`
  (test world only).

### 16. `obsidian_pool`: REWORK of the diamond route, gene `skill.obsidian_pool`
- Why: with a diamond pickaxe, hardening a pool and mining 10 obsidian is the classic route.
- Does: stand on a safe bank, pour water to harden the pool's near sources (make_obsidian), mine
  each obsidian block **only after checking what's under it** (lava under obsidian: place a block
  there first or skip it), pick up, repeat to 10. Scoop the water back.
- Spec: needs diamond_pickaxe 1, water_bucket 1; facts lava_pool; gives obsidian 10; prior 300 s, 0.4.
- Drill: `-PtestScenario=portal` (exists).

### 17. `diamond_hunt`: NEW, overworld, gene `skill.diamond_hunt`
- Why: diamonds unlock the classic portal and better armor; the bot finds 3 diamonds only by luck.
- Does: `stair_down` to Y -58, then branch-mine: a 1x2 main tunnel with side tunnels every 3
  blocks (every ore within 1 block of a tunnel wall comes into view: fair), mining only ores in
  sight (SeenMiner). Water bucket ready: a lava block seen ahead gets covered with a block
  before walking on. Ends at n diamonds or the time limit.
- Spec: needs iron_pickaxe 1; gives diamond 3; prior 600 s, 0.35.
- Drill: `-PtestTask="diamond_hunt 3" -PtestGive="iron_pickaxe,cobblestone 64,torch 32,water_bucket"`.

### 18. `portal_repair`: NEW, overworld/nether, gene `skill.portal_repair`
- Why: a frame that's complete but not lit, or a portal a ghast put out, is lost progress.
- Does: compare the known frame against the 4x5 template, fill the gaps (carried obsidian or
  `cast_portal`'s cell routine), light it, verify. Also relights our portal from the Nether side.
- Spec: needs flint_and_steel 1; facts obsidian_known; makes portal_known; prior 60 s, 0.6.

### 19. `stash`: NEW, overworld, gene `skill.stash`
- Why: one death drops the whole kit, and a lava start goes back to wood tools.
- Does: at the base (bed or crafting spot), place a chest and put in the spares: extra iron,
  spare food, a spare bucket, blocks beyond 64; remember it (WorldMemory chest). Keeps what the
  current step needs.
- Spec: makes chest_known; prior 30 s, 0.9.

### 20. `restock`: NEW, overworld, gene `skill.restock`
- Why: the other half of 19: after a death, the spare kit is a walk away.
- Does: walk to our remembered chest, take what the planner's current step needs (argument:
  item list) plus food and blocks.
- Spec: facts chest_known; gives iron_ingot 3 (whatever the chest holds; the strategist uses it
  when the chest is known to hold the item); prior 60 s, 0.8.

## Wave 3: the Nether (0 of 33 nether starts get rods)

### 21. `nether_arrival`: NEW, nether, gene `skill.nether_arrival`
- Does: on arriving: stand still 2 s and sweep the view (NetherSkills.look), remember the
  portal's position (and its overworld pair), then if a ghast is in sight or the portal stands in
  the open: cover the portal's open sides with cobblestone (never break it). Report
  `portal_known` in the Nether.
- Spec: facts in_nether; makes `nether_base`; prior 20 s, 0.9.

### 22. `nether_bridge`: NEW, nether, gene `skill.nether_bridge`
- Why: Nether deaths are lava falls and fire; Baritone's paths cross lava edges.
- Does: `nether_bridge <x> <z>`: walk toward a point; across lava or gaps, sneak-bridge (crouch
  at the edge, place a block against the edge's face, step on) with a 1-block rail on the lava side
  when the lava is next to the path. Never walk on a block with lava beside and below it.
- Spec: needs throwaway 32; makes `crossed`; prior 60 s, 0.7.

### 23. `fortress_scout`: REWORK of `fortress find`, gene `skill.fortress_scout`
- Why: the fortress search walks without a plan and rarely finds one in the time.
- Does: from the portal, pillar up 6-10 blocks (a vantage) and sweep (96-block view rays),
  then walk long legs in a spiral of widening legs (like a search pattern), sweeping each 30 blocks,
  with a budget of blocks out (so it can always get back); bricks seen: go to them and report.
- Spec: facts in_nether; makes fortress_known; prior 360 s, 0.35.
- Drill: a `-PtestScenario=nether` start (exists), 10 minutes: fortress found or not.

### 24. `blaze_farm`: REWORK of `fortress blazes`, gene `skill.blaze_farm`
- Does: at the spawner: build a 1x1 nook next to it (blocks on 3 sides and a roof, open toward
  the spawner), stand in it with the shield up; blazes that come in front get hit (they're in
  melee reach at the opening); fire on us: step back into the nook, eat; health < 10: seal the
  front and heal. Pick up rods. Ends at n rods.
- Spec: facts fortress_known, spawner_known; gives blaze_rod 7; prior 300 s, 0.35.
- Drill: `-PtestScenario=blaze` (exists).

### 25. `ghast_defense`: NEW, nether, gene `skill.ghast_defense`
- Does: a ghast in sight and in the open: break line of sight (step behind a block or place 2
  blocks toward it); with a sword and the gene `combat.deflect`, hit fireballs back. With a bow:
  shoot it.
- Hook: nether reflex for ghasts (none today: they're ignored until the fire lands).
- Spec: makes threat_cleared; prior 15 s, 0.7.

### 26. `gold_armor`: NEW, nether, gene `skill.gold_armor`
- Why: piglins attack a player who wears no gold; bartering needs one gold piece worn.
- Does: mine nether gold ore in sight (any pickaxe; nuggets), craft 9 nuggets → ingot, craft
  golden boots (4 ingots) or a helmet (5), wear it. Mine extra gold for bartering after.
- Spec: facts in_nether; gives golden_boots 1; prior 180 s, 0.6.

### 27. `barter_loop`: REWORK of `Barter`, gene `skill.barter_loop`
- Does: wearing gold, stand 3-4 blocks from an adult piglin (not in a bastion), throw one ingot at
  a time, wait for the trade, pick up drops (pearls, string, obsidian, fire resistance potions),
  count pearls; walk away from hoglins; never hit a piglin. Ends at n pearls or no gold.
- Spec: needs gold_ingot 12, golden_boots 1 (any gold armor); gives ender_pearl 4; prior 240 s, 0.4.
- Drill: `-PtestScenario=barter` (exists).

### 28. `portal_return`: NEW, nether, gene `skill.portal_return`
- Does: path back to the remembered Nether portal (the same bridge rules as 22), enter it. If the
  portal is gone and 10 obsidian are carried, build one here (portal_repair / build).
- Spec: facts in_nether, portal_known; makes overworld; prior 120 s, 0.7.

### 29. `enderman_warped`: NEW, nether, gene `skill.enderman_warped`
- Why: warped forests hold many endermen; a 2-block-high roof keeps them from reaching us.
- Does: in a warped forest (warped nylium in sight): build a 1x2 hut (walls to head height, roof
  at 2 blocks: endermen are 3 tall), look at an enderman to provoke it, hit its legs through the
  gap when it comes. Pick up pearls. Ends at n pearls.
- Spec: facts in_nether; gives ender_pearl 4; prior 300 s, 0.35.
- Drill: a staged warped-forest scenario (`/place biome` or teleport to one found by `/locate`,
  test world only).

## Wave 4: the stronghold (0 of 30 eye starts find it)

### 30. `bow_kit`: NEW, overworld, gene `skill.bow_kit`
- Why: the End's crystals need a bow and arrows; the bot rarely has string or feathers.
- Does: string from spiders (night, or spider in sight; cobwebs in mineshafts with a sword),
  feathers from chickens, flint from gravel; craft a bow (3 string + 3 sticks) and arrows (flint
  + stick + feather = 4) up to n arrows.
- Spec: gives bow 1, arrow 32; prior 400 s, 0.4.

### 31. `eye_triangulate`: REWORK of `locate_stronghold`, gene `skill.eye_triangulate`
- Does: keep the existing triangulation (throw, pick up, sidestep ~60 blocks, throw again, cross
  the bearings), but check the angle between the bearings (> 5°, else sidestep further), estimate
  the error, and report `stronghold_known` with the point. At the point, throw once more: an eye
  that goes down means dig.
- Spec: needs ender_eye 2; facts overworld; makes stronghold_known; prior 300 s, 0.4.

### 32. `dig_to_stronghold`: NEW, overworld, gene `skill.dig_to_stronghold`
- Does: at the triangulated point: `stair_down` until stone bricks / mossy bricks come into view
  (or Y 10), then tunnel toward the bricks in sight. Report `in_stronghold`.
- Spec: facts stronghold_known; makes in_stronghold; prior 240 s, 0.5.

### 33. `stronghold_navigate`: REWORK of `search_stronghold`, gene `skill.stronghold_navigate`
- Does: explore corridors as a graph (rooms and doors seen, visited or not), prefer unvisited
  doorways and stairs going down, recognize the portal room by what's visible (end portal
  frames, the lava pool under them, the silverfish spawner). Report `frame_known`.
- Spec: facts in_stronghold; makes frame_known; prior 300 s, 0.4.

### 34. `silverfish_control`: NEW, stronghold, gene `skill.silverfish_control`
- Does: in the portal room: break the silverfish spawner (it's allowed and a player does it) or
  wall it in; silverfish on us: stand on the frame blocks' edge and hit them; never mine plain
  stone bricks without need (some hide silverfish).
- Spec: facts frame_known; makes room_safe; prior 30 s, 0.8.

### 35. `boat_cross`: NEW, overworld, gene `skill.boat_cross`
- Why: long swims kill (drowned, tridents, drowning) and are slow; a boat is 3x faster.
- Does: craft a boat (5 planks) if none; at open water on the route, place it, ride to the target
  bank (steer: yaw toward the target; the boat is a vehicle the player controls with the keys),
  get out, pick the boat back up.
- Spec: needs boat 1; facts water_known; makes crossed; prior 60 s, 0.7.

## Wave 5: the End (0 of 18 end starts)

### 36. `end_landing`: NEW, end, gene `skill.end_landing`
- Does: arrival on the obsidian platform: don't move until the island is in view; build a bridge
  of blocks toward the main island (sneak-bridge, a rail on both sides), or pillar up if the
  platform is below the island's edge. Look at the ground, not at endermen, all the way.
- Spec: needs throwaway 48; facts in_end; makes on_island; prior 60 s, 0.7.

### 37. `crystal_hunt`: REWORK of `shoot end_crystal`, gene `skill.crystal_hunt`
- Does: go round the pillars: shoot every crystal in sight (the existing ballistic aim); for a
  caged crystal (iron bars around it): pillar up beside that pillar to just below the top, break
  a bar, and shoot the crystal from 4+ blocks (never hit it in melee: 6 blocks of blast).
  Remembers which pillars are done. Report `crystals_down`.
- Spec: needs bow 1, arrow 24, throwaway 64; facts on_island; makes crystals_down; prior 300 s, 0.35.

### 38. `bed_bomb`: NEW, end, gene `skill.bed_bomb`
- Why: beds explode in the End; five or six bed blasts at the perched dragon's head are how
  most speedruns kill it. Vanilla mechanics, fair.
- Does: when the dragon perches on the fountain: stand by its head with a block between us and
  the bed's spot (the blast hurts us too), place a bed where the head is, use it (right-click) →
  explosion; repeat while it perches. Keep 16 hp or more between beds.
- Spec: needs bed 5; facts in_end; makes dragon_dead; prior 240 s, 0.3.

### 39. `dragon_strike`: REWORK of `DragonFight`, gene `skill.dragon_strike`
- Does: the existing fight, improved: approach the perched dragon's head along the side that
  isn't in the breath, charged hits (and crits with the gene), back out of the breath cloud;
  during the charge phase: shield up facing it; never within 3 blocks of an edge.
- Spec: facts in_end; makes dragon_dead; prior 900 s, 0.1.

### 40. `end_guard`: NEW, end, gene `skill.end_guard` (a reflex, like clutch)
- Why: in the End the bot can be knocked into the void or aggro every enderman by looking at it.
- Does: an always-on reflex in the End: (a) keep the view pitched down at the ground whenever an
  enderman is within 32 and would be in the crosshair; (b) knocked off an edge (no ground below
  within 12): place a block under us if in reach, else throw a pearl at the nearest island block
  in sight; (c) an angry enderman: stand under a 2-high roof (build 3 blocks) or fight with the
  shield up.
- Spec: makes `threat_cleared`; prior 5 s, 0.8.

## Order of work

Wave 1 → 5, one commit per skill (`skill <n>: <name>: <what it does>`), `./gradlew test` passing
at every commit. After each wave: push, update the PR's description with a table (skill, gene,
suggestion queued, drill command, what the drill showed if you could run it), and queue each
skill's gene as a suggestion at the **end** of `scripts/loop/settings.json`'s `suggest` list
(`{"skill.kite": 1}`); Claude orders the queue.
