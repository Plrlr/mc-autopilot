# Batch 38131915979 @ 04923ab

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:18 | 1:51 | 15:09 | onFire, onFire | 19:30 | nether_portal: shore |
| natural-drill-0-0-drill-0-on-0 | 0:21 | 1:56 | 6:46 | explosion.player:creeper, arrow:skeleton | 19:30 | nether_portal: explore any |
| natural-drill-0-1-drill-0-off-1 | 0:17 | 4:15 | 7:47 | 0 | 19:30 | nether_portal: retreat |
| natural-drill-0-1-drill-0-on-1 | 1:01 | 4:28 | - | mob:zombie, fall | 19:30 | iron_tools: collect stone:13 |
| natural-drill-0-2-drill-0-off-2 | 0:16 | 2:06 | 4:30 | explosion.player:creeper | 19:30 | nether_portal: attack sheep |
| natural-drill-0-2-drill-0-on-2 | 3:02 | 4:52 | 9:44 | 0 | 19:30 | nether_portal: shelter |
| **natural: reached, median** | 6/6 0:19 | 6/6 3:10 | 5/6 8:45* | 7 (1.2 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | 5:18 | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 13:51 | 13:25 | 4:47 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | 5:30 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | 15:06 | 14:16 | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | 0:32 | - | - | - |

## Deaths by cause (what the bot was doing)

- 2 x onFire (shore 1, craft stone_axe 1)
- 2 x explosion.player:creeper (retreat 2)
- 1 x arrow:skeleton (retreat 1)
- 1 x mob:zombie (attack zombie 1)
- 1 x fall (goto surface 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 694 | 164 | 23 | 85 | 38 | 0 | 0 | 187 | 0 |
| natural-drill-0-0-drill-0-on-0 | 257 | 173 | 15 | 277 | 156 | 0 | 50 | 236 | 2 |
| natural-drill-0-1-drill-0-off-1 | 458 | 378 | 81 | 68 | 131 | 61 | 0 | 20 | 0 |
| natural-drill-0-1-drill-0-on-1 | 765 | 37 | 38 | 46 | 42 | 0 | 105 | 158 | 0 |
| natural-drill-0-2-drill-0-off-2 | 570 | 242 | 51 | 226 | 74 | 26 | 0 | 6 | 0 |
| natural-drill-0-2-drill-0-on-2 | 382 | 37 | 111 | 110 | 22 | 0 | 534 | 1 | 0 |

## Top failures (action + code, count, one example)

- 14 x attack creeper HAZARD  (e.g. natural-drill-0-0-drill-0-on-0: no safe gap after hitting the creeper)
- 8 x shore STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 8 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-off-0: couldn't get away (nearest monster 10 blocks))
- 7 x craft wooden_pickaxe UNREACHABLE  (e.g. natural-drill-0-0-drill-0-on-0: couldn't reach the crafting_table)
- 6 x attack zombie UNREACHABLE  (e.g. natural-drill-0-0-drill-0-on-0: lost sight of the zombie)
- 5 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-on-0: no free flat spot beside us for the gravel)
- 5 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-on-0: no reachable zombie in sight)
- 3 x collect raw_iron STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-on-0: already see gravel)
- 3 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-off-1: no known recipe for torch (not unlocked yet?))

## Portal cast log, natural-drill-0-1-drill-0-off-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.8

## Portal cast log, natural-drill-0-2-drill-0-off-2 (last steps)

- bucket: fill water at -50, 22, -151 from -49, 22, -151 hand=bucket accepted=true dist=1.3
