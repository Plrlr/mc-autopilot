# Route to the Nether and blaze rods (proposal)

Written 2026-09-26 by the laptop session for the cloud session, which owns `plan/` and `skills/`.
Evidence: the local rules run on seed a at `b3bffeb` (`.trials/local-opus-10m/`), docs/batches.md
(8a/8b) and docs/review.md (the reviewer's R1-R8 and L1-L11). Nothing here is implemented or
measured yet. Each step names a check to run before a full batch.

## Where runs stall today

- **Iron is fine; the steps after it aren't.** Batch 8a reached iron tools on 8/8 seeds (median
  6:10), but no natural run has seen lava. The staged cast reached the Nether at 3:13, so casting
  works once lava is known. The missing link is **finding lava**.
- **Caves trap the bot.** Local run, seed a: surface until 2:00, then y 1-7 for iron from 3:00,
  y 11-23 until it died at ~8:30. Once underground, every surface need failed:
  - `collect log:9` at y 17 failed twice, 60 s each (0/9). Baritone logged "unable to find any
    path" to surface logs after 1.48M movements considered.
  - `explore log` from a cave, then death, then `goto death` UNREACHABLE.
  - The trigger was the smelt job being dropped on an interrupt (coordination request 10). The
    planner then wanted 12 more iron plus fuel logs, deep underground.

## Part 1: stop getting lost in caves

In order of value per line of code:

1. **Keep the furnace load** (request 10): drop a smelt job only when its output is collected or
   the furnace is gone, never on INTERRUPTED or DIED. Store `readyAt` in `level.getGameTime()`
   (review R4). This alone would have given seed a its iron tools about a minute later.
2. **Pack for the trip down.** Before the first `collect raw_iron` that starts a descent, the
   planner makes sure the bag holds what the iron rung needs from the surface:
   - planks for the shield (6), sticks (3), the table (4) and smelting fuel (9 planks for 13 iron
     if there's no coal): **about 8 logs**. The first-trip rule currently asks for 2.
   - a crafting table, and 8 cobblestone for a furnace.

   Then nothing in rungs 4 and 7 needs the surface.
3. **Fuel underground is coal, not logs.** Underground, the fuel step should offer `collect coal`
   (an ore, legitMine at the current Y; coal is visible in most caves) before `collect log`. Keep
   planks as the fallback when some are already carried.
4. **Fail fast on surface blocks underground.** `collect log|sand` with the player underground
   (no sky) and no remembered block of that kind → fail NOT_FOUND at once instead of a 60 s
   NO_PROGRESS. This also applies CLAUDE.md's rule that surface blocks come from WorldMemory. Also
   end the skill UNREACHABLE when Baritone reports "Unable to find any path" (a path event).
5. **A way up: `goto surface`.** Remember the top of each descent (the column where the
   GoalYLevel staircase started) as `shaft` in WorldMemory. When a surface-only item is needed
   underground, offer `goto surface`: path back to the remembered shaft top. If there's none, dig
   a 1x2 staircase upward until the sky is visible (fair: a player knows which way is up). One
   option, a short timeout. It also helps night returns and `goto death` from deep deaths.

Check first (task tests, laptop or cloud): `collect log:3` started at y 10 in a cave (needs a
`tp` in a small `cave` scenario). Pass: logs in hand in under 90 s, or NOT_FOUND then
`goto surface`, with no 60 s timeouts.

## Part 2: find lava where it always is

In 1.18+ worlds, cave air below **y -55 is lava** (lava aquifers), so any deep cave there has
open lava pools. Surface pools are rare and batches 1-8 never found one.

1. **When the portal step needs lava and none is known, go deep instead of exploring the
   surface.** Staircase down to y -50 (the same GoalYLevel descent collect already uses), then
   legit branch-mine at about y -54. Lava lights up and is found by the existing close scan
   (sources with air above are remembered). A stone pickaxe mines deepslate, just slowly; the
   iron pickaxe from rung 4 is faster.
2. **Cast the portal down there.** The Nether portal doesn't need the surface. `CastPortal` needs
   a lava source to refill from, water, and a wall site. Two things to check in `CastGeometry`:
   that the site fit accepts a carved stone room (it was written for surface pools), and that
   the water bucket is filled before going down (water is scarce at depth; `fill_bucket water`
   at the surface first).
3. **Mine gold on the way:** gold ore is at y -64 to 32. Four ingots make gold boots, which stop
   piglins from attacking (review L4) at almost no cost; smelt them in the same furnace load.
4. **While at depth, pick up** flint (gravel is common) and 64+ cobblestone/deepslate for Nether
   bridging.

Check first: a staged `deep` scenario (iron gear, 2 buckets, flint and steel, 64 cobblestone, tp
to y -50 in a deepslate cave), goal NETHER_PORTAL, 10 min. Pass: `lava_seen`, then `portal_lit`
and the Nether.

## Part 3: Nether to blaze rods

From docs/review.md L4-L7, reduced to what the first rod needs:

1. **On arrival:** remember the portal (the way back). Gold boots on. Stay on netherrack and
   don't walk into lava: Baritone's lava avoidance plus 64 throwaway blocks for bridges.
2. **See fortresses from afar:** nether bricks are big dark structures visible over long
   distances. Add `nether_bricks` to the far scan (a line-of-sight raycast sample out to ~64
   blocks) in the Nether. Explore in long straight lines into new regions: fortresses and
   bastions share 432-block regions, so stepping by 64 blocks revisits the same region.
3. **Blazes: wait at the spawner, don't chase.** `attack` gives up after 8 s without getting
   closer (L5), and blazes hover. Better: walk to the spawner (remembered as `spawner`), stand
   beside it with the shield in the off hand, and swing only at blazes within 3 blocks. They
   spawn and drift right there. Rods drop 0-1, so 6 rods is about 12 kills. A later option: a bow
   (needs 3 string: spiders or cobwebs).
4. **Deaths in the Nether** respawn the bot in the overworld without its gear (L6). Log them
   apart from overworld deaths so the scoreboard shows which part kills us.

Check first: the reviewer's `fortress` scenario (tp next to a fortress via `locate structure`),
goal BLAZE_RODS, 10 min. Pass: `blaze_rod` ≥ 1 from the inventory; deaths by cause.

## Order of work (one change per batch, per the loop rules)

1. Smelt job fix (Part 1.1): task test, then a batch. Expect m4 on the seeds that lost a load.
2. Pack for the trip plus fail-fast collect (Parts 1.2 and 1.4): the `cave` check, then a batch.
3. `goto surface` (Part 1.5).
4. Go deep for lava (Part 2.1): the `deep` scenario, then a batch. Expect `lava_seen` > 0 in
   natural runs for the first time.
5. The `fortress` scenario for blazes (Part 3), in parallel, since it's staged.
