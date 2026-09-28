# Batch 36416318494 @ ad6bd5b

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| cast-a-cast1 | - | - | 0:00 | - | 0 | 11:30 | nether_portal: attack zombie |
| cast-b-cast2 | - | - | 0:00 | 3:21 | 0 | 11:30 | blaze_rods: fortress blazes:8 |
| natural-h | 0:16 | 2:18 | 10:15 | - | explosion.player, mob | 19:30 | nether_portal: attack pig |
| natural-i | 0:14 | 10:35 | - | - | mob | 19:30 | iron_tools: collect stone:13 |
| natural-j | 0:32 | 1:37 | 6:25 | - | arrow, lava | 19:30 | nether_portal: collect raw_iron:13 |
| natural-k | 0:16 | 1:32 | 10:20 | - | mob, arrow, mob, mob, mob, explosion.player | 19:30 | nether_portal: retreat |
| **natural: reached, median** | 4/4 0:16 | 4/4 1:57 | 3/4 10:17* | 0/4 - | 11 (2.8 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-cast1 | 0:00 | 0:00 | 0:00 | 1:41 | - | - |
| cast-b-cast2 | 0:00 | 0:00 | 0:02 | 2:56 | 3:13 | 3:14 |
| natural-h | - | - | - | - | - | - |
| natural-i | - | - | - | - | - | - |
| natural-j | - | - | 18:16 | - | - | - |
| natural-k | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 6 x mob (attack zombie 5, retreat 1)
- 2 x explosion.player (retreat 2)
- 2 x arrow (retreat 1, attack skeleton 1)
- 1 x lava (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-cast1 | 200 | 6 | 27 | 234 | 35 | 200 | 12 | 2 | 0 |
| cast-b-cast2 | 0 | 0 | 2 | 97 | 0 | 101 | 0 | 518 | 0 |
| natural-h | 424 | 151 | 137 | 280 | 28 | 0 | 166 | 5 | 0 |
| natural-i | 430 | 27 | 37 | 509 | 25 | 0 | 0 | 165 | 0 |
| natural-j | 513 | 224 | 52 | 192 | 58 | 0 | 62 | 87 | 0 |
| natural-k | 570 | 154 | 5 | 189 | 219 | 0 | 32 | 11 | 0 |

## Top failures (action + code, count, one example)

- 17 x collect log NOT_FOUND  (e.g. natural-h: no log seen nearby)
- 8 x explore log ALREADY_DONE  (e.g. natural-h: already see log)
- 7 x attack zombie NOT_FOUND  (e.g. natural-h: no reachable zombie in sight)
- 7 x collect coal STUCK  (e.g. natural-i: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x craft stone_pickaxe NO_ROOM  (e.g. natural-h: no room to place a crafting_table)
- 6 x collect stone STUCK  (e.g. natural-i: stuck: stayed inside 2.0 blocks for 12 s)
- 4 x build_portal UNREACHABLE  (e.g. cast-a-cast1: lost the line of sight to the frame)
- 4 x build_portal NO_ROOM  (e.g. cast-a-cast1: no flat open ground for a portal near the lava)
- 4 x collect flint STUCK  (e.g. natural-h: stuck: stayed inside 2.0 blocks for 12 s)
- 4 x retreat NO_PROGRESS  (e.g. natural-h: couldn't get away (nearest monster 5 blocks))

## Portal cast log, cast-a-cast1 (last steps)

- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: lava into 18, 68, -4 from 17, 65, -1, water planned at 17, 68, -4
- cast: water at 17, 68, -4 (lava there: lava)
- cast: scooping water at 17, 68, -4 from 17, 65, -1, target is obsidian

## Portal cast log, cast-b-cast2 (last steps)

- bucket: fill lava at 16, 123, -4 from 19, 124, -3 hand=lava_bucket accepted=true dist=3.7
- cast: lava into 23, 128, -1 from 21, 124, -2, water planned at 23, 128, 0
- cast: water at 23, 128, 0 (lava there: lava)
- cast: scooping water at 23, 128, 0 from 21, 124, -2, target is obsidian
- bucket: fill lava at 15, 123, -2 from 18, 124, -1 hand=lava_bucket accepted=true dist=3.6
- cast: lava into 23, 128, 0 from 21, 124, -1, water planned at 23, 128, 1
- cast: water at 23, 128, 1 (lava there: lava)
- cast: scooping water at 23, 128, 1 from 21, 124, -1, target is obsidian
