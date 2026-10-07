# Batch 37557525393 @ ec39832

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 1:10 | 2:17 | - | mob:zombie, mob:zombie, mob:spider, mob:zombie, mob:zombie, mob:zombie, mob:zombie, mob:zombie, mob:zombie | 19:30 | iron_tools: shore |
| natural-drill-0-0-drill-0-on-0 | 0:15 | 3:31 | 8:48 | 0 | 19:30 | nether_portal: collect log:9 |
| natural-drill-0-1-drill-0-off-1 | 0:56 | 7:33 | 16:57 | 0 | 19:30 | nether_portal: explore any |
| natural-drill-0-1-drill-0-on-1 | 0:31 | 3:00 | 7:00 | arrow:skeleton, arrow:skeleton, mob:zombie | 19:30 | nether_portal: shore |
| natural-drill-0-2-drill-0-off-2 | 0:29 | 2:23 | 6:25 | 0 | 19:30 | nether_portal: shelter |
| natural-drill-0-2-drill-0-on-2 | 0:15 | 2:08 | 5:49 | arrow:skeleton | 19:30 | nether_portal: goto surface |
| **natural: reached, median** | 6/6 0:30 | 6/6 2:41 | 5/6 7:54* | 13 (2.2 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | 8:24 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 9:16 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 12:22 | 12:21 | - | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 9 x mob:zombie (attack zombie 6, retreat 2, attack spider 1)
- 3 x arrow:skeleton (attack skeleton 2, retreat 1)
- 1 x mob:spider (attack sheep 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 476 | 109 | 17 | 88 | 174 | 0 | 6 | 297 | 0 |
| natural-drill-0-0-drill-0-on-0 | 359 | 143 | 4 | 116 | 41 | 0 | 516 | 16 | 0 |
| natural-drill-0-1-drill-0-off-1 | 319 | 173 | 50 | 158 | 84 | 0 | 110 | 287 | 0 |
| natural-drill-0-1-drill-0-on-1 | 537 | 255 | 53 | 65 | 114 | 54 | 7 | 95 | 0 |
| natural-drill-0-2-drill-0-off-2 | 437 | 144 | 29 | 84 | 23 | 0 | 468 | 12 | 0 |
| natural-drill-0-2-drill-0-on-2 | 533 | 145 | 5 | 88 | 80 | 0 | 344 | 0 | 0 |

## Top failures (action + code, count, one example)

- 12 x attack creeper HAZARD  (e.g. natural-drill-0-0-drill-0-off-0: no safe gap after hitting the creeper)
- 10 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-on-0: no free flat spot beside us for the gravel)
- 8 x shore STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 8 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 8 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-on-0: already see gravel)
- 7 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-off-0: couldn't get away (nearest monster 7 blocks))
- 6 x explore any UNREACHABLE  (e.g. natural-drill-0-1-drill-0-off-1: couldn't make headway that way; will turn)
- 4 x sleep USE_FAILED  (e.g. natural-drill-0-1-drill-0-off-1: couldn't sleep (monsters nearby?))
- 4 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-1-drill-0-on-1: no reachable skeleton in sight)
- 3 x craft stone_pickaxe NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no room to place a crafting_table)

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 53, 62, -57 from 55, 62, -59 hand=bucket accepted=true dist=3.5
