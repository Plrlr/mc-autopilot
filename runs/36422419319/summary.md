# Batch 36422419319 @ aa4701f

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-p1 | 0:37 | 1:14 | - | arrow, mob | 19:30 | iron_tools: collect stone:13 |
| natural-p2 | 0:14 | 1:01 | 3:30 | arrow | 19:30 | nether_portal: goto death |
| natural-p3 | 0:17 | 0:59 | 3:47 | 0 | 19:30 | nether_portal: collect diamond:1 |
| natural-p4 | 0:19 | 0:57 | 5:54 | 0 | 19:30 | nether_portal: explore cow |
| natural-p5 | 0:46 | 1:28 | 6:30 | 0 | 19:30 | nether_portal: eat |
| natural-p6 | 0:20 | 1:10 | - | mob, mob, mob | 19:30 | iron_tools: collect log:3 |
| **natural: reached, median** | 6/6 0:19 | 6/6 1:05 | 4/6 6:12* | 6 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-p1 | - | - | - | - | - | - |
| natural-p2 | - | - | 7:31 | - | - | - |
| natural-p3 | 10:37 | 9:49 | - | - | - | - |
| natural-p4 | - | 18:05 | - | - | - | - |
| natural-p5 | - | - | - | - | - | - |
| natural-p6 | - | - | 8:24 | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x mob (retreat 2, attack spider 1, attack husk 1)
- 2 x arrow (attack skeleton 2)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-p1 | 197 | 293 | 271 | 226 | 21 | 0 | 152 | 30 | 0 |
| natural-p2 | 400 | 265 | 0 | 462 | 27 | 0 | 0 | 40 | 0 |
| natural-p3 | 484 | 335 | 18 | 315 | 26 | 15 | 0 | 4 | 0 |
| natural-p4 | 569 | 232 | 2 | 332 | 30 | 0 | 0 | 33 | 0 |
| natural-p5 | 429 | 160 | 87 | 151 | 15 | 0 | 239 | 107 | 0 |
| natural-p6 | 371 | 107 | 3 | 461 | 151 | 0 | 90 | 7 | 0 |

## Top failures (action + code, count, one example)

- 16 x collect log NOT_FOUND  (e.g. natural-p1: no log in sight underground)
- 16 x retreat NO_PROGRESS  (e.g. natural-p4: couldn't get away (nearest monster 7 blocks))
- 11 x collect log STUCK  (e.g. natural-p1: stuck: stayed inside 2.0 blocks for 12 s)
- 9 x attack skeleton NOT_FOUND  (e.g. natural-p1: no reachable skeleton in sight)
- 6 x collect raw_iron STUCK  (e.g. natural-p1: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x explore log ALREADY_DONE  (e.g. natural-p1: already see log)
- 6 x attack zombie NOT_FOUND  (e.g. natural-p1: no reachable zombie in sight)
- 5 x collect flint STUCK  (e.g. natural-p2: stuck: stayed inside 2.0 blocks for 12 s)
- 4 x collect stone STUCK  (e.g. natural-p1: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x goto surface STUCK  (e.g. natural-p1: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-p3 (last steps)

- bucket: fill water at -2, 62, 5 from 0, 64, 4 hand=bucket accepted=true dist=3.6
