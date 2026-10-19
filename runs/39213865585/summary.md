# Batch 39213865585 @ eb6aa85

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:16 | 1:52 | 9:34 | inWall | 19:30 | nether_portal: shore |
| natural-drill-0-0-drill-0-on-0 | 0:16 | 4:05 | 13:40 | onFire, explosion.player:creeper, arrow:skeleton | 19:30 | nether_portal: goto surface |
| natural-drill-0-1-drill-0-off-1 | 0:27 | 3:59 | 6:54 | drown | 19:30 | nether_portal: unstuck |
| natural-drill-0-1-drill-0-on-1 | 0:35 | 3:45 | - | arrow:skeleton, mob:zombie, mob:zombie, arrow:skeleton | 19:30 | iron_tools: smelt iron_ingot:8 |
| natural-drill-0-2-drill-0-off-2 | 1:58 | 2:57 | 9:16 | 0 | 19:30 | nether_portal: shore |
| natural-drill-0-2-drill-0-on-2 | 0:18 | 2:33 | 5:39 | 0 | 19:30 | nether_portal: smelt iron_ingot:3 |
| **natural: reached, median** | 6/6 0:22 | 6/6 3:21 | 5/6 9:25* | 9 (1.5 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | 12:41 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 2:52 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | 4:02 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | 17:28 | 17:27 | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x arrow:skeleton (retreat 2, attack skeleton 1)
- 2 x mob:zombie (attack skeleton 1, attack zombie 1)
- 1 x inWall (shelter 1)
- 1 x onFire (collect flint 1)
- 1 x explosion.player:creeper (retreat 1)
- 1 x drown (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 536 | 61 | 5 | 8 | 53 | 0 | 433 | 97 | 0 |
| natural-drill-0-0-drill-0-on-0 | 574 | 91 | 33 | 126 | 22 | 0 | 182 | 156 | 0 |
| natural-drill-0-1-drill-0-off-1 | 508 | 149 | 36 | 69 | 0 | 0 | 396 | 20 | 0 |
| natural-drill-0-1-drill-0-on-1 | 313 | 211 | 26 | 0 | 198 | 0 | 424 | 13 | 0 |
| natural-drill-0-2-drill-0-off-2 | 535 | 261 | 63 | 141 | 90 | 50 | 35 | 21 | 0 |
| natural-drill-0-2-drill-0-on-2 | 386 | 141 | 63 | 92 | 6 | 0 | 497 | 12 | 0 |

## Top failures (action + code, count, one example)

- 14 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no free flat spot beside us for the gravel)
- 7 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-off-0: couldn't get away (nearest monster 2 blocks))
- 6 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-off-1: no known recipe for torch (not unlocked yet?))
- 6 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-1-drill-0-off-1: already see gravel)
- 5 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-0-drill-0-on-0: no reachable skeleton in sight)
- 4 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 3 x craft iron_pickaxe NO_RECIPE  (e.g. natural-drill-0-0-drill-0-off-0: no known recipe for iron_pickaxe (not unlocked yet?))
- 3 x smelt iron_ingot UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: couldn't reach the furnace)
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-off-2: already see log)

## Portal cast log, natural-drill-0-2-drill-0-off-2 (last steps)

- bucket: fill water at -49, 62, 51 from -48, 62, 49 hand=bucket accepted=true dist=2.1
