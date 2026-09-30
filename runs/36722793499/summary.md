# Batch 36722793499 @ a5da028

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m6 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:33 | 1:17 | - | - | 0 | 19:30 | iron_tools: collect raw_iron:13 |
| natural-drill-0-0-drill-0-on-0 | 0:18 | 6:10 | 13:32 | - | drown | 19:30 | nether_portal: collect raw_iron:9 |
| natural-drill-0-1-drill-0-off-1 | 0:56 | 2:57 | 8:06 | - | lava, onFire, onFire | 19:30 | nether_portal: collect log:3 |
| natural-drill-0-1-drill-0-on-1 | 1:05 | 5:06 | - | - | explosion.player:creeper, explosion.player:creeper | 19:30 | iron_tools: idle |
| natural-drill-0-2-drill-0-off-2 | 0:16 | 1:13 | 10:53 | - | mob:zombie, explosion.player:creeper, arrow:skeleton | 19:30 | nether_portal: collect log:3 |
| natural-drill-0-2-drill-0-on-2 | 0:17 | 1:00 | 7:50 | 15:49 | onFire | 19:30 | nether_portal: attack zombie |
| **natural: reached, median** | 6/6 0:25 | 6/6 2:07 | 4/6 12:12* | 1/6 20:00* | 10 (1.7 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 10:20 | 10:19 | 2:16 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | 9:56 | 9:56 | 12:42 | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x onFire (idle 1, fill_bucket water 1, eat 1)
- 3 x explosion.player:creeper (retreat 1, attack skeleton 1, attack zombie 1)
- 1 x drown (attack drowned 1)
- 1 x lava (eat 1)
- 1 x mob:zombie (attack zombie 1)
- 1 x arrow:skeleton (attack skeleton 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 1027 | 16 | 12 | 68 | 53 | 0 | 20 | 0 | 0 |
| natural-drill-0-0-drill-0-on-0 | 714 | 135 | 31 | 78 | 118 | 0 | 7 | 108 | 0 |
| natural-drill-0-1-drill-0-off-1 | 744 | 273 | 51 | 42 | 6 | 15 | 11 | 0 | 23 |
| natural-drill-0-1-drill-0-on-1 | 228 | 188 | 51 | 270 | 147 | 0 | 0 | 146 | 135 |
| natural-drill-0-2-drill-0-off-2 | 424 | 207 | 7 | 42 | 144 | 0 | 364 | 0 | 0 |
| natural-drill-0-2-drill-0-on-2 | 453 | 145 | 145 | 71 | 51 | 57 | 6 | 266 | 0 |

## Top failures (action + code, count, one example)

- 13 x craft torch NO_RECIPE  (e.g. natural-drill-0-0-drill-0-on-0: no known recipe for torch (not unlocked yet?))
- 6 x attack drowned UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: can't reach the drowned (3 blocks away))
- 6 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-on-0: couldn't get away (nearest monster 9 blocks))
- 4 x attack drowned NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable drowned in sight)
- 4 x shelter NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no safe ground to dig into nearby)
- 4 x unstuck STUCK  (e.g. natural-drill-0-0-drill-0-on-0: boxed in: can't walk, tunnel or climb out)
- 4 x collect log NOT_FOUND  (e.g. natural-drill-0-1-drill-0-on-1: no log seen nearby)
- 3 x collect raw_iron NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-off-0: no progress for 150 s (got 0/13))
- 3 x explore any HAZARD  (e.g. natural-drill-0-0-drill-0-off-0: open water ahead; will turn)
- 3 x collect stone STUCK  (e.g. natural-drill-0-0-drill-0-on-0: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-drill-0-2-drill-0-on-2 (last steps)

- bucket: fill water at -49, 62, 52 from -47, 63, 50 hand=bucket accepted=true dist=3.5
