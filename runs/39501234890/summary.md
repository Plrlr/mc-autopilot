# Batch 39501234890 @ eb243fd

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m6 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:26 | - | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-0-drill-0-on-0 | 0:58 | 4:45 | 9:47 | - | 0 | 19:30 | nether_portal: collect raw_iron:2 |
| natural-drill-0-1-drill-0-off-1 | 0:38 | 3:17 | 11:04 | - | 0 | 19:30 | nether_portal: collect raw_iron:4 |
| natural-drill-0-1-drill-0-on-1 | 0:56 | 3:36 | 7:51 | 16:29 | 0 | 19:30 | nether_portal: obsidian_pool |
| natural-drill-0-2-drill-0-off-2 | 0:16 | 2:10 | 9:21 | - | arrow:skeleton | 19:30 | nether_portal: retreat |
| natural-drill-0-2-drill-0-on-2 | 0:15 | 2:16 | 6:43 | - | 0 | 19:30 | nether_portal: collect coal:6 |
| **natural: reached, median** | 6/6 0:32 | 5/6 3:26* | 5/6 9:34* | 1/6 20:00* | 1 (0.2 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 4:23 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 12:58 | 12:57 | 3:59 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x arrow:skeleton (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 54 | 8 | 10 | 216 | 161 | 0 | 0 | 747 | 0 |
| natural-drill-0-0-drill-0-on-0 | 468 | 128 | 1 | 128 | 62 | 0 | 400 | 9 | 0 |
| natural-drill-0-1-drill-0-off-1 | 438 | 378 | 82 | 164 | 55 | 0 | 10 | 70 | 0 |
| natural-drill-0-1-drill-0-on-1 | 649 | 245 | 85 | 0 | 13 | 23 | 0 | 183 | 0 |
| natural-drill-0-2-drill-0-off-2 | 609 | 261 | 28 | 130 | 160 | 0 | 0 | 8 | 0 |
| natural-drill-0-2-drill-0-on-2 | 412 | 135 | 35 | 112 | 68 | 0 | 436 | 0 | 0 |

## Top failures (action + code, count, one example)

- 18 x attack drowned UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: can't reach the drowned (3 blocks away))
- 12 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-on-0: no free flat spot beside us for the gravel)
- 7 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-off-0: timed out after 90 s)
- 6 x craft torch NO_RECIPE  (e.g. natural-drill-0-0-drill-0-on-0: no known recipe for torch (not unlocked yet?))
- 6 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-1-drill-0-off-1: already see gravel)
- 3 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-on-0: couldn't get away (nearest monster 2 blocks))
- 3 x attack zombie UNREACHABLE  (e.g. natural-drill-0-2-drill-0-on-2: lost sight of the zombie)
- 2 x shelter STUCK  (e.g. natural-drill-0-0-drill-0-on-0: it's morning, but couldn't climb out: couldn't fill the step)
- 2 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no reachable skeleton in sight)
- 1 x collect stone STUCK  (e.g. natural-drill-0-0-drill-0-on-0: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.8
- bucket: fill lava at 96, -55, 14 from 95, -54, 14 hand=lava_bucket accepted=true dist=2.2
- bucket: fill lava at 97, -55, 14 from 94, -55, 14 hand=lava_bucket accepted=true dist=3.0
