# Batch 37641755636 @ 330002f

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:14 | 1:59 | 8:43 | 0 | 19:30 | nether_portal: shelter |
| natural-drill-0-0-drill-0-on-0 | 0:37 | 6:17 | - | 0 | 19:30 | iron_tools: goto surface |
| natural-drill-0-1-drill-0-off-1 | 0:27 | 2:55 | 5:40 | drown | 19:30 | nether_portal: collect raw_iron:13 |
| natural-drill-0-1-drill-0-on-1 | 0:15 | 3:13 | 6:45 | 0 | 19:30 | nether_portal: diamond_hunt 1 |
| natural-drill-0-2-drill-0-off-2 | 0:19 | 2:22 | 6:28 | 0 | 19:30 | nether_portal: collect flint:1 |
| natural-drill-0-2-drill-0-on-2 | 0:14 | 2:07 | 13:25 | arrow:skeleton, arrow:skeleton | 19:30 | nether_portal: collect flint:1 |
| **natural: reached, median** | 6/6 0:17 | 6/6 2:38 | 5/6 7:44* | 3 (0.5 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | 5:52 | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | 7:06 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 13:50 | 13:11 | 1:30 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 14:04 | 13:39 | 3:25 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 2 x arrow:skeleton (attack skeleton 2)
- 1 x drown (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 412 | 94 | 10 | 236 | 60 | 0 | 385 | 0 | 0 |
| natural-drill-0-0-drill-0-on-0 | 292 | 22 | 44 | 220 | 4 | 0 | 515 | 99 | 0 |
| natural-drill-0-1-drill-0-off-1 | 570 | 316 | 30 | 72 | 54 | 90 | 0 | 45 | 0 |
| natural-drill-0-1-drill-0-on-1 | 376 | 408 | 99 | 79 | 31 | 34 | 5 | 165 | 0 |
| natural-drill-0-2-drill-0-off-2 | 506 | 121 | 212 | 215 | 99 | 0 | 44 | 0 | 0 |
| natural-drill-0-2-drill-0-on-2 | 598 | 168 | 6 | 288 | 118 | 0 | 0 | 12 | 0 |

## Top failures (action + code, count, one example)

- 10 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no free flat spot beside us for the gravel)
- 9 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-off-0: already see gravel)
- 9 x craft torch NO_RECIPE  (e.g. natural-drill-0-0-drill-0-on-0: no known recipe for torch (not unlocked yet?))
- 6 x attack creeper HAZARD  (e.g. natural-drill-0-1-drill-0-off-1: no safe gap after hitting the creeper)
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no log seen nearby)
- 3 x retreat NO_PROGRESS  (e.g. natural-drill-0-1-drill-0-off-1: couldn't get away (nearest monster 2 blocks))
- 2 x goto surface UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: couldn't find the way up (stairs: couldn't fill the step))
- 2 x attack sheep NOT_FOUND  (e.g. natural-drill-0-0-drill-0-on-0: no reachable sheep in sight)
- 2 x collect log NO_PROGRESS  (e.g. natural-drill-0-2-drill-0-off-2: found only 5 log in sight)
- 2 x sleep USE_FAILED  (e.g. natural-drill-0-2-drill-0-off-2: couldn't sleep (monsters nearby?))

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.8
