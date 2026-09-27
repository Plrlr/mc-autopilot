# Batch 36336886546 @ e5b55e2

## Milestones (game time since the autopilot started)

| run | m4 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|
| cast-a-c1 | 0:00 | - | 0 | 9:30 | nether_portal: shelter |
| cast-b-c2 | 0:00 | 6:11 | 0 | 9:30 | blaze_rods: fortress find |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-c1 | 0:00 | 0:00 | 0:00 | 3:56 | - | - |
| cast-b-c2 | 0:00 | 0:00 | 0:00 | 5:19 | 6:05 | 6:05 |

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-c1 | 200 | 0 | 4 | 192 | 13 | 172 | 17 | 0 | 0 |
| cast-b-c2 | 12 | 0 | 4 | 139 | 11 | 205 | 0 | 226 | 0 |

## Top failures (action + code, count, one example)

- 3 x fill_bucket water NOT_FOUND  (e.g. cast-a-c1: no known water source with a bank to stand on)
- 3 x explore water ALREADY_DONE  (e.g. cast-a-c1: already see water)
- 2 x build_portal NO_ROOM  (e.g. cast-a-c1: no spot with a clear aim at the frame)
- 1 x build_portal UNREACHABLE  (e.g. cast-a-c1: lost the line of sight to the frame)
- 1 x build_portal NOT_FOUND  (e.g. cast-a-c1: couldn't fill a bucket with lava: no known lava source with a bank to stand on)
- 1 x build_portal NEED_ITEM  (e.g. cast-a-c1: need a second bucket for lava)

## Portal cast log, cast-a-c1 (last steps)

- cast: failed: no spot with a clear aim at the frame
- cast: failed: no spot with a clear aim at the frame
- cast: failed: no spot with a clear aim at the frame
- cast: failed: no spot with a clear aim at the frame
- cast: failed: no spot with a clear aim at the frame
- cast: lava into -214, 66, -195 from -215, 63, -194, water planned at -214, 66, -194
- cast: water at -214, 66, -194 (lava there: lava)
- cast: scooping water at -214, 66, -194 from -215, 63, -194, target is obsidian

## Portal cast log, cast-b-c2 (last steps)

- bucket: fill lava at 10, 129, 2 from 11, 130, 5 hand=lava_bucket accepted=true dist=3.9
- cast: lava into 15, 129, 6 from 12, 127, 7, water planned at 15, 130, 6
- cast: water at 15, 130, 6 (lava there: lava)
- cast: scooping water at 15, 130, 6 from 12, 127, 7, target is obsidian
- bucket: fill lava at 9, 129, 2 from 10, 130, 5 hand=lava_bucket accepted=true dist=3.6
- cast: lava into 15, 129, 7 from 12, 127, 7, water planned at 15, 130, 7
- cast: water at 15, 130, 7 (lava there: lava)
- cast: scooping water at 15, 130, 7 from 12, 127, 7, target is obsidian
