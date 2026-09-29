# Batch 36566303543 @ 148c985

## Milestones (game time since the autopilot started)

| run | m4 | m6 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| cast-a-castOFF | 0:00 | 8:19 | - | 0 | 11:30 | nether_portal: shelter |
| cast-a-castON | 0:00 | - | 2:57 | 0 | 11:30 | blaze_rods: fortress find |
| cast-b-castOFF | 0:00 | - | - | 0 | 11:30 | nether_portal: shelter |
| cast-b-castON | 0:00 | - | - | mob, mob, mob, mob, lava, mob, mob | 11:30 | nether_portal: shelter |
| cast-c-castOFF | 0:00 | - | - | 0 | 11:30 | nether_portal: build_portal |
| cast-c-castON | 0:00 | - | - | mob, mob | 11:30 | nether_portal: shelter |
| cast-d-castOFF | 0:00 | - | 3:04 | mob | 11:30 | blaze_rods: retreat |
| cast-d-castON | 0:00 | 7:19 | - | 0 | 11:30 | nether_portal: explore lava |
| cast-e-castOFF | 0:00 | - | - | explosion.player | 11:30 | nether_portal: shelter |
| cast-e-castON | 0:00 | - | - | 0 | 11:30 | nether_portal: explore any |
| cast-f-castOFF | 0:00 | - | - | indirectMagic, indirectMagic, indirectMagic, indirectMagic | 11:30 | nether_portal: collect raw_iron:3 |
| cast-f-castON | 0:00 | - | - | lava | 11:30 | nether_portal: goto surface |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| cast-a-castOFF | 0:00 | 0:00 | 0:00 | 1:24 | - | - |
| cast-a-castON | 0:00 | 0:00 | 0:00 | 1:57 | 2:46 | 2:51 |
| cast-b-castOFF | 0:00 | 0:00 | 0:01 | 0:16 | - | - |
| cast-b-castON | 0:00 | 0:00 | 0:00 | 4:45 | - | - |
| cast-c-castOFF | 0:00 | 0:00 | 0:00 | - | - | - |
| cast-c-castON | 0:00 | 0:00 | 0:01 | - | - | - |
| cast-d-castOFF | 0:00 | 0:00 | 0:00 | 0:55 | 2:57 | 2:58 |
| cast-d-castON | 0:00 | 0:00 | 0:01 | 0:59 | - | - |
| cast-e-castOFF | 0:00 | 0:00 | 0:00 | 6:54 | - | - |
| cast-e-castON | 0:00 | 0:00 | 0:00 | - | - | - |
| cast-f-castOFF | 0:00 | 0:00 | 0:00 | - | - | - |
| cast-f-castON | 0:00 | 0:00 | 0:00 | 0:35 | - | - |

## Deaths by cause (what the bot was doing)

