# Batch 38491849826 @ 73d270e

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:41 | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-0-drill-0-on-0 | 0:16 | 3:07 | 9:51 | 0 | 19:30 | nether_portal: shore |
| natural-drill-0-1-drill-0-off-1 | 0:26 | 3:40 | 6:02 | 0 | 19:30 | nether_portal: fill_bucket water |
| natural-drill-0-1-drill-0-on-1 | 0:34 | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-2-drill-0-off-2 | 0:21 | 2:20 | 10:11 | 0 | 19:30 | nether_portal: goto surface |
| natural-drill-0-2-drill-0-on-2 | 0:16 | 2:10 | 6:07 | arrow:skeleton | 19:30 | nether_portal: goto death |
| **natural: reached, median** | 6/6 0:23 | 4/6 3:23* | 4/6 10:01* | 1 (0.2 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 19:34 | 19:33 | - | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | 13:44 | 13:18 | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x arrow:skeleton (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 213 | 8 | 25 | 148 | 0 | 0 | 0 | 803 | 0 |
| natural-drill-0-0-drill-0-on-0 | 456 | 102 | 0 | 34 | 25 | 0 | 0 | 472 | 80 |
| natural-drill-0-1-drill-0-off-1 | 427 | 454 | 37 | 213 | 45 | 22 | 0 | 1 | 0 |
| natural-drill-0-1-drill-0-on-1 | 41 | 8 | 21 | 274 | 22 | 0 | 0 | 826 | 0 |
| natural-drill-0-2-drill-0-off-2 | 426 | 126 | 60 | 229 | 39 | 0 | 320 | 0 | 0 |
| natural-drill-0-2-drill-0-on-2 | 542 | 195 | 36 | 148 | 111 | 59 | 99 | 4 | 0 |

## Top failures (action + code, count, one example)

- 16 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-off-0: timed out after 90 s)
- 10 x collect flint NO_ROOM  (e.g. natural-drill-0-1-drill-0-off-1: no free flat spot beside us for the gravel)
- 6 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-off-2: already see gravel)
- 4 x retreat NO_PROGRESS  (e.g. natural-drill-0-1-drill-0-off-1: couldn't get away (nearest monster 3 blocks))
- 3 x smelt iron_ingot UNREACHABLE  (e.g. natural-drill-0-0-drill-0-on-0: took too long to reach the furnace)
- 3 x attack cow NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no reachable cow in sight)
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no log seen nearby)
- 2 x goto surface UNREACHABLE  (e.g. natural-drill-0-1-drill-0-on-1: couldn't find the way up (stairs: water, lava or bedrock on every side at y 57))
- 2 x attack skeleton UNREACHABLE  (e.g. natural-drill-0-1-drill-0-on-1: lost sight of the skeleton)
- 2 x attack pig UNREACHABLE  (e.g. natural-drill-0-2-drill-0-on-2: no sightline to the pig)

## Portal cast log, natural-drill-0-2-drill-0-on-2 (last steps)

- bucket: fill water at -61, 62, -189 from -60, 64, -187 hand=bucket accepted=true dist=3.6
