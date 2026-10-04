# Batch 37214371518 @ edb493e

## Milestones (game time since the autopilot started)

| run | m7 | m8 | m10 | m12 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| blaze-late-blaze-0-late | 0:00 | 0:06 | - | - | onFire, explosion.player:creeper, mob:zombie | 19:30 | ender_pearls: explore enderman |
| blaze-late-blaze-1-late | 0:00 | - | - | - | inFire | 19:30 | nether_portal: smelt cooked_porkchop:4 |
| end-late-end-0-late | - | - | - | 0:00 | mob:enderman, mob:spider | 19:30 | enter_end: shore |
| end-late-end-1-late | - | - | - | 0:00 | indirectMagic:ender_dragon, arrow:skeleton, mob:zombie | 19:30 | enter_end: shore |
| stronghold-late-stronghold-0-late | - | - | 0:00 | - | trident:drowned, mob:drowned, explosion.player:creeper, arrow:parched | 19:30 | find_stronghold: explore blaze |
| stronghold-late-stronghold-1-late | - | - | 0:00 | - | 0 | 19:30 | find_stronghold: shore |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| blaze-late-blaze-0-late | - | 0:00 | - | - | - | - |
| blaze-late-blaze-1-late | - | 0:00 | - | - | - | - |
| end-late-end-0-late | - | - | - | - | - | - |
| end-late-end-1-late | - | - | - | - | - | - |
| stronghold-late-stronghold-0-late | - | - | - | - | - | - |
| stronghold-late-stronghold-1-late | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 2 x explosion.player:creeper (retreat 2)
- 2 x mob:zombie (attack spider 1, retreat 1)
- 1 x onFire (fortress blazes 1)
- 1 x inFire (fortress blazes 1)
- 1 x mob:enderman (shoot end_crystal 1)
- 1 x mob:spider (retreat 1)
- 1 x indirectMagic:ender_dragon (shoot end_crystal 1)
- 1 x arrow:skeleton (attack skeleton 1)
- 1 x trident:drowned (shore 1)
- 1 x mob:drowned (retreat 1)
- 1 x arrow:parched (explore blaze 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| blaze-late-blaze-0-late | 0 | 0 | 121 | 554 | 47 | 0 | 335 | 131 | 0 |
| blaze-late-blaze-1-late | 289 | 337 | 328 | 99 | 7 | 0 | 0 | 135 | 0 |
| end-late-end-0-late | 0 | 1 | 95 | 682 | 86 | 0 | 16 | 312 | 0 |
| end-late-end-1-late | 0 | 1 | 1 | 481 | 79 | 0 | 292 | 334 | 0 |
| stronghold-late-stronghold-0-late | 0 | 1 | 3 | 879 | 218 | 0 | 18 | 64 | 0 |
| stronghold-late-stronghold-1-late | 40 | 6 | 21 | 1038 | 41 | 0 | 9 | 42 | 0 |

## Top failures (action + code, count, one example)

- 6 x attack blaze NOT_FOUND  (e.g. blaze-late-blaze-0-late: no reachable blaze in sight)
- 6 x attack zombie NOT_FOUND  (e.g. blaze-late-blaze-0-late: no reachable zombie in sight)
- 6 x craft torch NO_RECIPE  (e.g. blaze-late-blaze-1-late: no known recipe for torch (not unlocked yet?))
- 5 x retreat NO_PROGRESS  (e.g. end-late-end-0-late: couldn't get away (nearest monster 8 blocks))
- 5 x attack drowned UNREACHABLE  (e.g. stronghold-late-stronghold-0-late: can't reach the drowned (5 blocks away))
- 3 x collect log NOT_FOUND  (e.g. blaze-late-blaze-1-late: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. blaze-late-blaze-1-late: already see log)
- 3 x attack spider NOT_FOUND  (e.g. end-late-end-0-late: no reachable spider in sight)
- 3 x shelter NO_ROOM  (e.g. stronghold-late-stronghold-0-late: no safe ground to dig into nearby)
- 3 x shelter NEED_ITEM  (e.g. stronghold-late-stronghold-0-late: no block to cover the hole)