- 9 x mob (attack zombie 5, retreat 3, fortress blazes 1)
- 4 x indirectMagic (retreat 2, shelter heal 1, pickup 1)
- 2 x lava (idle 2)
- 1 x explosion.player (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-a-castOFF | 211 | 12 | 5 | 219 | 82 | 99 | 84 | 3 | 0 |
| cast-a-castON | 0 | 0 | 3 | 47 | 2 | 15 | 0 | 651 | 0 |
| cast-b-castOFF | 11 | 0 | 3 | 355 | 21 | 102 | 224 | 0 | 0 |
| cast-b-castON | 61 | 7 | 11 | 221 | 57 | 9 | 16 | 312 | 0 |
| cast-c-castOFF | 0 | 0 | 1 | 450 | 60 | 173 | 30 | 0 | 0 |
| cast-c-castON | 55 | 1 | 5 | 506 | 24 | 0 | 119 | 0 | 0 |
| cast-d-castOFF | 20 | 0 | 2 | 342 | 21 | 143 | 0 | 185 | 0 |
| cast-d-castON | 274 | 7 | 2 | 298 | 11 | 10 | 7 | 108 | 0 |
| cast-e-castOFF | 136 | 0 | 20 | 165 | 3 | 213 | 44 | 133 | 0 |
| cast-e-castON | 0 | 0 | 1 | 708 | 7 | 1 | 0 | 0 | 0 |
| cast-f-castOFF | 159 | 0 | 0 | 502 | 41 | 0 | 1 | 0 | 0 |
| cast-f-castON | 153 | 14 | 1 | 170 | 9 | 5 | 0 | 358 | 0 |

## Top failures (action + code, count, one example)

- 75 x explore any HAZARD  (e.g. cast-c-castOFF: open water ahead; will turn)
- 63 x explore water ALREADY_DONE  (e.g. cast-c-castOFF: already see water)
- 62 x fill_bucket water NOT_FOUND  (e.g. cast-c-castOFF: no known water source with a bank to stand on)
- 14 x craft torch NO_RECIPE  (e.g. cast-a-castOFF: no known recipe for torch (not unlocked yet?))
- 14 x shelter NO_ROOM  (e.g. cast-e-castON: no safe ground to dig into nearby)
- 13 x attack spider UNREACHABLE  (e.g. cast-a-castOFF: no sightline to the spider)
- 9 x attack zombie NOT_FOUND  (e.g. cast-b-castON: no reachable zombie in sight)
- 8 x build_portal NEED_ITEM  (e.g. cast-b-castOFF: need a second bucket for lava)
- 7 x build_portal UNREACHABLE  (e.g. cast-b-castOFF: couldn't get in front of the portal site)
- 6 x attack spider NOT_FOUND  (e.g. cast-b-castON: no reachable spider in sight)

## Portal cast log, cast-a-castOFF (last steps)

- cast: fetching lava for -39, -41, -33
- bucket: fill lava at -51, -55, -35 from -50, -53, -34 hand=lava_bucket accepted=true dist=3.2
- cast: cast -39, -41, -33 from -43, -43, -34, water at -40, -41, -33
- cast: lava into -39, -41, -33 from -43, -43, -34, water planned at -40, -41, -33
- cast: water at -40, -41, -33 (lava there: lava)
- cast: scooping water at -40, -41, -33 from -43, -43, -34, target is obsidian
- cast: obsidian at -39, -41, -33; water recovered
- cast: fetching lava for -39, -42, -33

## Portal cast log, cast-a-castON (last steps)

- cast: water at 19, 69, -14 (lava there: lava)
- cast: scooping water at 19, 69, -14 from 20, 65, -12, target is obsidian
- cast: scooping water at 19, 69, -14 from 20, 68, -11, target is obsidian
- cast: obsidian at 19, 69, -13; water recovered
- cast: frame complete at 19, 65, -11
- cast: backing wall ready at 19, 65, -11
- cast: frame complete at 19, 65, -11
- cast: portal lit

## Portal cast log, cast-b-castOFF (last steps)

- cast: scooping water at -10, 122, -6 from -5, 122, -8, target is lava
- cast: failed: the lava didn't harden
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame
- cast: failed: water won't drain from the frame

## Portal cast log, cast-b-castON (last steps)

- cast: cast -4, 125, 2 from -1, 124, 0, water at -4, 125, 1
- cast: lava into -4, 125, 2 from -1, 124, 0, water planned at -4, 125, 1
- cast: water at -4, 125, 1 (lava there: lava)
- cast: scooping water at -4, 125, 1 from -1, 124, 0, target is obsidian
- cast: obsidian at -4, 125, 2; water recovered
- cast: fetching lava for -4, 124, 2
- cast: backing wall ready at -4, 123, 2
- cast: fetching lava for -4, 124, 2

## Portal cast log, cast-c-castOFF (last steps)

- bucket: fill water at 606, 62, 363 from 609, 62, 361 hand=bucket accepted=true dist=3.8

## Portal cast log, cast-d-castOFF (last steps)

- cast: backing wall ready at 23, 85, 1
- cast: cast 23, 89, -1 from 24, 88, -2, water at 23, 90, -1
- cast: lava into 23, 89, -1 from 24, 88, -2, water planned at 23, 90, -1
- cast: water at 23, 90, -1 (lava there: lava)
- cast: scooping water at 23, 90, -1 from 24, 88, -2, target is obsidian
- cast: obsidian at 23, 89, -1; water recovered
- cast: frame complete at 23, 85, 1
- cast: portal lit

## Portal cast log, cast-d-castON (last steps)

- cast: fetching lava for 16, 91, -10
- bucket: fill lava at 2, 94, -1 from 5, 95, 0 hand=lava_bucket accepted=true dist=3.9
- cast: cast 16, 91, -10 from 17, 90, -7, water at 16, 91, -9
- cast: lava into 16, 91, -10 from 17, 90, -8, water planned at 16, 91, -9
- cast: water at 16, 91, -9 (lava there: lava)
- cast: scooping water at 16, 91, -9 from 17, 90, -8, target is obsidian
- cast: obsidian at 16, 91, -10; water recovered
- cast: fetching lava for 16, 90, -8

## Portal cast log, cast-e-castOFF (last steps)

- cast: fetching lava for -261, 71, -130
- bucket: fill lava at -260, 71, -120 from -261, 72, -123 hand=lava_bucket accepted=true dist=3.5
- cast: cast -261, 71, -130 from -261, 70, -127, water at -262, 71, -130
- cast: lava into -261, 71, -130 from -261, 70, -127, water planned at -262, 71, -130
- cast: water at -262, 71, -130 (lava there: lava)
- cast: scooping water at -262, 71, -130 from -261, 70, -127, target is obsidian
- cast: obsidian at -261, 71, -130; water recovered
- cast: fetching lava for -263, 70, -130

## Portal cast log, cast-f-castON (last steps)

- cast: lava into 63, 97, -117 from 64, 95, -117, water planned at 63, 97, -118
- cast: water at 63, 97, -118 (lava there: lava)
- cast: scooping water at 63, 97, -118 from 64, 95, -117, target is obsidian
- cast: obsidian at 63, 97, -117; water recovered
- cast: fetching lava for 63, 96, -117
- bucket: fill lava at 17, 70, -63 from 18, 71, -66 hand=lava_bucket accepted=true dist=3.7
- cast: cast 63, 96, -117 from 64, 95, -117, water at 63, 96, -118
- cast: lava into 63, 96, -117 from 64, 95, -117, water planned at 63, 96, -118
