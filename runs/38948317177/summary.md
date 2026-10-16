# Batch 38948317177 @ 00f1176

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:17 | 1:55 | - | mob:zombie, mob:zombie, mob:zombie, mob:zombie, mob:zombie, mob:zombie, mob:zombie, mob:zombie | 19:30 | iron_tools: collect raw_iron:13 |
| natural-drill-0-0-drill-0-on-0 | 1:00 | 4:34 | 11:18 | lava | 19:30 | nether_portal: shore |
| natural-drill-0-1-drill-0-off-1 | 0:15 | 3:11 | 9:30 | 0 | 19:30 | nether_portal: goto surface |
| natural-drill-0-1-drill-0-on-1 | 1:14 | 3:25 | 7:05 | 0 | 19:30 | nether_portal: explore cow |
| natural-drill-0-2-drill-0-off-2 | 0:15 | 2:29 | 5:43 | 0 | 19:30 | nether_portal: diamond_hunt 3 |
| natural-drill-0-2-drill-0-on-2 | 2:04 | 3:22 | 12:25 | arrow:skeleton | 19:30 | nether_portal: eat |
| **natural: reached, median** | 6/6 0:38 | 6/6 3:16 | 5/6 10:24* | 10 (1.7 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | 10:05 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 17:32 | 17:01 | 7:47 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | 9:59 | 8:34 | 18:46 | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 8 x mob:zombie (attack zombie 6, retreat 2)
- 1 x lava (idle 1)
- 1 x arrow:skeleton (attack skeleton 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 630 | 108 | 10 | 168 | 105 | 0 | 109 | 41 | 0 |
| natural-drill-0-0-drill-0-on-0 | 477 | 172 | 98 | 176 | 18 | 0 | 8 | 236 | 0 |
| natural-drill-0-1-drill-0-off-1 | 450 | 327 | 33 | 181 | 98 | 0 | 6 | 101 | 0 |
| natural-drill-0-1-drill-0-on-1 | 434 | 444 | 78 | 39 | 80 | 59 | 0 | 62 | 0 |
| natural-drill-0-2-drill-0-off-2 | 446 | 198 | 169 | 96 | 44 | 0 | 0 | 244 | 0 |
| natural-drill-0-2-drill-0-on-2 | 392 | 132 | 88 | 164 | 168 | 0 | 251 | 0 | 0 |

## Top failures (action + code, count, one example)

- 11 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 7 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no free flat spot beside us for the gravel)
- 7 x shore STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-off-0: already see gravel)
- 3 x smelt iron_ingot UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: took too long to reach the furnace)
- 3 x attack spider NOT_FOUND  (e.g. natural-drill-0-1-drill-0-on-1: no reachable spider in sight)
- 3 x craft torch NO_RECIPE  (e.g. natural-drill-0-2-drill-0-off-2: no known recipe for torch (not unlocked yet?))
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-on-2: already see log)
- 3 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no reachable skeleton in sight)

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 67, 62, -43 from 67, 62, -46 hand=bucket accepted=true dist=3.5

## Portal cast log, natural-drill-0-2-drill-0-off-2 (last steps)

- bucket: fill water at -78, 62, -187 from -76, 63, -186 hand=bucket accepted=true dist=3.0
