# Batch 39116626152 @ d2e5638

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:25 | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-0-drill-0-on-0 | 0:22 | 8:32 | 12:37 | 0 | 19:30 | nether_portal: smelt iron_ingot:4 |
| natural-drill-0-1-drill-0-off-1 | 1:57 | 3:52 | 17:26 | arrow:skeleton, mob:zombie | 19:30 | nether_portal: collect raw_iron:4 |
| natural-drill-0-1-drill-0-on-1 | 0:22 | 3:28 | 8:03 | arrow:skeleton, mob:zombie, explosion.player:creeper, mob:zombie, spear:zombie | 19:30 | nether_portal: smelt iron_ingot:5 |
| natural-drill-0-2-drill-0-off-2 | 0:17 | 2:34 | 7:23 | arrow:skeleton, mob:zombie, mob:spider, arrow:skeleton | 19:30 | nether_portal: collect stone:13 |
| natural-drill-0-2-drill-0-on-2 | 0:15 | 2:17 | - | fall | 19:30 | iron_tools: smelt iron_ingot:5 |
| **natural: reached, median** | 6/6 0:22 | 5/6 3:40* | 4/6 15:01* | 12 (2.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 11:03 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | 1:38 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | 6:40 | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | 12:19 | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x arrow:skeleton (attack zombie 1, retreat 1, attack skeleton 1, eat 1)
- 4 x mob:zombie (attack zombie 3, attack spider 1)
- 1 x explosion.player:creeper (retreat 1)
- 1 x spear:zombie (attack zombie 1)
- 1 x mob:spider (attack zombie 1)
- 1 x fall (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 125 | 8 | 10 | 135 | 56 | 0 | 0 | 863 | 0 |
| natural-drill-0-0-drill-0-on-0 | 540 | 267 | 19 | 67 | 0 | 0 | 0 | 301 | 0 |
| natural-drill-0-1-drill-0-off-1 | 728 | 209 | 1 | 152 | 87 | 0 | 2 | 12 | 0 |
| natural-drill-0-1-drill-0-on-1 | 415 | 327 | 31 | 0 | 120 | 0 | 284 | 5 | 0 |
| natural-drill-0-2-drill-0-off-2 | 485 | 123 | 14 | 112 | 146 | 0 | 277 | 28 | 0 |
| natural-drill-0-2-drill-0-on-2 | 454 | 154 | 1 | 143 | 66 | 0 | 369 | 6 | 0 |

## Top failures (action + code, count, one example)

- 12 x attack zombie NOT_FOUND  (e.g. natural-drill-0-1-drill-0-off-1: no reachable zombie in sight)
- 11 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-off-0: timed out after 90 s)
- 8 x attack drowned UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: no sightline to the drowned)
- 8 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-1-drill-0-on-1: no reachable skeleton in sight)
- 8 x collect flint NO_ROOM  (e.g. natural-drill-0-2-drill-0-off-2: no free flat spot beside us for the gravel)
- 8 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-off-2: already see gravel)
- 6 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-on-1: no known recipe for torch (not unlocked yet?))
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-1-drill-0-off-1: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-drill-0-1-drill-0-off-1: already see log)
- 3 x attack zombie UNREACHABLE  (e.g. natural-drill-0-2-drill-0-off-2: can't reach the zombie (3 blocks away))
