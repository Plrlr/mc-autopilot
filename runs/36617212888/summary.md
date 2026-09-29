# Batch 36617212888 @ 65a084b

## Milestones (game time since the autopilot started)

| run | m6 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|
| mold-a-mold2-a | 0:00 | - | onFire | 11:30 | nether_portal: shelter |
| mold-c-mold2-c | 0:00 | - | 0 | 11:30 | nether_portal: dig_portal |
| mold-e-mold2-e | 0:00 | 5:45 | mob:magma_cube | 11:30 | blaze_rods: shelter |
| portal-b-pool2-b | 0:00 | 4:22 | 0 | 11:30 | blaze_rods: fortress blazes:8 |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| mold-a-mold2-a | 0:00 | 0:00 | 0:00 | - | - | - |
| mold-c-mold2-c | 0:00 | 0:00 | 0:00 | - | - | - |
| mold-e-mold2-e | 0:00 | 0:00 | 0:00 | 5:35 | 5:36 | 5:38 |
| portal-b-pool2-b | 1:54 | 0:00 | 0:00 | 4:16 | 4:18 | 4:19 |

## Deaths by cause (what the bot was doing)

- 1 x onFire (eat 1)
- 1 x mob:magma_cube (attack magma_cube 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| mold-a-mold2-a | 301 | 49 | 104 | 198 | 42 | 0 | 8 | 13 | 0 |
| mold-c-mold2-c | 0 | 0 | 0 | 68 | 0 | 0 | 0 | 546 | 74 |
| mold-e-mold2-e | 123 | 0 | 20 | 328 | 31 | 0 | 31 | 179 | 0 |
| portal-b-pool2-b | 51 | 39 | 3 | 5 | 26 | 13 | 0 | 579 | 0 |

## Top failures (action + code, count, one example)

- 14 x craft torch NO_RECIPE  (e.g. mold-a-mold2-a: no known recipe for torch (not unlocked yet?))
- 5 x explore log ALREADY_DONE  (e.g. mold-a-mold2-a: already see log)
- 3 x obsidian_pool NO_PROGRESS  (e.g. mold-a-mold2-a: only 0 obsidian after the mold and 3 rounds)
- 3 x collect log NOT_FOUND  (e.g. mold-a-mold2-a: no log seen nearby)
- 3 x attack pig UNREACHABLE  (e.g. mold-a-mold2-a: can't reach the pig (3 blocks away))
- 3 x collect flint NO_ROOM  (e.g. mold-a-mold2-a: no free flat spot beside us for the gravel)
- 3 x dig_portal NO_ROOM  (e.g. mold-c-mold2-c: three portal sites went bad)
- 3 x fortress find UNREACHABLE  (e.g. mold-e-mold2-e: couldn't make headway that way (lava or cliffs); will turn)
- 3 x enter_portal nether TIMEOUT  (e.g. mold-e-mold2-e: timed out after 90 s)
- 2 x collect log WRONG_PLACE  (e.g. mold-a-mold2-a: in water: log is dug from dry ground)

## Portal cast log, mold-a-mold2-a (last steps)

- bucket: fill lava at 18, 65, -8 from 15, 66, -8 hand=lava_bucket accepted=true dist=3.6

## Portal cast log, mold-c-mold2-c (last steps)

- bucket: fill lava at -99, 76, 22 from -102, 77, 21 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at -99, 76, 23 from -102, 77, 22 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at -98, 76, 21 from -101, 77, 21 hand=lava_bucket accepted=true dist=3.5
- bucket: fill lava at -98, 76, 22 from -101, 77, 21 hand=lava_bucket accepted=true dist=3.6
- bucket: fill lava at -98, 76, 23 from -101, 77, 22 hand=lava_bucket accepted=true dist=3.9
- bucket: fill lava at -97, 76, 21 from -100, 77, 21 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at -97, 76, 23 from -100, 77, 22 hand=lava_bucket accepted=true dist=3.8
- bucket: fill lava at -96, 76, 21 from -99, 77, 20 hand=lava_bucket accepted=true dist=3.6

## Portal cast log, mold-e-mold2-e (last steps)

- bucket: fill lava at -253, 71, -115 from -256, 72, -115 hand=lava_bucket accepted=true dist=3.5
- bucket: fill water at -251, -54, -162 from -249, -53, -163 hand=water_bucket accepted=true dist=3.2

## Portal cast log, portal-b-pool2-b (last steps)

- bucket: fill water at -7, 123, -1 from -6, 123, 1 hand=bucket accepted=true dist=2.5
- bucket: fill lava at 8, 123, -1 from 5, 124, 0 hand=lava_bucket accepted=true dist=3.7
