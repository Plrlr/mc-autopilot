# Batch 36310208987 @ bf754c6

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 0:22 | 1:03 | 19:31 | mob, mob, explosion.player, mob, mob, mob, mob, mob, mob | 20:00 | nether_portal: explore log |
| natural-b | 0:15 | 2:02 | 6:28 | arrow, mob | 20:00 | nether_portal: retreat |
| natural-c | 0:32 | 2:29 | 10:23 | 0 | 20:00 | nether_portal: build_portal |
| natural-d | 0:19 | 0:52 | 3:54 | 0 | 20:00 | nether_portal: explore cow |
| natural-e | 0:23 | 0:57 | 6:08 | 0 | 20:00 | nether_portal: build_portal |
| natural-f | 0:14 | 2:52 | 4:59 | mob, mob | 20:00 | nether_portal: collect log:9 |
| natural-g | 0:13 | 1:50 | 5:43 | mob | 20:00 | nether_portal: goto death |
| natural-h | 0:13 | 0:51 | 4:27 | 0 | 20:00 | nether_portal: explore cow |
| **natural: reached, median** | 8/8 0:17 | 8/8 1:26 | 8/8 5:55 | 14 (1.8 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a | - | - | - | - | - | - |
| natural-b | 10:20 | 9:44 | 11:48 | - | - | - |
| natural-c | 17:03 | 16:10 | 19:39 | - | - | - |
| natural-d | 12:44 | 12:09 | - | - | - | - |
| natural-e | 14:24 | 14:03 | 17:28 | - | - | - |
| natural-f | - | - | - | - | - | - |
| natural-g | 11:06 | 10:36 | - | - | - | - |
| natural-h | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 12 x mob (attack zombie 6, retreat 4, attack spider 2)
- 1 x explosion.player (retreat 1)
- 1 x arrow (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 466 | 135 | 16 | 249 | 285 | 0 | 19 | 0 | 26 |
| natural-b | 477 | 272 | 6 | 215 | 179 | 28 | 13 | 0 | 5 |
| natural-c | 606 | 364 | 98 | 48 | 29 | 51 | 0 | 0 | 0 |
| natural-d | 326 | 341 | 6 | 175 | 50 | 0 | 300 | 0 | 0 |
| natural-e | 570 | 331 | 67 | 20 | 73 | 65 | 70 | 0 | 0 |
| natural-f | 381 | 193 | 19 | 172 | 189 | 0 | 237 | 0 | 5 |
| natural-g | 684 | 305 | 54 | 55 | 88 | 8 | 0 | 0 | 2 |
| natural-h | 366 | 95 | 0 | 448 | 62 | 0 | 228 | 0 | 0 |

## Top failures (action + code, count, one example)

- 19 x attack zombie NOT_FOUND  (e.g. natural-a: no reachable zombie in sight)
- 11 x craft iron_sword NO_ROOM  (e.g. natural-h: no room to place a crafting_table)
- 9 x retreat NO_PROGRESS  (e.g. natural-a: couldn't get away (nearest monster 6 blocks))
- 9 x explore any HAZARD  (e.g. natural-h: open water ahead; will turn)
- 6 x attack spider NOT_FOUND  (e.g. natural-a: no reachable spider in sight)
- 6 x collect log NOT_FOUND  (e.g. natural-a: no log in sight underground)
- 6 x shelter NO_ROOM  (e.g. natural-f: no safe ground to dig into nearby)
- 3 x goto surface UNREACHABLE  (e.g. natural-d: couldn't find the way up)
- 3 x build_portal NO_ROOM  (e.g. natural-e: no flat open ground for a portal near the lava)
- 3 x attack skeleton NOT_FOUND  (e.g. natural-h: no reachable skeleton in sight)

## Portal cast log, natural-b (last steps)

- bucket: fill water at -9, 10, 95 from -10, 11, 92 hand=bucket accepted=true dist=3.7

## Portal cast log, natural-c (last steps)

- bucket: fill water at -111, 62, -65 from -113, 63, -67 hand=bucket accepted=true dist=3.4

## Portal cast log, natural-e (last steps)

- bucket: fill water at -268, 15, -266 from -268, 17, -264 hand=bucket accepted=true dist=3.6

## Portal cast log, natural-g (last steps)

- bucket: fill water at -53, -3, -22 from -51, -2, -24 hand=bucket accepted=true dist=3.4

## Screenshots (open only if the numbers above point at something visual)

- natural-a: death-1, death-2, death-3, death-4, death-5, death-6, death-7, death-8, death-9, final, milestone-1, milestone-2, milestone-3
- natural-b: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-c: final, milestone-1, milestone-2, milestone-3
- natural-d: final, milestone-1, milestone-2, milestone-3
- natural-e: final, milestone-1, milestone-2, milestone-3
- natural-f: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-g: death-1, final, milestone-1, milestone-2, milestone-3
- natural-h: final, milestone-1, milestone-2, milestone-3
