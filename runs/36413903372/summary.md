# Batch 36413903372 @ 89cabb1

## Milestones (game time since the autopilot started)

| run |  | deaths | length | ended doing |
|---|---|---|---|
| cast-a-cast1 |  | 0 | ? | no FINAL line |
| cast-b-cast2 |  | 0 | ? | no FINAL line |
| natural-h |  | mob, mob | ? | no FINAL line |
| natural-i |  | mob | ? | no FINAL line |
| natural-j |  | arrow, mob | ? | no FINAL line |
| natural-k |  | 0 | ? | no FINAL line |

## Deaths by cause (what the bot was doing)

- 4 x mob (retreat 3, attack zombie 1)
- 1 x arrow (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-cast1 | 150 | 0 | 5 | 232 | 50 | 247 | 0 | 5 | 0 |
| cast-b-cast2 | 40 | 0 | 4 | 268 | 29 | 298 | 0 | 0 | 0 |
| natural-h | 274 | 89 | 52 | 135 | 49 | 0 | 0 | 28 | 0 |
| natural-i | 256 | 20 | 17 | 190 | 49 | 0 | 0 | 44 | 0 |
| natural-j | 238 | 110 | 1 | 137 | 182 | 0 | 0 | 25 | 0 |
| natural-k | 256 | 173 | 30 | 160 | 0 | 0 | 0 | 9 | 0 |

## Top failures (action + code, count, one example)

- 13 x retreat NO_PROGRESS  (e.g. natural-h: couldn't get away (nearest monster 5 blocks))
- 10 x collect log NOT_FOUND  (e.g. natural-h: no log seen nearby)
- 9 x attack zombie NOT_FOUND  (e.g. natural-h: no reachable zombie in sight)
- 5 x collect stone STUCK  (e.g. cast-a-cast1: stuck: stayed inside 2.0 blocks for 12 s)
- 5 x explore log ALREADY_DONE  (e.g. natural-i: already see log)
- 4 x explore any HAZARD  (e.g. cast-a-cast1: open water ahead; will turn)
- 4 x build_portal NOT_FOUND  (e.g. cast-a-cast1: couldn't fill a bucket with lava: no known lava source with a bank to stand on)
- 3 x craft stone_pickaxe NO_ROOM  (e.g. natural-h: no room to place a crafting_table)
- 3 x collect flint STUCK  (e.g. natural-h: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x explore gravel,water ALREADY_DONE  (e.g. natural-h: already see gravel)

## Portal cast log, cast-a-cast1 (last steps)

- cast: failed: no spot with a clear aim at the frame
- cast: lava into 15, 68, -4 from 14, 65, -3, water planned at 15, 68, -3
- cast: water at 15, 68, -3 (lava there: lava)
- cast: scooping water at 15, 68, -3 from 14, 65, -3, target is obsidian
- bucket: fill lava at 18, 65, -8 from 15, 65, -10 hand=lava_bucket accepted=true dist=3.8
- cast: lava into 9, 69, -10 from 9, 66, -11, water planned at 8, 69, -10
- cast: water at 8, 69, -10 (lava there: lava)
- cast: scooping water at 8, 69, -10 from 9, 66, -11, target is obsidian

## Portal cast log, cast-b-cast2 (last steps)

- cast: lava into 10, 126, -7 from 7, 124, -6, water planned at 10, 126, -8
- cast: water at 10, 126, -8 (lava there: lava)
- cast: scooping water at 10, 126, -8 from 7, 124, -6, target is obsidian
- bucket: fill lava at 18, 123, 3 from 15, 124, 2 hand=lava_bucket accepted=true dist=3.8
- cast: lava into 10, 125, -7 from 7, 124, -6, water planned at 10, 125, -8
- cast: water at 10, 125, -8 (lava there: lava)
- cast: scooping water at 10, 125, -8 from 7, 124, -6, target is obsidian
- cast: failed: water won't drain from the frame
