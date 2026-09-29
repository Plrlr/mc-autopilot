# Batch 36500989212 @ 14d39aa

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m6 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|---|
| cast-a-v2cast | - | - | 0:00 | 6:16 | - | 0 | 9:30 | nether_portal: retreat |
| natural-b-v2nat | 0:16 | 7:16 | - | - | - | arrow, explosion.player, indirectMagic, mob, mob | 11:30 | iron_tools: collect coal:6 |
| nether-c-v2neth | - | - | - | - | 0:00 | arrow | 7:30 | nether_portal: collect log:3 |
| **natural: reached, median** | 1/1 0:16 | 1/1 7:16 | 0/1 - | 0/1 - | 0/1 - | 5 (5.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-v2cast | 0:00 | 0:00 | 0:01 | - | - | - |
| natural-b-v2nat | - | - | - | - | - | - |
| nether-c-v2neth | - | 0:00 | 0:03 | - | - | - |

## Deaths by cause (what the bot was doing)

- 2 x arrow (attack zombie 1, fortress find 1)
- 2 x mob (attack zombie 2)
- 1 x explosion.player (attack skeleton 1)
- 1 x indirectMagic (attack witch 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-v2cast | 194 | 6 | 24 | 325 | 29 | 17 | 0 | 1 | 0 |
| natural-b-v2nat | 523 | 77 | 1 | 1 | 101 | 0 | 0 | 0 | 0 |
| nether-c-v2neth | 66 | 0 | 1 | 315 | 25 | 0 | 0 | 68 | 0 |

## Top failures (action + code, count, one example)

- 8 x make_obsidian obsidian NOT_FOUND  (e.g. cast-a-v2cast: no lava source we can safely pour water next to)
- 8 x collect log WRONG_PLACE  (e.g. nether-c-v2neth: in water: log is dug from dry ground)
- 6 x explore log HAZARD  (e.g. nether-c-v2neth: open water ahead; will turn)
- 5 x retreat NO_PROGRESS  (e.g. cast-a-v2cast: couldn't get away (nearest monster 5 blocks))
- 4 x shelter NO_ROOM  (e.g. cast-a-v2cast: no safe ground to dig into nearby)
- 4 x attack zombie NOT_FOUND  (e.g. natural-b-v2nat: no reachable zombie in sight)
- 3 x attack skeleton NOT_FOUND  (e.g. natural-b-v2nat: no reachable skeleton in sight)
- 3 x attack witch NOT_FOUND  (e.g. natural-b-v2nat: no reachable witch in sight)
- 3 x collect log NOT_FOUND  (e.g. nether-c-v2neth: no log seen nearby)
- 2 x explore any HAZARD  (e.g. nether-c-v2neth: open water ahead; will turn)

## Portal cast log, cast-a-v2cast (last steps)

- bucket: fill water at 168, 62, -77 from 170, 63, -76 hand=bucket accepted=true dist=3.2
