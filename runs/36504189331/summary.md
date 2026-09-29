# Batch 36504189331 @ 580cac9

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| cast-a-w2cast | - | - | 0:00 | magic, explosion.player, mob, mob | 11:30 | nether_portal: retreat |
| cast-b-w2cast | - | - | 0:00 | 0 | 11:30 | nether_portal: shelter |
| combat-a-w1combat | - | 0:01 | - | mob | ? | iron_tools: collect coal:6 |
| natural-c-w1nat | 3:44 | 4:37 | - | 0 | 14:30 | iron_tools: creeper_defuse |
| **natural: reached, median** | 1/1 3:44 | 1/1 4:37 | 0/1 - | 0 (0.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-w2cast | 0:00 | 0:00 | 0:00 | 1:56 | - | - |
| cast-b-w2cast | 0:00 | 0:00 | 0:00 | 5:09 | - | - |
| combat-a-w1combat | - | - | - | - | - | - |
| natural-c-w1nat | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob (retreat 2, attack zombie 1)
- 1 x magic (shelter heal 1)
- 1 x explosion.player (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-w2cast | 155 | 7 | 74 | 158 | 148 | 9 | 3 | 153 | 0 |
| cast-b-w2cast | 0 | 0 | 12 | 296 | 0 | 12 | 230 | 166 | 0 |
| combat-a-w1combat | 159 | 0 | 0 | 17 | 198 | 0 | 0 | 102 | 0 |
| natural-c-w1nat | 147 | 13 | 33 | 147 | 0 | 0 | 0 | 559 | 0 |

## Top failures (action + code, count, one example)

- 21 x creeper_defuse TIMEOUT  (e.g. natural-c-w1nat: timed out after 25 s)
- 7 x portal_repair NO_PROGRESS  (e.g. cast-b-w2cast: no lightable frame)
- 6 x block_arrows NOT_FOUND  (e.g. combat-a-w1combat: no skeleton in sight)
- 5 x cast_portal NO_ROOM  (e.g. cast-a-w2cast: no site near the lava can be dug out safely)
- 5 x retreat NO_PROGRESS  (e.g. cast-a-w2cast: couldn't get away (nearest monster 4 blocks))
- 3 x attack skeleton NOT_FOUND  (e.g. cast-a-w2cast: no reachable skeleton in sight)
- 3 x collect log NOT_FOUND  (e.g. natural-c-w1nat: no log seen nearby)
- 2 x attack spider NOT_FOUND  (e.g. cast-a-w2cast: no reachable spider in sight)
- 2 x explore log HAZARD  (e.g. natural-c-w1nat: open water ahead; will turn)
- 2 x collect log WRONG_PLACE  (e.g. natural-c-w1nat: in water: log is dug from dry ground)

## Portal cast log, cast-a-w2cast (last steps)

- cast: fetching lava for 11, 67, -2
- bucket: fill lava at 5, 65, 3 from 8, 65, 1 hand=lava_bucket accepted=true dist=3.8
- cast: cast 11, 67, -2 from 12, 65, -2, water at 11, 67, -3
- cast: lava into 11, 67, -2 from 12, 65, -2, water planned at 11, 67, -3
- cast: water at 11, 67, -3 (lava there: lava)
- cast: scooping water at 11, 67, -3 from 12, 65, -2, target is obsidian
- cast: scooping water at 11, 67, -3 from 12, 65, -1, target is obsidian
- bucket: fill water at 11, 67, -3 from 11, 69, -2 hand=water_bucket accepted=true dist=2.9

## Portal cast log, cast-b-w2cast (last steps)

- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
- bucket: fill lava at 14, 123, -1 from 16, 124, -1 hand=bucket accepted=false dist=3.0
