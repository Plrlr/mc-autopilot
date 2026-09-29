# Batch 36614417906 @ 38848a6

## Milestones (game time since the autopilot started)

| run | m4 | m6 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| diamond-a-diamond-a | 0:00 | 24:28 | - | 0 | 29:30 | nether_portal: craft torch:8 |
| diamond-b-diamond-b | 0:00 | - | - | 0 | 29:30 | nether_portal: diamond_hunt 3 |
| diamond-c-diamond-c | 0:00 | - | - | mob:zombie | 29:30 | nether_portal: collect stone:12 |
| diamond-d-diamond-d | 0:00 | - | - | 0 | 29:30 | nether_portal: craft torch:8 |
| mold-a-mold-a | - | 0:00 | 4:20 | 0 | 11:30 | blaze_rods: explore any |
| mold-b-mold-b | - | 0:00 | - | arrow:skeleton | 11:30 | nether_portal: smelt iron_ingot:14 |
| portal-a-pool-a | - | 0:00 | 2:39 | 0 | 11:30 | blaze_rods: fortress blazes:8 |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| diamond-a-diamond-a | 0:00 | 0:00 | 18:00 | - | - | - |
| diamond-b-diamond-b | 0:00 | 0:00 | - | - | - | - |
| diamond-c-diamond-c | 0:00 | 0:00 | - | - | - | - |
| diamond-d-diamond-d | 0:00 | 0:00 | 0:04 | - | - | - |
| mold-a-mold-a | 0:00 | 0:00 | 0:00 | 4:09 | 4:12 | 4:12 |
| mold-b-mold-b | 0:00 | 0:00 | 0:01 | - | - | - |
| portal-a-pool-a | - | 0:00 | 0:00 | 2:31 | 2:33 | 2:34 |

## Deaths by cause (what the bot was doing)

- 1 x mob:zombie (attack skeleton 1)
- 1 x arrow:skeleton (attack skeleton 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| diamond-a-diamond-a | 135 | 268 | 31 | 365 | 47 | 0 | 0 | 952 | 0 |
| diamond-b-diamond-b | 280 | 66 | 0 | 141 | 0 | 0 | 25 | 1266 | 0 |
| diamond-c-diamond-c | 341 | 12 | 1 | 419 | 17 | 0 | 187 | 810 | 0 |
| diamond-d-diamond-d | 181 | 291 | 3 | 244 | 47 | 5 | 0 | 1026 | 0 |
| mold-a-mold-a | 42 | 0 | 14 | 445 | 0 | 0 | 0 | 218 | 0 |
| mold-b-mold-b | 276 | 64 | 3 | 296 | 72 | 0 | 0 | 3 | 0 |
| portal-a-pool-a | 0 | 1 | 23 | 63 | 13 | 10 | 0 | 608 | 0 |

## Top failures (action + code, count, one example)

- 21 x craft torch NO_RECIPE  (e.g. diamond-b-diamond-b: no known recipe for torch (not unlocked yet?))
- 16 x fortress blazes NOT_FOUND  (e.g. mold-a-mold-a: no blazes in the parts of the fortress we know)
- 11 x goto surface UNREACHABLE  (e.g. diamond-a-diamond-a: couldn't find the way up)
- 10 x explore any HAZARD  (e.g. diamond-a-diamond-a: open water ahead; will turn)
- 8 x diamond_hunt 3 HAZARD  (e.g. diamond-a-diamond-a: stair_down -54 failed: no safe direction to dig down)
- 8 x collect raw_iron STUCK  (e.g. diamond-c-diamond-c: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x collect log NOT_FOUND  (e.g. diamond-a-diamond-a: no log seen nearby)
- 6 x explore log ALREADY_DONE  (e.g. diamond-a-diamond-a: already see log)
- 6 x collect raw_iron NOT_FOUND  (e.g. diamond-c-diamond-c: can't find any raw_iron nearby)
- 4 x diamond_hunt 3 NO_PROGRESS  (e.g. diamond-a-diamond-a: stair_down -54 failed: block too hard to dig)

## Portal cast log, diamond-d-diamond-d (last steps)

- bucket: fill water at -13, 62, 75 from -14, 63, 77 hand=water_bucket accepted=true dist=3.0

## Portal cast log, mold-a-mold-a (last steps)

- bucket: fill lava at 8, 65, -7 from 5, 66, -7 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at 6, 65, -7 from 3, 66, -8 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at 6, 65, -6 from 3, 66, -7 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at 7, 65, -8 from 4, 66, -8 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at 7, 65, -6 from 4, 65, -5 hand=lava_bucket accepted=true dist=3.5

## Portal cast log, portal-a-pool-a (last steps)

- bucket: fill water at -18, 62, 32 from -16, 63, 34 hand=water_bucket accepted=true dist=3.3
