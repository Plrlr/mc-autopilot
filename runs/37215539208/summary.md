# Batch 37215539208 @ 9149ab5

## Milestones (game time since the autopilot started)

| run | m7 | m8 | m9 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| nether_fortress-fv-0-0-drill-0-off-0 | 0:00 | - | - | onFire | 11:30 | nether_portal: smelt iron_ingot:4 |
| nether_fortress-fv-0-0-drill-0-on-0 | 0:00 | - | - | onFire | 11:30 | nether_portal: fill_bucket water |
| nether_fortress-fv-0-1-drill-0-off-1 | 0:00 | - | - | mob:magma_cube | 11:30 | nether_portal: smelt iron_ingot:8 |
| nether_fortress-fv-0-1-drill-0-on-1 | 0:00 | - | - | fall, arrow:skeleton | 11:30 | nether_portal: pickup stations |
| nether_fortress-fv-1-0-drill-1-off-0 | 0:00 | - | - | inFire, arrow:skeleton | 11:30 | nether_portal: collect log:3 |
| nether_fortress-fv-1-0-drill-1-on-0 | 0:00 | 0:42 | - | mob:magma_cube | 11:30 | ender_pearls: attack pig |
| nether_fortress-fv-1-1-drill-1-off-1 | 0:00 | 5:44 | - | onFire, arrow:skeleton | 11:30 | ender_pearls: explore enderman |
| nether_fortress-fv-1-1-drill-1-on-1 | 0:00 | 0:44 | 2:45 | mob:piglin_brute, arrow:skeleton, mob:zombie | 11:30 | eyes_of_ender: shelter |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| nether_fortress-fv-0-0-drill-0-off-0 | - | 0:00 | - | - | - | - |
| nether_fortress-fv-0-0-drill-0-on-0 | 11:16 | 0:00 | - | - | - | - |
| nether_fortress-fv-0-1-drill-0-off-1 | - | 0:00 | 0:45 | - | - | - |
| nether_fortress-fv-0-1-drill-0-on-1 | - | 0:00 | 0:38 | - | - | - |
| nether_fortress-fv-1-0-drill-1-off-0 | - | 0:00 | 0:07 | - | - | - |
| nether_fortress-fv-1-0-drill-1-on-0 | - | 0:00 | 0:52 | - | - | - |
| nether_fortress-fv-1-1-drill-1-off-1 | - | 0:00 | 0:52 | - | - | - |
| nether_fortress-fv-1-1-drill-1-on-1 | - | 0:00 | 0:52 | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x arrow:skeleton (shelter heal 1, smelt iron_ingot 1, attack skeleton 1, shelter 1)
- 3 x onFire (fortress blazes 2, fortress find 1)
- 2 x mob:magma_cube (retreat 2)
- 1 x fall (explore any 1)
- 1 x inFire (fortress blazes 1)
- 1 x mob:piglin_brute (explore blaze 1)
- 1 x mob:zombie (attack zombie 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| nether_fortress-fv-0-0-drill-0-off-0 | 326 | 279 | 0 | 37 | 2 | 0 | 0 | 71 | 0 |
| nether_fortress-fv-0-0-drill-0-on-0 | 331 | 324 | 0 | 0 | 15 | 38 | 0 | 8 | 0 |
| nether_fortress-fv-0-1-drill-0-off-1 | 399 | 96 | 0 | 6 | 12 | 0 | 0 | 199 | 0 |
| nether_fortress-fv-0-1-drill-0-on-1 | 128 | 19 | 27 | 353 | 34 | 0 | 6 | 143 | 0 |
| nether_fortress-fv-1-0-drill-1-off-0 | 455 | 165 | 1 | 18 | 55 | 0 | 0 | 17 | 0 |
| nether_fortress-fv-1-0-drill-1-on-0 | 0 | 0 | 19 | 605 | 37 | 0 | 0 | 54 | 0 |
| nether_fortress-fv-1-1-drill-1-off-1 | 0 | 0 | 1 | 165 | 47 | 0 | 0 | 499 | 0 |
| nether_fortress-fv-1-1-drill-1-on-1 | 0 | 0 | 32 | 474 | 98 | 0 | 22 | 83 | 0 |

## Top failures (action + code, count, one example)

- 23 x fortress blazes NOT_FOUND  (e.g. nether_fortress-fv-0-1-drill-0-off-1: no blazes in the parts of the fortress we know)
- 6 x shore STUCK  (e.g. nether_fortress-fv-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 5 x attack blaze NOT_FOUND  (e.g. nether_fortress-fv-1-0-drill-1-off-0: no reachable blaze in sight)
- 5 x attack skeleton NOT_FOUND  (e.g. nether_fortress-fv-1-0-drill-1-off-0: no reachable skeleton in sight)
- 4 x attack creeper HAZARD  (e.g. nether_fortress-fv-0-0-drill-0-off-0: no safe gap after hitting the creeper)
- 3 x craft torch NO_RECIPE  (e.g. nether_fortress-fv-0-1-drill-0-off-1: no known recipe for torch (not unlocked yet?))
- 3 x explore enderman STUCK  (e.g. nether_fortress-fv-1-0-drill-1-on-0: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x unstuck STUCK  (e.g. nether_fortress-fv-0-0-drill-0-off-0: couldn't get out (walked, tunnelled and climbed 0 blocks))
- 2 x retreat NO_PROGRESS  (e.g. nether_fortress-fv-0-0-drill-0-off-0: couldn't get away (nearest monster 4 blocks))
- 2 x attack magma_cube NOT_FOUND  (e.g. nether_fortress-fv-0-1-drill-0-off-1: no reachable magma_cube in sight)
