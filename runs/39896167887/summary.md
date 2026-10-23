# Batch 39896167887 @ 3b76a84

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:24 | 2:05 | 6:17 | onFire | 19:30 | nether_portal: pickup stations |
| natural-drill-0-0-drill-0-on-0 | 0:29 | 2:11 | 6:25 | 0 | 19:30 | nether_portal: explore cow |
| natural-drill-0-1-drill-0-off-1 | 0:15 | 2:39 | 7:04 | fall | 19:30 | nether_portal: collect raw_iron:4 |
| natural-drill-0-1-drill-0-on-1 | 0:14 | 4:30 | 8:05 | 0 | 19:30 | nether_portal: diamond_hunt 3 |
| natural-drill-0-2-drill-0-off-2 | 0:15 | 2:15 | 12:35 | arrow:skeleton | 19:30 | nether_portal: goto surface |
| natural-drill-0-2-drill-0-on-2 | 0:15 | 2:18 | 9:46 | 0 | 19:30 | nether_portal: shelter |
| **natural: reached, median** | 6/6 0:15 | 6/6 2:16 | 6/6 7:34 | 3 (0.5 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 16:23 | 14:23 | 17:46 | - | - | - |
| natural-drill-0-0-drill-0-on-0 | 17:52 | 17:51 | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 4:23 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 13:10 | 13:08 | 4:52 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | 3:39 | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x onFire (goto surface 1)
- 1 x fall (collect raw_iron 1)
- 1 x arrow:skeleton (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 666 | 374 | 25 | 89 | 20 | 0 | 0 | 20 | 0 |
| natural-drill-0-0-drill-0-on-0 | 623 | 323 | 25 | 71 | 36 | 36 | 0 | 83 | 0 |
| natural-drill-0-1-drill-0-off-1 | 800 | 243 | 65 | 0 | 36 | 0 | 0 | 50 | 0 |
| natural-drill-0-1-drill-0-on-1 | 515 | 265 | 148 | 127 | 12 | 56 | 6 | 65 | 0 |
| natural-drill-0-2-drill-0-off-2 | 539 | 152 | 0 | 96 | 95 | 0 | 291 | 20 | 0 |
| natural-drill-0-2-drill-0-on-2 | 485 | 102 | 125 | 44 | 33 | 0 | 390 | 20 | 0 |

## Top failures (action + code, count, one example)

- 11 x collect flint NO_ROOM  (e.g. natural-drill-0-1-drill-0-off-1: no free flat spot beside us for the gravel)
- 5 x shore STUCK  (e.g. natural-drill-0-0-drill-0-on-0: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-on-1: no known recipe for torch (not unlocked yet?))
- 3 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no reachable skeleton in sight)
- 3 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-on-2: already see gravel)
- 2 x explore cow,pig,sheep,chicken STUCK  (e.g. natural-drill-0-0-drill-0-on-0: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x attack sheep NOT_FOUND  (e.g. natural-drill-0-1-drill-0-on-1: no reachable sheep in sight)
- 2 x attack cow UNREACHABLE  (e.g. natural-drill-0-1-drill-0-on-1: lost sight of the cow)
- 2 x craft iron_pickaxe NO_RECIPE  (e.g. natural-drill-0-2-drill-0-on-2: no known recipe for iron_pickaxe (not unlocked yet?))
- 1 x craft wooden_sword NEED_ITEM  (e.g. natural-drill-0-0-drill-0-off-0: no crafting_table nearby or in the inventory)

## Portal cast log, natural-drill-0-0-drill-0-on-0 (last steps)

- bucket: fill water at 598, 62, 202 from 600, 62, 200 hand=bucket accepted=true dist=2.8

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.8
