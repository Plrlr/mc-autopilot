# Batch 37187946897 @ 393c98e

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:31 | 6:00 | - | 0 | 19:30 | iron_tools: shore |
| natural-drill-0-0-drill-0-on-0 | 1:11 | 3:08 | 12:52 | arrow:skeleton | 19:30 | nether_portal: collect flint:1 |
| natural-drill-0-1-drill-0-off-1 | 0:34 | 3:19 | 7:09 | 0 | 19:30 | nether_portal: fill_bucket water |
| natural-drill-0-1-drill-0-on-1 | 0:32 | 3:05 | 14:47 | 0 | 19:30 | nether_portal: collect raw_iron:4 |
| natural-drill-0-2-drill-0-off-2 | 0:19 | 2:24 | 7:44 | arrow:skeleton, mob:zombie, arrow:skeleton, mob:zombie, mob:zombie, mob:zombie | 19:30 | nether_portal: craft planks:4 |
| natural-drill-0-2-drill-0-on-2 | 2:57 | 4:54 | 17:52 | mob:zombie_villager | 19:30 | nether_portal: retreat |
| **natural: reached, median** | 6/6 0:33 | 6/6 3:13 | 5/6 13:49* | 8 (1.3 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 19:35 | 18:56 | 19:49 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | 1:41 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | 12:37 | 12:20 | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x mob:zombie (retreat 3, attack zombie 1)
- 3 x arrow:skeleton (retreat 2, attack skeleton 1)
- 1 x mob:zombie_villager (attack zombie 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 235 | 34 | 13 | 217 | 0 | 0 | 0 | 696 | 0 |
| natural-drill-0-0-drill-0-on-0 | 579 | 265 | 15 | 71 | 106 | 0 | 130 | 26 | 0 |
| natural-drill-0-1-drill-0-off-1 | 498 | 389 | 64 | 79 | 111 | 20 | 0 | 35 | 0 |
| natural-drill-0-1-drill-0-on-1 | 400 | 497 | 178 | 66 | 35 | 0 | 0 | 21 | 0 |
| natural-drill-0-2-drill-0-off-2 | 530 | 328 | 20 | 63 | 206 | 0 | 25 | 8 | 0 |
| natural-drill-0-2-drill-0-on-2 | 652 | 207 | 69 | 124 | 90 | 0 | 0 | 50 | 0 |

## Top failures (action + code, count, one example)

- 9 x craft torch NO_RECIPE  (e.g. natural-drill-0-0-drill-0-off-0: no known recipe for torch (not unlocked yet?))
- 9 x attack zombie NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no reachable zombie in sight)
- 7 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-off-0: timed out after 90 s)
- 7 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-0-drill-0-on-0: no reachable skeleton in sight)
- 6 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-on-0: couldn't get away (nearest monster 8 blocks))
- 5 x goto surface UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: couldn't find the way up (stairs: water, lava or bedrock on every side at y 56))
- 4 x attack skeleton UNREACHABLE  (e.g. natural-drill-0-0-drill-0-on-0: no sightline to the skeleton)
- 4 x attack creeper HAZARD  (e.g. natural-drill-0-0-drill-0-on-0: no safe gap after hitting the creeper)
- 3 x smelt iron_ingot UNREACHABLE  (e.g. natural-drill-0-0-drill-0-on-0: took too long to reach the furnace)
- 3 x collect flint NO_ROOM  (e.g. natural-drill-0-1-drill-0-off-1: no free flat spot beside us for the gravel)
