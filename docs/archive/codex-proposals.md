# Next skill proposals from loop generations 20–29

This is a read-only diagnosis of the last ten **completed** generations at
`origin/trial-results` (`feaef3b`, generation 29). It covers 160 runs: 150 evaluation
runs and 10 data runs. The 100 fresh-spawn runs include those 10 data runs; the
other starts are 30 portal-kit, 12 Nether, 12 eyes, and 6 End starts. Paired
runs reuse seeds, so a run count is not a count of independent worlds.

I used `loop/history.jsonl` and `loop/state.json`, the 160 compressed decision
files, and their retained raw trial logs. Compressed rows give the planner's
last *decision*, which can differ from the skill actually running at death.
The raw `skill_start`, `skill_end`, and `death` events give the failure and
death-time counts below. Failures exclude ordinary `INTERRUPTED` and `DIED`
skill endings. Checkpoints are counted once per run.

## Where progress stops

| Fresh-spawn step reached at least once | Runs / 100 |
|---|---:|
| Iron pickaxe milestone (m4) | 83 |
| Flint and steel | 53 |
| Two buckets | 47 |
| Lava seen | 42 |
| Obsidian placed in a cast frame | 4 |
| Frame complete | 1 |
| Portal lit | 1 |

These are separate “ever reached” counts, not a single funnel of the same
runs. Median first iron-pickaxe milestone was 388 game seconds; median first
two-bucket and lava-seen checkpoints were 599 and 579 seconds among runs
reaching each. The kit starts already had buckets and flint and steel: 22/30
saw lava, 5/30 placed obsidian, and 1/30 lit a portal. Across *all* 160
starts, 9 placed obsidian, 2 completed a frame, and 8 recorded portal lit;
6 of those 8 were Nether-stage starts with an existing portal. The history's
reported 3 lit portals in 454 total games through generation 28 mixes
starting stages. In these ten generations only one fresh-spawn run lit one.

At the end of the 100 fresh-spawn runs, 82 still had `nether_portal` as their
goal, 15 `iron_tools`, two `stone_tools`, and one `blaze_rods`. The most
common *last planner decisions* were `collect:raw_iron` (18),
`explore:any` (9), `smelt:iron_ingot` (8), `collect:stone` (8),
`collect:log` (7), and `goto:surface` (7). The raw end-of-run skill
sometimes differs from those decisions. Across all 160 runs, confirmed
repeated failures included `fill_bucket water NOT_FOUND` (163 in 19 runs),
`build_portal NO_ROOM` (51 in 16), and `explore any UNREACHABLE`
(1,250 in 5). The last count is concentrated in four runs with 121, 160,
298, and 670 failures, rather than spread across the population.

## Deaths outside retreat

There were 477 deaths in these 160 runs. Retreat accounted for 174 and is
already being addressed. Of the remaining 303, causes were mobs 158,
arrows 91, falls 16, lava 9, fire 8, spear 5, trident 5, explosions 4,
indirect magic 4, and drowning 3. The skill active at death was
`attack zombie` 121 times (100 mob, 13 arrow), `attack skeleton` 55
(44 arrow), `attack drowned` 22, and `attack spider` 15. Arrow deaths
while attacking a skeleton are a clear combat cost, but the current attack
already raises an off-hand shield between swings when equipped, and
`combat.shield_guard`, `gear.sword_early`, `combat.crits`,
`combat.backstep`, and armor-route genes already cover the main tunable
responses. In the last logged decision before the 44 arrow deaths during
`attack skeleton`, 36 showed no shield in inventory; that is a proxy for
equipment at death, not proof of the immediate cause. I would race those
genes and assess the retreat PR before proposing a new combat skill.

## Ranked skill fixes

### 1. Make water collection and search agree

**Evidence.** `fill_bucket water` failed `NOT_FOUND` 163 times in 19
evaluation runs (13 paired generation/seed tasks), always reporting no
known source with a bank to stand on. `explore water` failed
`ALREADY_DONE` 149 times in 11 runs, saying it already saw water; ten
runs had both failures. The portal-kit starts contributed 116 water
`NOT_FOUND` and 108 `ALREADY_DONE` failures. One kit run in generation
23 repeated each 39 times and ended with no lava checkpoint. The loop's
`insights` calls out this ocean-bank refusal loop.

**Player fix.** When the remembered source has no usable bank, find a
*visible* shore or reachable dry ledge, move there, then scoop a visible
source within reach. Remember a source/stand pair that failed so the
planner does not immediately hand the same unusable water back to
`explore water`; search a different shore after a bounded attempt.
Keep lava-adjacent standing rules. This is distinct from losing g21,
which only tried scooping a nearby source from the current position
while swimming or in shallows; its paired race lost by 0.65 over ten
seeds.

