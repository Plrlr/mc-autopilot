# Batch 36406541948 @ 6f831ac

## Milestones (game time since the autopilot started)

| run | m4 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|
| cast-a-portal-genes | 0:00 | 2:21 | mob, fireball, fireball, mob, mob, onFire, mob | 19:30 | blaze_rods: explore any |
| cast-b-portal-genes | 0:00 | 6:28 | lava, mob, lava | 19:30 | blaze_rods: explore any |
| cast-c-portal-genes | 0:00 | - | 0 | 19:30 | nether_portal: collect diamond:1 |
| cast-d-portal-genes | 0:00 | 14:26 | fall, arrow | 19:30 | blaze_rods: enter_portal nether |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-portal-genes | 0:00 | 0:00 | 0:00 | 1:07 | 1:42 | 2:16 |
| cast-b-portal-genes | 0:00 | 0:00 | 0:00 | 5:09 | 6:20 | 6:22 |
| cast-c-portal-genes | 0:00 | 0:00 | 0:00 | 3:04 | - | - |
| cast-d-portal-genes | 0:00 | 0:00 | 0:06 | 0:37 | 14:21 | 14:21 |

## Deaths by cause (what the bot was doing)

- 5 x mob (fortress blazes 3, goto death 1, fortress find 1)
- 2 x fireball (explore cow,pig,sheep,chicken 1, fortress blazes 1)
- 2 x lava (fortress find 1, idle 1)
- 1 x onFire (fortress blazes 1)
- 1 x fall (retreat 1)
- 1 x arrow (attack zombie 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-portal-genes | 44 | 6 | 22 | 531 | 0 | 58 | 0 | 514 | 0 |
| cast-b-portal-genes | 0 | 0 | 9 | 469 | 5 | 242 | 18 | 444 | 0 |
| cast-c-portal-genes | 381 | 31 | 2 | 403 | 9 | 316 | 24 | 31 | 0 |
| cast-d-portal-genes | 92 | 0 | 4 | 168 | 50 | 342 | 283 | 250 | 0 |

## Top failures (action + code, count, one example)

- 15 x fortress blazes NOT_FOUND  (e.g. cast-a-portal-genes: no blazes in the parts of the fortress we know)
- 12 x build_portal UNREACHABLE  (e.g. cast-c-portal-genes: lost the line of sight to the frame)
- 6 x explore any HAZARD  (e.g. cast-c-portal-genes: open water ahead; will turn)
- 5 x collect log NOT_FOUND  (e.g. cast-c-portal-genes: no log in sight underground)
- 5 x explore log ALREADY_DONE  (e.g. cast-c-portal-genes: already see log)
- 4 x collect log STUCK  (e.g. cast-a-portal-genes: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x build_portal USE_FAILED  (e.g. cast-a-portal-genes: couldn't light the portal)
- 3 x fortress find UNREACHABLE  (e.g. cast-a-portal-genes: couldn't make headway that way (lava or cliffs); will turn)
- 3 x attack ghast NOT_FOUND  (e.g. cast-a-portal-genes: no reachable ghast in sight)
- 3 x enter_portal nether TIMEOUT  (e.g. cast-b-portal-genes: timed out after 90 s)

## Portal cast log, cast-a-portal-genes (last steps)

- bucket: fill lava at 6, 65, 6 from 4, 66, 4 hand=lava_bucket accepted=true dist=3.4
- cast: lava into -1, 69, 6 from 2, 66, 5, water planned at -1, 69, 5
- cast: water at -1, 69, 5 (lava there: lava)
- cast: scooping water at -1, 69, 5 from 2, 66, 5, target is obsidian
- bucket: fill lava at 6, 65, 5 from 3, 66, 4 hand=lava_bucket accepted=true dist=3.9
- cast: lava into -1, 69, 5 from 2, 66, 4, water planned at -1, 69, 4
- cast: water at -1, 69, 4 (lava there: lava)
- cast: scooping water at -1, 69, 4 from 2, 66, 4, target is obsidian

## Portal cast log, cast-b-portal-genes (last steps)

- cast: failed: no spot with a clear aim at the frame
- cast: failed: no spot with a clear aim at the frame
- cast: failed: no spot with a clear aim at the frame
- cast: failed: lost the line of sight to the frame
- cast: lava into 9, 128, -8 from 7, 124, -9, water planned at 9, 128, -7
- cast: water at 9, 128, -7 (lava there: lava)
- cast: scooping water at 9, 128, -7 from 7, 124, -9, target is obsidian
- cast: scooping water at 9, 128, -7 from 9, 127, -11, target is obsidian

## Portal cast log, cast-c-portal-genes (last steps)

- bucket: fill water at -105, 80, 35 from -105, 81, 36 hand=water_bucket accepted=true dist=2.0
- cast: lava into -86, 95, 52 from -87, 92, 51, water planned at -87, 95, 52
- cast: water at -87, 95, 52 (lava there: lava)
- cast: scooping water at -87, 95, 52 from -87, 92, 51, target is obsidian
- bucket: fill lava at -96, 96, 37 from -93, 96, 39 hand=lava_bucket accepted=true dist=3.8
- cast: lava into -86, 94, 52 from -88, 93, 49, water planned at -87, 94, 52
- cast: water at -87, 94, 52 (lava there: lava)
- cast: scooping water at -87, 94, 52 from -88, 93, 49, target is obsidian

## Portal cast log, cast-d-portal-genes (last steps)

- bucket: fill lava at 36, 63, 69 from 37, 64, 66 hand=lava_bucket accepted=true dist=3.9
- cast: lava into 41, 68, 62 from 40, 65, 61, water planned at 40, 68, 62
- cast: water at 40, 68, 62 (lava there: lava)
- cast: scooping water at 40, 68, 62 from 40, 65, 61, target is obsidian
- bucket: fill lava at 34, 63, 68 from 35, 64, 65 hand=lava_bucket accepted=true dist=3.8
- cast: lava into 40, 68, 62 from 38, 65, 61, water planned at 39, 68, 62
- cast: water at 39, 68, 62 (lava there: lava)
- cast: scooping water at 39, 68, 62 from 38, 65, 61, target is obsidian
