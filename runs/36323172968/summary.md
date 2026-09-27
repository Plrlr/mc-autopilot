# Batch 36323172968 @ be17f34

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a-lean | 0:20 | 2:26 | - | arrow, mob | 8:00 | iron_tools: goto death |
| natural-a-leanlf | 0:21 | 1:00 | 5:20 | arrow | 8:00 | nether_portal: collect stone:13 |
| natural-a-leansod | 0:23 | 1:01 | - | mob | 8:00 | iron_tools: attack pig |
| natural-a-plain | 0:21 | 1:02 | 4:33 | mob | 8:00 | nether_portal: goto death |
| **natural: reached, median** | 4/4 0:21 | 4/4 1:01 | 2/4 12:40* | 5 (1.2 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 3 x mob (attack zombie 2, attack cave_spider 1)
- 2 x arrow (attack skeleton 1, explore log 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a-lean | 261 | 40 | 37 | 92 | 42 | 0 | 0 | 0 | 5 |
| natural-a-leanlf | 216 | 82 | 32 | 112 | 31 | 0 | 0 | 0 | 2 |
| natural-a-leansod | 287 | 79 | 2 | 40 | 68 | 0 | 0 | 0 | 2 |
| natural-a-plain | 230 | 78 | 5 | 64 | 96 | 0 | 1 | 0 | 2 |

## Top failures (action + code, count, one example)

- 10 x collect log NOT_FOUND  (e.g. natural-a-leanlf: no log in sight underground)
- 8 x explore log ALREADY_DONE  (e.g. natural-a-leanlf: already see log)
- 3 x attack cave_spider NOT_FOUND  (e.g. natural-a-plain: no reachable cave_spider in sight)
- 2 x attack zombie NOT_FOUND  (e.g. natural-a-lean: no reachable zombie in sight)
- 1 x attack zombie UNREACHABLE  (e.g. natural-a-lean: can't reach the zombie (4 blocks away))
- 1 x smelt iron_ingot STUCK  (e.g. natural-a-leansod: stuck for 12 s)

## Screenshots (open only if the numbers above point at something visual)

- natural-a-lean: death-1, death-2, final, milestone-1, milestone-2
- natural-a-leanlf: death-1, final, milestone-1, milestone-2, milestone-3
- natural-a-leansod: death-1, final, milestone-1, milestone-2
- natural-a-plain: death-1, final, milestone-1, milestone-2, milestone-3
