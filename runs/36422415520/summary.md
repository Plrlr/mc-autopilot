# Batch 36422415520 @ b3a940d

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-p1 | 0:23 | - | - | 0 | 19:30 | stone_tools: explore any |
| natural-p2 | 0:15 | 1:06 | 10:57 | explosion.player, fall | 19:30 | nether_portal: explore cow |
| natural-p3 | 0:19 | 4:08 | - | arrow, onFire, lava, mob | 19:30 | iron_tools: explore log |
| natural-p4 | 1:38 | - | - | 0 | 19:30 | stone_tools: idle |
| natural-p5 | 0:17 | 1:09 | 6:49 | 0 | 19:30 | nether_portal: collect flint:1 |
| natural-p6 | - | - | - | 0 | 19:30 | wood_tools: explore log |
| **natural: reached, median** | 5/6 0:21* | 3/6 12:04* | 2/6 20:00* | 6 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-p1 | - | - | - | - | - | - |
| natural-p2 | - | - | - | - | - | - |
| natural-p3 | - | - | 6:04 | - | - | - |
| natural-p4 | - | - | - | - | - | - |
| natural-p5 | - | - | - | - | - | - |
| natural-p6 | - | - | 3:04 | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x explosion.player (retreat 1)
- 1 x fall (retreat 1)
- 1 x arrow (attack skeleton 1)
- 1 x onFire (collect stone 1)
- 1 x lava (idle 1)
- 1 x mob (attack spider 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-p1 | 143 | 10 | 16 | 1019 | 8 | 0 | 0 | 0 | 0 |
| natural-p2 | 621 | 72 | 5 | 264 | 230 | 0 | 0 | 0 | 0 |
| natural-p3 | 525 | 37 | 6 | 296 | 83 | 0 | 193 | 36 | 0 |
| natural-p4 | 973 | 7 | 0 | 72 | 0 | 0 | 0 | 0 | 115 |
| natural-p5 | 996 | 64 | 28 | 101 | 0 | 0 | 0 | 0 | 0 |
| natural-p6 | 22 | 0 | 2 | 1056 | 117 | 0 | 0 | 0 | 0 |

## Top failures (action + code, count, one example)

- 32 x collect log WRONG_PLACE  (e.g. natural-p3: in water: log is dug from dry ground)
- 31 x craft stone_pickaxe NO_ROOM  (e.g. natural-p1: no room to place a crafting_table)
- 27 x shelter NO_ROOM  (e.g. natural-p1: no safe ground to dig into nearby)
- 23 x explore any HAZARD  (e.g. natural-p1: open water ahead; will turn)
- 19 x collect log NOT_FOUND  (e.g. natural-p3: no log seen nearby)
- 17 x explore log HAZARD  (e.g. natural-p3: open water ahead; will turn)
- 10 x retreat NO_PROGRESS  (e.g. natural-p2: couldn't get away (nearest monster 10 blocks))
- 8 x explore log ALREADY_DONE  (e.g. natural-p3: already see log)
- 5 x explore cow,pig,sheep,chicken HAZARD  (e.g. natural-p6: open water ahead; will turn)
- 4 x goto surface UNREACHABLE  (e.g. natural-p2: couldn't find the way up)
