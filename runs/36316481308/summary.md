# Batch 36316481308 @ 21b837f

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 0:47 | 1:28 | 11:38 | mob, arrow, arrow | 20:00 | nether_portal: attack sheep |
| natural-b | 0:15 | 0:45 | - | arrow, mob, mob | 20:00 | iron_tools: attack skeleton |
| natural-c | 1:32 | 3:16 | 17:35 | 0 | 20:00 | nether_portal: smelt iron_ingot:4 |
| natural-d | 0:14 | 1:22 | 4:28 | arrow, arrow | 20:00 | nether_portal: collect log:9 |
| natural-e | 0:23 | 0:58 | 5:12 | explosion.player, arrow | 20:00 | nether_portal: attack skeleton |
| natural-f | 0:13 | 0:48 | 7:08 | fall | 20:00 | nether_portal: collect log:9 |
| natural-g | 0:14 | 0:49 | 2:43 | 0 | 20:00 | nether_portal: retreat |
| natural-h | 0:16 | 0:45 | 5:41 | 0 | 20:00 | nether_portal: explore cow |
| **natural: reached, median** | 8/8 0:15 | 8/8 0:53 | 7/8 6:24* | 11 (1.4 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a | - | - | - | - | - | - |
| natural-b | - | - | - | - | - | - |
| natural-c | - | - | - | - | - | - |
| natural-d | - | - | - | - | - | - |
| natural-e | 13:55 | 13:55 | - | - | - | - |
| natural-f | - | - | - | - | - | - |
| natural-g | 12:57 | 12:56 | 17:41 | - | - | - |
| natural-h | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 6 x arrow (retreat 3, attack skeleton 2, explore cow,pig,sheep,chicken 1)
- 3 x mob (attack zombie 1, attack sheep 1, attack spider 1)
- 1 x explosion.player (attack zombie 1)
- 1 x fall (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 232 | 100 | 51 | 240 | 312 | 0 | 253 | 0 | 8 |
| natural-b | 279 | 32 | 4 | 464 | 165 | 0 | 244 | 0 | 8 |
| natural-c | 410 | 418 | 323 | 48 | 0 | 0 | 0 | 0 | 0 |
| natural-d | 445 | 199 | 13 | 103 | 238 | 0 | 194 | 0 | 5 |
| natural-e | 481 | 318 | 13 | 80 | 70 | 80 | 150 | 0 | 5 |
| natural-f | 365 | 138 | 7 | 332 | 130 | 0 | 222 | 0 | 2 |
| natural-g | 578 | 386 | 29 | 30 | 72 | 18 | 84 | 0 | 0 |
| natural-h | 427 | 134 | 1 | 133 | 49 | 0 | 453 | 0 | 0 |

## Top failures (action + code, count, one example)

- 16 x collect log NOT_FOUND  (e.g. natural-a: no log in sight underground)
- 8 x attack spider NOT_FOUND  (e.g. natural-a: no reachable spider in sight)
- 7 x retreat NO_PROGRESS  (e.g. natural-a: couldn't get away (nearest monster 10 blocks))
- 5 x explore log ALREADY_DONE  (e.g. natural-a: already see log)
- 5 x attack skeleton NOT_FOUND  (e.g. natural-a: no reachable skeleton in sight)
- 5 x goto surface UNREACHABLE  (e.g. natural-b: couldn't find the way up)
- 3 x attack zombie UNREACHABLE  (e.g. natural-a: can't reach the zombie (4 blocks away))
- 3 x build_portal NO_ROOM  (e.g. natural-g: no flat open ground for a portal near the lava)
- 2 x goto surface TIMEOUT  (e.g. natural-b: timed out after 120 s)
- 2 x attack zombie NOT_FOUND  (e.g. natural-d: no reachable zombie in sight)

## Portal cast log, natural-e (last steps)

- bucket: fill water at -326, 62, -69 from -325, 63, -70 hand=bucket accepted=true dist=2.6

## Portal cast log, natural-g (last steps)

- bucket: fill water at 4, -20, -31 from 3, -19, -28 hand=bucket accepted=true dist=3.9

## Screenshots (open only if the numbers above point at something visual)

- natural-a: death-1, death-2, death-3, final, milestone-1, milestone-2, milestone-3
- natural-b: death-1, death-2, death-3, final, milestone-1, milestone-2
- natural-c: final, milestone-1, milestone-2, milestone-3
- natural-d: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-e: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-f: death-1, final, milestone-1, milestone-2, milestone-3
- natural-g: final, milestone-1, milestone-2, milestone-3
- natural-h: final, milestone-1, milestone-2, milestone-3
