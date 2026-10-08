# Batch 37772803971 @ 2da591b

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:22 | 2:49 | 6:38 | 0 | 19:30 | nether_portal: collect log:9 |
| natural-drill-0-0-drill-0-on-0 | 0:26 | 2:18 | - | mob:zombie, mob:zombie, mob:zombie | 19:30 | iron_tools: collect stone:13 |
| natural-drill-0-1-drill-0-off-1 | 1:07 | 3:59 | - | mob:zombie | 19:30 | iron_tools: shelter |
| natural-drill-0-1-drill-0-on-1 | 0:45 | 2:28 | 5:54 | 0 | 19:30 | nether_portal: attack chicken |
| natural-drill-0-2-drill-0-off-2 | 1:55 | 4:17 | 8:48 | 0 | 19:30 | nether_portal: attack zombie |
| natural-drill-0-2-drill-0-on-2 | 2:00 | 4:02 | - | mob:cave_spider | 19:30 | iron_tools: collect coal:6 |
| **natural: reached, median** | 6/6 0:56 | 6/6 3:24 | 3/6 14:24* | 5 (0.8 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 16:21 | 16:20 | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 13:29 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 15:31 | 15:30 | 8:26 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x mob:zombie (attack zombie 2, retreat 1, shelter heal 1)
- 1 x mob:cave_spider (attack cave_spider 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 581 | 304 | 29 | 56 | 86 | 124 | 0 | 18 | 0 |
| natural-drill-0-0-drill-0-on-0 | 709 | 166 | 28 | 86 | 66 | 0 | 5 | 121 | 0 |
| natural-drill-0-1-drill-0-off-1 | 462 | 53 | 38 | 169 | 22 | 0 | 310 | 139 | 0 |
| natural-drill-0-1-drill-0-on-1 | 398 | 354 | 73 | 127 | 49 | 78 | 0 | 117 | 0 |
| natural-drill-0-2-drill-0-off-2 | 463 | 71 | 11 | 173 | 37 | 0 | 384 | 58 | 0 |
| natural-drill-0-2-drill-0-on-2 | 341 | 31 | 12 | 202 | 61 | 0 | 498 | 48 | 0 |

## Top failures (action + code, count, one example)

- 8 x shore STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 7 x attack creeper HAZARD  (e.g. natural-drill-0-0-drill-0-off-0: no safe gap after hitting the creeper)
- 7 x collect log NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no log seen nearby)
- 6 x explore log ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-off-2: already see log)
- 5 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-on-0: no reachable zombie in sight)
- 3 x attack zombie UNREACHABLE  (e.g. natural-drill-0-0-drill-0-on-0: lost sight of the zombie)
- 3 x collect flint NO_ROOM  (e.g. natural-drill-0-1-drill-0-off-1: no free flat spot beside us for the gravel)
- 3 x attack cave_spider NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no reachable cave_spider in sight)
- 3 x explore cow,pig,sheep,chicken ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-on-2: already see cow)
- 2 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-off-0: couldn't get away (nearest monster 5 blocks))

## Portal cast log, natural-drill-0-0-drill-0-off-0 (last steps)

- bucket: fill water at 599, 62, 126 from 598, 63, 127 hand=bucket accepted=true dist=2.3

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 53, 62, -63 from 56, 62, -63 hand=bucket accepted=true dist=3.4