**Gene:** `portal.water_shore_fallback` (BOOL, default 0).
**Rough size:** medium, about 100–180 lines across bucket selection
and the water-search handoff, plus a focused scenario test.

### 2. Prepare a usable portal work area beside a lava pool

**Evidence.** `build_portal` failed 80 times in 25 runs; 51 were
`NO_ROOM` in 16 runs (14 paired tasks). Of those, 44 said there was no
flat open ground near lava. Another 13 failures in 9 runs said the
lava bucket had no source with a bank. Only 4/100 fresh-spawn runs and
5/30 kit starts placed even one obsidian block. Lighting is not the
observed bottleneck: both runs that completed a frame also lit it.
One successful fresh-spawn run in generation 23 had three earlier
`NO_ROOM` failures before casting.

**Player fix.** Pick a safe visible edge of the pool, move a few blocks
back from the lava, and build a short cobblestone standing pad and
backstop for the frame before pouring. Check reach and line of sight
from the actual standing cell; retry a different visible edge after a
bounded failure. Use placed blocks and ordinary player controls only.
This targets the lack of a workable site rather than simply widening
the carve search: losing g19 already tried nearby carve origins within
four blocks and lost by 0.60 over ten seeds.

**Gene:** `portal.prepare_work_area` (BOOL, default 0).
**Rough size:** large, about 150–250 lines in the cast skill and
geometry helpers, with a cast scenario check.

### 3. Recover from an exploration direction that immediately fails

**Evidence.** `explore any` logged 1,250 `UNREACHABLE` failures in
five runs (four paired tasks). Four runs accounted for 1,249 failures;
all four ended still trying `explore any`. Generation 24's
`eval-g20-2` had 670 such failures, reached only stone tools, and
finished without a death. The skill's failure occurs after about
1.5 seconds when Baritone stops before moving 16 blocks. The normal
stuck detector waits 12 seconds, so changing `stuck.window_s` cannot
catch this rapid retry loop.

**Player fix.** Remember the local position and failed heading.
After a few immediate refusals, choose a visible nearby safe waypoint
that actually changes position, then replan the longer exploration
route. If no safe step exists, stop offering the same direction until
the local situation changes. Do not send Baritone back to the identical
unreachable goal every decision.

**Gene:** `nav.dead_end_recovery` (BOOL, default 0).
**Rough size:** medium, about 80–140 lines in exploration and local
retry memory, with a small no-progress regression test.

### 4. Reserve one bucket for lava during portal casting

**Evidence.** Generation 28's `eval-g25-3` carried **two water
buckets** (confirmed in its inventory log) and repeatedly selected
`build_portal`. That skill ended `NEED_ITEM` seven times in that one
run: it had no empty bucket for lava or second lava bucket. The
two-bucket checkpoint and `Goal.have("bucket")` count filled buckets,
while the cast skill needs one water bucket *and* an empty or lava
bucket. This is a narrow, directly observed case.

**Player fix.** Maintain separate water and lava bucket roles. Before
casting, if both buckets contain water, empty one safely onto a
visible recoverable spot away from lava and retain the other. Check
the actual bucket states when offering `build_portal` so a failed
precondition cannot be selected again immediately.

**Gene:** `portal.reserve_lava_bucket` (BOOL, default 0).
**Rough size:** small, about 40–80 lines across planner and cast skill,
with a bucket-state test. Lower rank because this appeared in one
paired task.

## Problems already covered by genes or prior races

- Combat and night deaths: `combat.flee_hp`, `combat.outnumbered`,
  `combat.wall_in_anywhere`, `combat.shield_guard`,
  `gear.sword_early`, `combat.crits`, `combat.backstep`,
  `nav.mob_avoid_coef`, `nav.mob_avoid_radius`, `cave.torches`,
  `night.wall_in`, and `night.shelter_armor`. Generation 28
  promoted `cave.torches=1`. Retreat has its own pending
  `combat.no_close_retreat` suggestion.
- The 271 `collect log STUCK`, 208 `collect stone STUCK`,
  and 126 `collect raw_iron STUCK` failures are widespread, but
  `stuck.window_s`, `stuck.box`, `focus.commit`, and gathering
  stock genes already affect the behavior. g17 tried inventory
  progress as an exception and lost its race. Tune before another
  mining-skill proposal.
- Lava discovery has `route.deep_for_lava` and
  `route.diamond_portal`; neither solves a failed water scoop or
  an unusable frame site. For drowned `UNREACHABLE` loops, g25 is
  still a contender in generation 29. Avoid duplicating that patch.
