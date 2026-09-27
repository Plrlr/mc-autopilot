# Batch 36307176816 @ 4b5bacb

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| cast-a | - | - | 0:00 | 0 | 20:00 | nether_portal: build_portal |
| cast-b | - | - | 0:00 | 0 | 20:00 | nether_portal: explore lava |
| natural-a | 0:20 | 1:01 | 3:43 | 0 | 20:00 | nether_portal: explore water |
| natural-b | 0:13 | 0:48 | 6:36 | mob, mob, explosion.player, mob | 20:00 | nether_portal: collect raw_iron:1 |
| natural-c | 0:28 | 1:00 | 14:16 | mob | 20:00 | nether_portal: explore any |
| natural-d | 0:13 | 1:34 | 4:40 | mob, arrow | 20:00 | nether_portal: goto death |
| natural-e | 0:24 | 0:57 | 5:16 | 0 | 20:00 | nether_portal: explore cow |
| natural-f | 0:14 | 0:50 | 5:56 | mob, drown | 20:00 | nether_portal: smelt iron_ingot:5 |
| natural-g | 0:46 | 1:31 | 5:24 | mob, arrow | 20:00 | nether_portal: explore any |
| natural-h | 0:14 | 0:43 | 5:39 | 0 | 20:00 | nether_portal: retreat |
| **natural: reached, median** | 8/8 0:17 | 8/8 0:58 | 8/8 5:31 | 11 (1.4 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a | 0:00 | 0:00 | 0:00 | 1:42 | - | - |
| cast-b | 0:00 | 0:00 | 0:00 | 4:56 | - | - |
| natural-a | 11:21 | 11:21 | - | - | - | - |
| natural-b | - | - | - | - | - | - |
| natural-c | - | - | 7:13 | - | - | - |
| natural-d | 9:23 | 12:41 | - | - | - | - |
| natural-e | 10:26 | 10:14 | - | - | - | - |
| natural-f | - | - | - | - | - | - |
| natural-g | - | - | - | - | - | - |
| natural-h | 14:38 | 14:37 | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 7 x mob (attack zombie 4, attack zombie_villager 1, shelter heal 1, attack spider 1)
- 2 x arrow (attack skeleton 1, retreat 1)
- 1 x explosion.player (retreat 1)
- 1 x drown (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a | 177 | 7 | 50 | 505 | 22 | 424 | 12 | 0 | 0 |
| cast-b | 20 | 0 | 5 | 396 | 4 | 288 | 484 | 0 | 0 |
| natural-a | 294 | 327 | 5 | 53 | 57 | 0 | 461 | 0 | 0 |
| natural-b | 636 | 273 | 22 | 121 | 128 | 0 | 5 | 0 | 11 |
| natural-c | 660 | 59 | 0 | 187 | 48 | 0 | 240 | 0 | 2 |
| natural-d | 472 | 265 | 24 | 265 | 131 | 21 | 13 | 0 | 5 |
| natural-e | 350 | 261 | 0 | 518 | 12 | 58 | 0 | 0 | 0 |
| natural-f | 337 | 269 | 7 | 433 | 132 | 0 | 3 | 0 | 15 |
| natural-g | 264 | 179 | 88 | 181 | 151 | 0 | 328 | 0 | 5 |
| natural-h | 379 | 346 | 37 | 74 | 98 | 0 | 263 | 0 | 0 |

## Top failures (action + code, count, one example)

- 14 x shelter NO_ROOM  (e.g. natural-e: no safe ground to dig into nearby)
- 12 x explore any HAZARD  (e.g. cast-a: open water ahead; will turn)
- 11 x attack zombie NOT_FOUND  (e.g. natural-b: no reachable zombie in sight)
- 10 x fill_bucket water NOT_FOUND  (e.g. natural-a: no known water source with a bank to stand on)
- 9 x explore lava HAZARD  (e.g. natural-e: open water ahead; will turn)
- 8 x build_portal UNREACHABLE  (e.g. cast-a: lost the line of sight to the frame)
- 8 x collect log NOT_FOUND  (e.g. natural-a: no log in sight underground)
- 8 x attack skeleton NOT_FOUND  (e.g. natural-d: no reachable skeleton in sight)
- 7 x collect raw_iron NOT_FOUND  (e.g. natural-c: can't find any raw_iron nearby)
- 6 x attack spider NOT_FOUND  (e.g. natural-b: no reachable spider in sight)

## Portal cast log, cast-a (last steps)

- bucket: fill water at -571, 69, -541 from -573, 70, -539 hand=water_bucket accepted=true dist=3.2
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- bucket: fill lava at -593, 71, -528 from -591, 72, -530 hand=lava_bucket accepted=true dist=3.7

## Portal cast log, cast-b (last steps)

- cast: failed: lost the line of sight to the frame
- cast: lava into 4, 126, 5 from 8, 124, 6, water planned at 5, 126, 5
- cast: water at 5, 126, 5 (lava there: lava)
- cast: scooping water at 5, 126, 5 from 8, 124, 6, target is obsidian
- bucket: fill lava at 12, 123, 5 from 9, 124, 6 hand=lava_bucket accepted=true dist=3.8
- cast: lava into 4, 125, 5 from 8, 124, 6, water planned at 5, 125, 5
- cast: water at 5, 125, 5 (lava there: lava)
- cast: scooping water at 5, 125, 5 from 8, 124, 6, target is obsidian

## Portal cast log, natural-d (last steps)

- bucket: fill water at 111, 62, -47 from 114, 63, -47 hand=bucket accepted=true dist=3.3

## Portal cast log, natural-e (last steps)

- bucket: fill water at -214, 62, -263 from -216, 63, -265 hand=bucket accepted=true dist=3.5

## Screenshots (open only if the numbers above point at something visual)

- cast-a: final, milestone-1
- cast-b: final, milestone-1
- natural-a: final, milestone-1, milestone-2, milestone-3
- natural-b: death-1, death-2, death-3, death-4, final, milestone-1, milestone-2, milestone-3
- natural-c: death-1, final, milestone-1, milestone-2, milestone-3
- natural-d: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-e: final, milestone-1, milestone-2, milestone-3
- natural-f: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-g: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-h: final, milestone-1, milestone-2, milestone-3
