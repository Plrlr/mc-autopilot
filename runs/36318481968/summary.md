# Batch 36318481968 @ 4716296

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 0:24 | 1:05 | 4:40 | explosion.player, arrow, explosion.player | 20:00 | nether_portal: collect log:3 |
| natural-b | 0:14 | 1:23 | 5:12 | mob, arrow | 20:00 | nether_portal: collect raw_iron:5 |
| natural-c | 0:31 | 2:05 | - | explosion.player, mob, arrow | 20:00 | iron_tools: collect raw_iron:13 |
| natural-d | 0:14 | 1:32 | 5:40 | arrow | 20:00 | nether_portal: fill_bucket water |
| natural-e | 0:22 | 0:56 | 8:25 | 0 | 20:00 | nether_portal: collect log:9 |
| natural-f | 0:13 | 1:35 | 5:54 | lava | 20:00 | nether_portal: pickup stations |
| natural-g | 0:18 | 1:29 | 6:42 | 0 | 20:00 | nether_portal: explore any |
| natural-h | 0:21 | 2:01 | 8:34 | 0 | 20:00 | nether_portal: explore cow |
| **natural: reached, median** | 8/8 0:19 | 8/8 1:30 | 7/8 6:18* | 10 (1.2 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a | - | - | - | - | - | - |
| natural-b | - | - | - | - | - | - |
| natural-c | - | - | - | - | - | - |
| natural-d | 17:07 | 17:06 | 19:12 | - | - | - |
| natural-e | - | - | - | - | - | - |
| natural-f | 10:40 | 10:39 | 13:16 | 13:47 | - | - |
| natural-g | 12:02 | 12:01 | 15:58 | - | - | - |
| natural-h | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x arrow (attack skeleton 2, retreat 1, pickup stations 1)
- 3 x explosion.player (retreat 2, attack zombie_villager 1)
- 2 x mob (attack zombie 2)
- 1 x lava (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 489 | 255 | 3 | 192 | 249 | 0 | 0 | 0 | 8 |
| natural-b | 676 | 288 | 12 | 81 | 134 | 0 | 0 | 0 | 5 |
| natural-c | 471 | 279 | 78 | 168 | 140 | 0 | 52 | 0 | 8 |
| natural-d | 437 | 396 | 5 | 121 | 175 | 61 | 0 | 0 | 2 |
| natural-e | 348 | 277 | 0 | 178 | 0 | 0 | 395 | 0 | 0 |
| natural-f | 643 | 322 | 0 | 60 | 33 | 133 | 0 | 0 | 6 |
| natural-g | 346 | 341 | 62 | 123 | 87 | 130 | 107 | 0 | 0 |
| natural-h | 300 | 53 | 18 | 208 | 117 | 0 | 500 | 0 | 0 |

## Top failures (action + code, count, one example)

- 13 x collect log NOT_FOUND  (e.g. natural-a: no log in sight underground)
- 9 x attack zombie NOT_FOUND  (e.g. natural-b: no reachable zombie in sight)
- 7 x build_portal NEED_ITEM  (e.g. natural-g: need a second bucket for lava)
- 6 x explore log ALREADY_DONE  (e.g. natural-a: already see log)
- 6 x retreat NO_PROGRESS  (e.g. natural-a: couldn't get away (nearest monster 5 blocks))
- 6 x shelter NO_ROOM  (e.g. natural-a: no safe ground to dig into nearby)
- 6 x attack zombie UNREACHABLE  (e.g. natural-a: can't reach the zombie (4 blocks away))
- 5 x attack skeleton NOT_FOUND  (e.g. natural-b: no reachable skeleton in sight)
- 3 x craft stone_pickaxe NO_ROOM  (e.g. natural-h: no room to place a crafting_table)
- 2 x smelt iron_ingot UNREACHABLE  (e.g. natural-a: couldn't reach the furnace)

## Portal cast log, natural-f (last steps)

- bucket: fill lava at 55, -55, -150 from 53, -54, -149 hand=lava_bucket accepted=true dist=2.6
- cast: lava into 58, -49, -154 from 57, -51, -151, water planned at 59, -49, -154
- cast: water at 59, -49, -154 (lava there: lava)
- cast: scooping water at 59, -49, -154 from 57, -51, -151, target is obsidian
- bucket: fill lava at 54, -55, -154 from 54, -54, -151 hand=lava_bucket accepted=true dist=3.5
- cast: lava into 58, -50, -154 from 57, -51, -151, water planned at 59, -50, -154
- cast: water at 59, -50, -154 (lava there: lava)
- cast: scooping water at 59, -50, -154 from 57, -51, -151, target is obsidian

## Portal cast log, natural-g (last steps)

- bucket: fill water at -3, 22, -41 from -4, 23, -38 hand=bucket accepted=true dist=3.7

## Screenshots (open only if the numbers above point at something visual)

- natural-a: death-1, death-2, death-3, final, milestone-1, milestone-2, milestone-3
- natural-b: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-c: death-1, death-2, death-3, final, milestone-1, milestone-2
- natural-d: death-1, final, milestone-1, milestone-2, milestone-3
- natural-e: final, milestone-1, milestone-2, milestone-3
- natural-f: death-1, final, milestone-1, milestone-2, milestone-3
- natural-g: final, milestone-1, milestone-2, milestone-3
- natural-h: final, milestone-1, milestone-2, milestone-3
