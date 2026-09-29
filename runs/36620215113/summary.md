# Batch 36620215113 @ 4b6c3a7

## Milestones (game time since the autopilot started)

| run | m4 | m6 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| diamond-a-dia2-a | 0:00 | 3:42 | 7:17 | onFire, mob:zombie, mob:zombie, mob:zombie, arrow:skeleton, mob:spider | 39:30 | blaze_rods: enter_portal nether |
| diamond-b-dia2-b | 0:00 | 3:10 | - | lava | 39:30 | nether_portal: diamond_hunt 3 |
| diamond-c-dia2-c | 0:00 | - | - | indirectMagic:guardian, indirectMagic:guardian, trident:drowned, explosion.player:creeper, arrow:skeleton, mob:zombie, arrow:skeleton | 39:30 | nether_portal: collect raw_iron:13 |
| diamond-h-dia2-h | 0:00 | 2:53 | - | lava, arrow:skeleton | 39:30 | nether_portal: collect raw_iron:13 |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| diamond-a-dia2-a | 0:00 | 0:00 | 2:30 | 7:08 | 7:10 | 7:11 |
| diamond-b-dia2-b | 0:00 | 0:00 | 2:36 | - | - | - |
| diamond-c-dia2-c | 0:00 | 0:00 | - | - | - | - |
| diamond-h-dia2-h | 0:00 | 0:00 | 2:04 | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x mob:zombie (attack zombie 4)
- 4 x arrow:skeleton (retreat 2, attack skeleton 1, goto surface 1)
- 2 x lava (idle 2)
- 2 x indirectMagic:guardian (retreat 1, pickup 1)
- 1 x onFire (fortress blazes 1)
- 1 x mob:spider (attack spider 1)
- 1 x trident:drowned (retreat 1)
- 1 x explosion.player:creeper (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| diamond-a-dia2-a | 37 | 6 | 60 | 1165 | 272 | 0 | 346 | 490 | 0 |
| diamond-b-dia2-b | 630 | 174 | 212 | 640 | 139 | 50 | 5 | 541 | 0 |
| diamond-c-dia2-c | 724 | 38 | 25 | 902 | 152 | 0 | 533 | 0 | 0 |
| diamond-h-dia2-h | 399 | 315 | 10 | 511 | 80 | 0 | 875 | 198 | 0 |

## Top failures (action + code, count, one example)

- 16 x collect log NOT_FOUND  (e.g. diamond-c-dia2-c: no log seen nearby)
- 15 x fortress blazes NOT_FOUND  (e.g. diamond-a-dia2-a: no blazes in the parts of the fortress we know)
- 10 x collect log WRONG_PLACE  (e.g. diamond-c-dia2-c: in water: log is dug from dry ground)
- 9 x craft torch NO_RECIPE  (e.g. diamond-b-dia2-b: no known recipe for torch (not unlocked yet?))
- 7 x goto surface UNREACHABLE  (e.g. diamond-a-dia2-a: couldn't find the way up)
- 7 x explore log HAZARD  (e.g. diamond-c-dia2-c: open water ahead; will turn)
- 6 x explore any UNREACHABLE  (e.g. diamond-a-dia2-a: couldn't make headway that way; will turn)
- 6 x attack zombie NOT_FOUND  (e.g. diamond-a-dia2-a: no reachable zombie in sight)
- 6 x obsidian_pool NOT_FOUND  (e.g. diamond-b-dia2-b: make_obsidian obsidian failed: no lava source we can safely pour water next to)
- 6 x explore any HAZARD  (e.g. diamond-c-dia2-c: open water ahead; will turn)

## Portal cast log, diamond-a-dia2-a (last steps)

- bucket: fill lava at 74, -55, 35 from 73, -53, 36 hand=lava_bucket accepted=true dist=3.1
- bucket: fill lava at 74, -55, 34 from 73, -53, 36 hand=lava_bucket accepted=true dist=3.5
- bucket: fill lava at 55, -55, 26 from 52, -54, 27 hand=lava_bucket accepted=true dist=3.8

## Portal cast log, diamond-b-dia2-b (last steps)

- bucket: fill lava at -2, -55, 42 from -2, -54, 40 hand=lava_bucket accepted=true dist=2.7
- bucket: fill lava at -1, -55, 42 from -2, -54, 40 hand=lava_bucket accepted=true dist=2.9
- bucket: fill lava at -2, -55, 41 from -2, -54, 40 hand=lava_bucket accepted=true dist=2.1
- bucket: fill lava at -1, -55, 41 from -2, -54, 40 hand=lava_bucket accepted=true dist=2.4
- bucket: fill lava at -3, -55, 41 from -2, -54, 40 hand=lava_bucket accepted=true dist=2.2
- bucket: fill water at -3, 64, 56 from -2, 66, 54 hand=bucket accepted=true dist=3.6
