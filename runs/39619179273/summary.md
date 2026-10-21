# Batch 39619179273 @ 94b4063

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m6 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 1:32 | - | - | - | mob:zombie | 19:30 | stone_tools: explore any |
| natural-drill-0-0-drill-0-on-0 | 0:14 | 3:15 | 8:19 | - | 0 | 19:30 | nether_portal: explore cow |
| natural-drill-0-1-drill-0-off-1 | 0:36 | 2:41 | 6:33 | 16:39 | 0 | 19:30 | nether_portal: collect diamond:1:lava |
| natural-drill-0-1-drill-0-on-1 | 0:50 | 1:28 | 9:01 | - | 0 | 19:30 | nether_portal: craft bucket:1 |
| natural-drill-0-2-drill-0-off-2 | 0:16 | 2:04 | - | - | arrow:skeleton, mob:zombie, mob:zombie | 19:30 | iron_tools: shelter |
| natural-drill-0-2-drill-0-on-2 | 0:17 | 2:12 | 3:44 | - | lava | 19:30 | nether_portal: fill_bucket water |
| **natural: reached, median** | 6/6 0:26 | 5/6 2:26* | 4/6 8:40* | 1/6 20:00* | 5 (0.8 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | 13:47 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 11:40 | 11:39 | 2:58 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | 17:44 | 11:44 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | 19:57 | 19:56 | 5:11 | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob:zombie (attack zombie 3)
- 1 x arrow:skeleton (retreat 1)
- 1 x lava (eat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 240 | 105 | 0 | 523 | 6 | 0 | 0 | 308 | 0 |
| natural-drill-0-0-drill-0-on-0 | 444 | 270 | 1 | 362 | 92 | 0 | 0 | 28 | 0 |
| natural-drill-0-1-drill-0-off-1 | 567 | 270 | 63 | 100 | 9 | 30 | 0 | 160 | 0 |
| natural-drill-0-1-drill-0-on-1 | 590 | 411 | 95 | 43 | 24 | 0 | 23 | 12 | 0 |
| natural-drill-0-2-drill-0-off-2 | 487 | 151 | 21 | 59 | 130 | 0 | 338 | 2 | 0 |
| natural-drill-0-2-drill-0-on-2 | 639 | 284 | 8 | 33 | 156 | 1 | 6 | 66 | 0 |

## Top failures (action + code, count, one example)

- 17 x explore any STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 12 x shore STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 10 x shelter NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no safe ground to dig into nearby)
- 8 x explore log ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-off-0: already see log)
- 6 x collect stone STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 5 x collect log NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no log seen nearby)
- 5 x collect log STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 5 x collect flint NO_ROOM  (e.g. natural-drill-0-2-drill-0-off-2: no free flat spot beside us for the gravel)
- 3 x goto death STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-drill-0-1-drill-0-off-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.9
