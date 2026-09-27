# Batch 36314450143 @ 3f41a45

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 0:20 | 2:26 | 10:11 | explosion.player | 20:00 | nether_portal: collect raw_iron:13 |
| natural-b | 0:15 | 0:46 | 7:39 | 0 | 20:00 | nether_portal: explore any |
| natural-c | 0:54 | 1:46 | - | 0 | 20:00 | iron_tools: smelt iron_ingot:13 |
| natural-d | 0:14 | 1:23 | 4:17 | arrow | 20:00 | nether_portal: goto death |
| natural-e | 0:28 | 1:00 | 6:05 | 0 | 20:00 | nether_portal: collect raw_iron:3 |
| natural-f | 0:16 | 1:22 | 6:00 | spear | 20:00 | nether_portal: smelt iron_ingot:13 |
| natural-g | 0:13 | 0:49 | 5:19 | 0 | 20:00 | nether_portal: explore any |
| natural-h | 0:15 | 1:35 | 5:49 | mob | 20:00 | nether_portal: craft stone_pickaxe:1 |
| **natural: reached, median** | 8/8 0:15 | 8/8 1:22 | 7/8 6:02* | 4 (0.5 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a | - | - | - | - | - | - |
| natural-b | - | 15:11 | - | - | - | - |
| natural-c | - | - | - | - | - | - |
| natural-d | 6:08 | 6:01 | - | - | - | - |
| natural-e | 11:29 | 11:28 | - | - | - | - |
| natural-f | 6:05 | 6:04 | 7:40 | 8:31 | - | - |
| natural-g | 6:58 | 5:23 | 9:36 | - | - | - |
| natural-h | 8:01 | 8:00 | 10:33 | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x explosion.player (retreat 1)
- 1 x arrow (retreat 1)
- 1 x spear (retreat 1)
- 1 x mob (attack zombie 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 380 | 78 | 220 | 78 | 97 | 0 | 341 | 0 | 2 |
| natural-b | 349 | 165 | 18 | 422 | 179 | 0 | 64 | 0 | 0 |
| natural-c | 548 | 28 | 57 | 161 | 17 | 0 | 387 | 0 | 0 |
| natural-d | 712 | 100 | 29 | 108 | 245 | 1 | 0 | 0 | 2 |
| natural-e | 401 | 244 | 5 | 436 | 22 | 90 | 0 | 0 | 0 |
| natural-f | 607 | 130 | 5 | 126 | 54 | 120 | 152 | 0 | 2 |
| natural-g | 339 | 131 | 1 | 111 | 16 | 90 | 507 | 0 | 0 |
| natural-h | 386 | 168 | 57 | 13 | 63 | 0 | 506 | 0 | 2 |

## Top failures (action + code, count, one example)

- 6 x goto surface UNREACHABLE  (e.g. natural-b: couldn't find the way up)
- 6 x explore log ALREADY_DONE  (e.g. natural-b: already see log)
- 5 x collect log NOT_FOUND  (e.g. natural-b: no log in sight underground)
- 5 x collect log NO_PROGRESS  (e.g. natural-c: no progress for 60 s (got 1/10))
- 5 x explore cow,pig,sheep,chicken HAZARD  (e.g. natural-e: open water ahead; will turn)
- 5 x build_portal NEED_ITEM  (e.g. natural-g: need a second bucket for lava)
- 4 x attack zombie NOT_FOUND  (e.g. natural-f: no reachable zombie in sight)
- 3 x attack cow NOT_FOUND  (e.g. natural-a: no reachable cow in sight)
- 3 x craft stone_pickaxe NO_ROOM  (e.g. natural-a: no room to place a crafting_table)
- 3 x attack skeleton NOT_FOUND  (e.g. natural-d: no reachable skeleton in sight)

## Portal cast log, natural-d (last steps)

- bucket: fill water at -66, 62, -61 from -65, 63, -58 hand=bucket accepted=true dist=3.7

## Portal cast log, natural-f (last steps)

- cast: scooping water at 42, -48, -153 from 45, -51, -152, target is obsidian
- bucket: fill lava at 53, -55, -150 from 52, -53, -149 hand=lava_bucket accepted=true dist=3.1
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame
- cast: failed: lost the line of sight to the frame

## Portal cast log, natural-g (last steps)

- bucket: fill water at 37, 62, -28 from 39, 63, -29 hand=bucket accepted=true dist=2.9

## Portal cast log, natural-h (last steps)

- bucket: fill water at -369, 62, -136 from -370, 63, -137 hand=bucket accepted=true dist=2.3

## Screenshots (open only if the numbers above point at something visual)

- natural-a: death-1, final, milestone-1, milestone-2, milestone-3
- natural-b: final, milestone-1, milestone-2, milestone-3
- natural-c: final, milestone-1, milestone-2
- natural-d: death-1, final, milestone-1, milestone-2, milestone-3
- natural-e: final, milestone-1, milestone-2, milestone-3
- natural-f: death-1, final, milestone-1, milestone-2, milestone-3
- natural-g: final, milestone-1, milestone-2, milestone-3
- natural-h: death-1, final, milestone-1, milestone-2, milestone-3
