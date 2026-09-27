# Batch 36307180105 @ 500d621

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m7 | m8 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|---|
| blaze-a-1 | - | - | - | 0:00 | 0:59 | onFire | 5:00 | blaze_rods: collect stone:13 |
| blaze-a-2 | - | - | - | 0:00 | 0:08 | onFire | 5:00 | blaze_rods: collect raw_iron:13 |
| blaze-a-3 | - | - | - | 0:00 | 0:38 | 0 | 5:00 | ender_pearls: fortress blazes:8 |
| deep-a | - | - | 0:01 | - | - | 0 | 10:00 | nether_portal: explore any |
| natural-a | 0:22 | 1:05 | 3:47 | - | - | mob, arrow | 20:00 | nether_portal: collect log:10 |
| natural-b | 0:13 | 0:49 | 7:34 | - | - | 0 | 20:00 | nether_portal: explore any |
| natural-c | 1:19 | 1:55 | 7:58 | - | - | 0 | 20:00 | nether_portal: attack skeleton |
| natural-d | 0:18 | 0:46 | 9:11 | - | - | arrow, mob, mob, mob | 20:00 | nether_portal: collect log:9 |
| natural-e | 0:28 | 1:00 | 4:19 | - | - | 0 | 20:00 | nether_portal: explore any |
| natural-f | 0:14 | 0:47 | 5:14 | - | - | 0 | 20:00 | nether_portal: collect log:9 |
| natural-g | 0:16 | 1:56 | 6:39 | - | - | 0 | 20:00 | nether_portal: retreat |
| natural-h | 0:12 | 5:10 | - | - | - | mob | 20:00 | iron_tools: collect log:9 |
| nether-a-1 | - | - | - | 0:00 | - | lava, mob, arrow, explosion.player | 10:00 | nether_portal: collect log:3 |
| nether-a-2 | - | - | - | 0:00 | - | 0 | 10:00 | blaze_rods: explore any |
| **natural: reached, median** | 8/8 0:17 | 8/8 1:02 | 7/8 7:06* | 0/8 - | 0/8 - | 7 (0.9 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| blaze-a-1 | - | 0:00 | - | - | - | - |
| blaze-a-2 | - | 0:00 | - | - | - | - |
| blaze-a-3 | - | 0:00 | - | - | - | - |
| deep-a | 0:01 | 0:01 | 0:50 | - | - | - |
| natural-a | - | - | - | - | - | - |
| natural-b | 13:42 | 12:30 | 15:12 | - | - | - |
| natural-c | 14:25 | 13:47 | 15:40 | - | - | - |
| natural-d | - | 12:42 | - | - | - | - |
| natural-e | 11:37 | 11:25 | 14:56 | - | - | - |
| natural-f | - | - | - | - | - | - |
| natural-g | 19:35 | 19:33 | - | - | - | - |
| natural-h | - | - | - | - | - | - |
| nether-a-1 | - | 0:00 | 0:27 | - | - | - |
| nether-a-2 | - | 0:00 | 0:28 | - | - | - |

## Deaths by cause (what the bot was doing)

- 6 x mob (attack zombie 5, retreat 1)
- 3 x arrow (attack skeleton 2, collect raw_iron 1)
- 2 x onFire (fortress blazes 2)
- 1 x lava (idle 1)
- 1 x explosion.player (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| blaze-a-1 | 19 | 7 | 10 | 0 | 0 | 0 | 0 | 260 | 2 |
| blaze-a-2 | 138 | 11 | 13 | 0 | 7 | 0 | 0 | 125 | 2 |
| blaze-a-3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 300 | 0 |
| deep-a | 59 | 0 | 5 | 251 | 105 | 177 | 0 | 0 | 0 |
| natural-a | 623 | 190 | 34 | 231 | 99 | 0 | 14 | 0 | 5 |
| natural-b | 383 | 314 | 36 | 116 | 93 | 25 | 230 | 0 | 0 |
| natural-c | 572 | 245 | 63 | 115 | 43 | 36 | 122 | 0 | 0 |
| natural-d | 319 | 287 | 56 | 353 | 138 | 0 | 32 | 0 | 11 |
| natural-e | 535 | 240 | 18 | 58 | 15 | 86 | 245 | 0 | 0 |
| natural-f | 327 | 129 | 0 | 239 | 0 | 0 | 503 | 0 | 0 |
| natural-g | 337 | 432 | 42 | 157 | 209 | 18 | 2 | 0 | 0 |
| natural-h | 253 | 29 | 43 | 789 | 77 | 0 | 3 | 0 | 2 |
| nether-a-1 | 180 | 43 | 1 | 140 | 129 | 0 | 0 | 88 | 15 |
| nether-a-2 | 0 | 0 | 11 | 142 | 33 | 0 | 0 | 411 | 1 |

## Top failures (action + code, count, one example)

- 27 x craft stone_pickaxe NO_ROOM  (e.g. natural-h: no room to place a crafting_table)
- 26 x build_portal NO_ROOM  (e.g. deep-a: no flat open ground for a portal near the lava)
- 18 x collect log NOT_FOUND  (e.g. natural-a: no log in sight underground)
- 18 x shelter NO_ROOM  (e.g. natural-c: no safe ground to dig into nearby)
- 18 x explore any HAZARD  (e.g. natural-h: open water ahead; will turn)
- 12 x explore log ALREADY_DONE  (e.g. natural-a: already see log)
- 9 x attack zombie NOT_FOUND  (e.g. natural-a: no reachable zombie in sight)
- 7 x retreat NO_PROGRESS  (e.g. natural-a: couldn't get away (nearest monster 5 blocks))
- 7 x fortress blazes NOT_FOUND  (e.g. nether-a-2: no blazes in the parts of the fortress we know)
- 4 x attack zombie UNREACHABLE  (e.g. natural-b: can't reach the zombie (7 blocks away))

## Portal cast log, natural-b (last steps)

- bucket: fill water at 120, 37, -73 from 119, 39, -74 hand=bucket accepted=true dist=3.1

## Portal cast log, natural-c (last steps)

- bucket: fill water at -141, 19, -21 from -143, 20, -23 hand=bucket accepted=true dist=3.4

## Portal cast log, natural-e (last steps)

- bucket: fill water at -227, 62, -247 from -227, 63, -250 hand=bucket accepted=true dist=3.5

## Screenshots (open only if the numbers above point at something visual)

- blaze-a-1: death-1, final, milestone-1, milestone-2
- blaze-a-2: death-1, final, milestone-1, milestone-2
- blaze-a-3: final, milestone-1, milestone-2
- deep-a: final, milestone-1
- natural-a: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-b: final, milestone-1, milestone-2, milestone-3
- natural-c: final, milestone-1, milestone-2, milestone-3
- natural-d: death-1, death-2, death-3, death-4, final, milestone-1, milestone-2, milestone-3
- natural-e: final, milestone-1, milestone-2, milestone-3
- natural-f: final, milestone-1, milestone-2, milestone-3
- natural-g: final, milestone-1, milestone-2, milestone-3
- natural-h: death-1, final, milestone-1, milestone-2
- nether-a-1: death-1, death-2, death-3, death-4, final, milestone-1
- nether-a-2: final, milestone-1
