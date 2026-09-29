# Batch 36619623407 @ 42b1b40

## Milestones (game time since the autopilot started)

| run | m6 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|
| mold-a-mold3-a | 0:01 | 3:01 | mob:piglin, mob:piglin, mob:piglin, mob:piglin, spear:piglin, mob:piglin, arrow:piglin, onFire, explosion.player:ghast | 11:30 | blaze_rods: fortress blazes:8 |
| mold-c-mold3-c | 0:00 | - | arrow:skeleton | 11:30 | nether_portal: collect log:9 |
| mold-f-mold3-f | 0:00 | - | mob:zombie, mob:zombie, mob:zombie | 11:30 | nether_portal: shelter |
| mold-g-mold3-g | 0:01 | 2:41 | lava | 11:30 | blaze_rods: fortress find |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| mold-a-mold3-a | 0:01 | 0:01 | 0:01 | 2:43 | 2:45 | 2:46 |
| mold-c-mold3-c | 0:00 | 0:00 | 1:47 | - | - | - |
| mold-f-mold3-f | 0:00 | 0:00 | 0:01 | - | - | - |
| mold-g-mold3-g | 0:01 | 0:01 | 0:02 | 2:33 | 2:34 | 2:36 |

## Deaths by cause (what the bot was doing)

- 5 x mob:piglin (fortress find 5)
- 3 x mob:zombie (attack zombie 2, clutch 1)
- 1 x spear:piglin (fortress find 1)
- 1 x arrow:piglin (fortress find 1)
- 1 x onFire (attack ghast 1)
- 1 x explosion.player:ghast (fortress find 1)
- 1 x arrow:skeleton (retreat 1)
- 1 x lava (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| mold-a-mold3-a | 0 | 0 | 11 | 390 | 1 | 0 | 0 | 289 | 0 |
| mold-c-mold3-c | 479 | 7 | 5 | 144 | 77 | 0 | 0 | 2 | 0 |
| mold-f-mold3-f | 184 | 54 | 3 | 208 | 100 | 0 | 156 | 2 | 0 |
| mold-g-mold3-g | 1 | 0 | 0 | 318 | 7 | 0 | 30 | 358 | 0 |

## Top failures (action + code, count, one example)

- 5 x attack zombie NOT_FOUND  (e.g. mold-f-mold3-f: no reachable zombie in sight)
- 3 x collect log NOT_FOUND  (e.g. mold-c-mold3-c: no log seen nearby)
- 3 x explore log HAZARD  (e.g. mold-c-mold3-c: open water ahead; will turn)
- 3 x attack skeleton NOT_FOUND  (e.g. mold-f-mold3-f: no reachable skeleton in sight)
- 3 x craft torch NO_RECIPE  (e.g. mold-f-mold3-f: no known recipe for torch (not unlocked yet?))
- 3 x collect flint NO_ROOM  (e.g. mold-f-mold3-f: no free flat spot beside us for the gravel)
- 3 x explore gravel,water ALREADY_DONE  (e.g. mold-f-mold3-f: already see gravel)
- 3 x enter_portal nether TIMEOUT  (e.g. mold-g-mold3-g: timed out after 90 s)
- 2 x fortress find UNREACHABLE  (e.g. mold-a-mold3-a: couldn't make headway that way (lava or cliffs); will turn)
- 2 x attack magma_cube NOT_FOUND  (e.g. mold-a-mold3-a: no reachable magma_cube in sight)

## Portal cast log, mold-a-mold3-a (last steps)

- bucket: fill lava at 5, 64, 10 from 2, 65, 9 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at 6, 64, 8 from 3, 65, 8 hand=lava_bucket accepted=true dist=3.6
- bucket: fill lava at 6, 64, 9 from 3, 65, 8 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at 6, 64, 10 from 3, 65, 9 hand=lava_bucket accepted=true dist=3.9
- bucket: fill lava at 5, 64, 8 from 2, 65, 8 hand=lava_bucket accepted=true dist=3.8
- bucket: fill lava at 5, 64, 9 from 2, 65, 8 hand=lava_bucket accepted=true dist=4.0
- bucket: fill lava at 7, 64, 8 from 4, 65, 8 hand=lava_bucket accepted=true dist=3.6
- bucket: fill lava at 7, 64, 10 from 4, 65, 9 hand=lava_bucket accepted=true dist=3.9

## Portal cast log, mold-g-mold3-g (last steps)

- bucket: fill lava at 14, 64, -9 from 11, 64, -8 hand=lava_bucket accepted=true dist=3.4
- bucket: fill lava at 13, 64, -10 from 11, 64, -8 hand=lava_bucket accepted=true dist=2.8
- bucket: fill lava at 14, 64, -10 from 11, 64, -8 hand=lava_bucket accepted=true dist=3.8
- bucket: fill lava at 15, 64, -9 from 12, 65, -8 hand=lava_bucket accepted=true dist=3.8
- bucket: fill lava at 14, 64, -11 from 12, 65, -9 hand=lava_bucket accepted=true dist=3.4
- bucket: fill lava at 15, 64, -10 from 12, 65, -9 hand=lava_bucket accepted=true dist=3.6
- bucket: fill lava at 15, 64, -11 from 12, 65, -10 hand=lava_bucket accepted=true dist=3.6
- bucket: fill lava at 16, 64, -9 from 13, 65, -8 hand=lava_bucket accepted=true dist=3.6
