# Batch 36338226027 @ 8a003e0

## Milestones (game time since the autopilot started)

| run | m4 | deaths | length | ended doing |
|---|---|---|---|---|
| cast-a-a1 | 0:01 | mob, mob | 9:30 | nether_portal: collect stone:13 |
| cast-d-a2 | 0:00 | mob | 9:30 | nether_portal: goto death |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-a1 | 0:01 | 0:01 | 0:01 | 1:49 | - | - |
| cast-d-a2 | 0:00 | 0:00 | 0:00 | 2:38 | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob (attack zombie 2, retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-a1 | 250 | 26 | 39 | 105 | 37 | 108 | 0 | 26 | 0 |
| cast-d-a2 | 130 | 8 | 18 | 170 | 38 | 208 | 17 | 3 | 0 |

## Top failures (action + code, count, one example)

- 3 x attack zombie NOT_FOUND  (e.g. cast-a-a1: no reachable zombie in sight)
- 3 x fill_bucket water NOT_FOUND  (e.g. cast-d-a2: no known water source with a bank to stand on)
- 3 x explore water ALREADY_DONE  (e.g. cast-d-a2: already see water)
- 3 x build_portal UNREACHABLE  (e.g. cast-d-a2: lost the line of sight to the frame)
- 3 x retreat NO_PROGRESS  (e.g. cast-d-a2: couldn't get away (nearest monster 1 blocks))
- 2 x attack sheep NOT_FOUND  (e.g. cast-a-a1: no reachable sheep in sight)
- 2 x build_portal NO_ROOM  (e.g. cast-a-a1: no spot with a clear aim at the frame)
- 2 x build_portal NOT_FOUND  (e.g. cast-a-a1: couldn't fill a bucket with lava: no known lava source with a bank to stand on)
- 2 x collect coal STUCK  (e.g. cast-a-a1: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x attack spider NOT_FOUND  (e.g. cast-d-a2: no reachable spider in sight)

## Portal cast log, cast-a-a1 (last steps)

- bucket: fill lava at 12, 64, 3 from 14, 64, 6 hand=lava_bucket accepted=true dist=3.7
- cast: lava into 18, 66, 6 from 15, 65, 7, water planned at 17, 66, 6
- cast: water at 17, 66, 6 (lava there: lava)
- cast: scooping water at 17, 66, 6 from 15, 65, 7, target is obsidian
- bucket: fill lava at 12, 64, 2 from 13, 65, 5 hand=lava_bucket accepted=true dist=3.8
- cast: lava into 16, 65, 6 from 13, 64, 7, water planned at 15, 65, 6
- cast: water at 15, 65, 6 (lava there: lava)
- cast: scooping water at 15, 65, 6 from 13, 64, 7, target is obsidian

## Portal cast log, cast-d-a2 (last steps)

- bucket: fill lava at -1, 88, 10 from 1, 89, 8 hand=lava_bucket accepted=true dist=3.6
- cast: lava into 12, 93, -8 from 13, 91, -5, water planned at 12, 93, -7
- cast: water at 12, 93, -7 (lava there: lava)
- cast: scooping water at 12, 93, -7 from 13, 91, -5, target is obsidian
- bucket: fill lava at -1, 88, 11 from 2, 89, 12 hand=lava_bucket accepted=true dist=3.9
- cast: lava into 12, 92, -8 from 14, 91, -4, water planned at 12, 92, -7
- cast: water at 12, 92, -7 (lava there: lava)
- cast: scooping water at 12, 92, -7 from 14, 91, -4, target is obsidian
