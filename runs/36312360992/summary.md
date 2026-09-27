# Batch 36312360992 @ d27e941

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 0:21 | 1:02 | 6:33 | 0 | 20:00 | nether_portal: smelt iron_ingot:6 |
| natural-b | 0:14 | 0:51 | 4:51 | 0 | 20:00 | nether_portal: smelt cooked_porkchop:9 |
| natural-c | 1:16 | 2:51 | 6:00 | 0 | 20:00 | nether_portal: collect diamond:1 |
| natural-d | 0:14 | 1:31 | 7:25 | arrow, onFire | 20:00 | nether_portal: collect log:3 |
| natural-e | 0:23 | 2:10 | 6:04 | 0 | 20:00 | nether_portal: build_portal |
| natural-f | 0:13 | 1:37 | 5:47 | explosion.player | 20:00 | nether_portal: collect log:9 |
| natural-g | 0:28 | 2:00 | - | explosion.player, mob | 20:00 | iron_tools: goto death |
| natural-h | 0:13 | 0:50 | 16:34 | arrow, mob, mob | 20:00 | nether_portal: collect log:9 |
| **natural: reached, median** | 8/8 0:17 | 8/8 1:34 | 7/8 6:18* | 8 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a | - | - | - | - | - | - |
| natural-b | - | - | - | - | - | - |
| natural-c | 14:03 | 13:12 | 19:59 | - | - | - |
| natural-d | 13:01 | 12:27 | - | - | - | - |
| natural-e | 10:57 | 10:57 | 16:09 | - | - | - |
| natural-f | - | - | - | - | - | - |
| natural-g | - | - | - | - | - | - |
| natural-h | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob (attack pig 1, retreat 1, attack spider 1)
- 2 x arrow (retreat 1, shelter heal 1)
- 2 x explosion.player (retreat 2)
- 1 x onFire (collect diamond 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 406 | 284 | 274 | 60 | 173 | 0 | 0 | 0 | 0 |
| natural-b | 288 | 396 | 322 | 192 | 0 | 0 | 0 | 0 | 0 |
| natural-c | 486 | 349 | 89 | 20 | 141 | 113 | 0 | 0 | 0 |
| natural-d | 691 | 163 | 26 | 176 | 130 | 1 | 4 | 0 | 5 |
| natural-e | 470 | 308 | 3 | 213 | 116 | 87 | 0 | 0 | 0 |
| natural-f | 362 | 122 | 0 | 165 | 57 | 0 | 489 | 0 | 2 |
| natural-g | 250 | 345 | 297 | 42 | 231 | 0 | 27 | 0 | 5 |
| natural-h | 681 | 174 | 31 | 229 | 60 | 0 | 12 | 0 | 8 |

## Top failures (action + code, count, one example)

- 28 x explore any UNREACHABLE  (e.g. natural-d: couldn't make headway that way; will turn)
- 19 x collect log NOT_FOUND  (e.g. natural-a: no log in sight underground)
- 17 x explore log ALREADY_DONE  (e.g. natural-a: already see log)
- 14 x retreat NO_PROGRESS  (e.g. natural-a: couldn't get away (nearest monster 3 blocks))
- 5 x shelter NO_ROOM  (e.g. natural-d: no safe ground to dig into nearby)
- 5 x goto surface UNREACHABLE  (e.g. natural-f: couldn't find the way up)
- 4 x attack zombie UNREACHABLE  (e.g. natural-d: lost sight of the zombie)
- 3 x attack zombie NOT_FOUND  (e.g. natural-h: no reachable zombie in sight)
- 3 x attack spider NOT_FOUND  (e.g. natural-h: no reachable spider in sight)
- 2 x attack skeleton UNREACHABLE  (e.g. natural-f: lost sight of the skeleton)

## Portal cast log, natural-c (last steps)

- bucket: fill water at -106, 62, -65 from -107, 63, -66 hand=bucket accepted=true dist=2.4

## Portal cast log, natural-d (last steps)

- bucket: fill water at -66, 62, -62 from -65, 63, -59 hand=bucket accepted=true dist=3.7

## Portal cast log, natural-e (last steps)

- bucket: fill water at -232, 62, -246 from -234, 63, -248 hand=bucket accepted=true dist=3.5

## Screenshots (open only if the numbers above point at something visual)

- natural-a: final, milestone-1, milestone-2, milestone-3
- natural-b: final, milestone-1, milestone-2, milestone-3
- natural-c: final, milestone-1, milestone-2, milestone-3
- natural-d: death-1, death-2, final, milestone-1, milestone-2, milestone-3
- natural-e: final, milestone-1, milestone-2, milestone-3
- natural-f: death-1, final, milestone-1, milestone-2, milestone-3
- natural-g: death-1, death-2, final, milestone-1, milestone-2
- natural-h: death-1, death-2, death-3, final, milestone-1, milestone-2, milestone-3
