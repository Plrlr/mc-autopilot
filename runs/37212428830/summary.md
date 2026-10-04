# Batch 37212428830 @ edb493e

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| combat-rg-c0-reg-main | - | 0:01 | - | mob:zombie, mob:zombie | ? | iron_tools: attack creeper |
| natural-rg-n0-reg-main | 0:14 | 2:30 | - | mob:zombie_villager, mob:zombie, fall, mob:zombie | 19:30 | iron_tools: collect raw_iron:13 |
| natural-rg-n1-reg-main | 0:20 | 2:11 | - | 0 | 19:30 | iron_tools: shore |
| natural-rg-n2-reg-main | 0:15 | 2:43 | 5:04 | 0 | 19:30 | nether_portal: shore |
| **natural: reached, median** | 3/3 0:15 | 3/3 2:30 | 1/3 20:00* | 4 (1.3 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| combat-rg-c0-reg-main | - | - | - | - | - | - |
| natural-rg-n0-reg-main | - | - | 19:48 | - | - | - |
| natural-rg-n1-reg-main | - | - | - | - | - | - |
| natural-rg-n2-reg-main | 12:46 | 12:44 | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x mob:zombie (attack zombie 2, retreat 2)
- 1 x mob:zombie_villager (attack zombie 1)
- 1 x fall (collect raw_iron 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| combat-rg-c0-reg-main | 239 | 13 | 0 | 0 | 341 | 0 | 0 | 0 | 0 |
| natural-rg-n0-reg-main | 694 | 137 | 43 | 89 | 187 | 0 | 36 | 0 | 0 |
| natural-rg-n1-reg-main | 282 | 27 | 0 | 0 | 0 | 0 | 0 | 734 | 125 |
| natural-rg-n2-reg-main | 443 | 343 | 1 | 265 | 40 | 7 | 0 | 98 | 0 |

## Top failures (action + code, count, one example)

- 7 x attack creeper HAZARD  (e.g. natural-rg-n0-reg-main: no safe gap after hitting the creeper)
- 6 x attack zombie NOT_FOUND  (e.g. combat-rg-c0-reg-main: no reachable zombie in sight)
- 4 x retreat NO_PROGRESS  (e.g. natural-rg-n0-reg-main: couldn't get away (nearest monster 8 blocks))
- 3 x attack zombie_villager NOT_FOUND  (e.g. natural-rg-n0-reg-main: no reachable zombie_villager in sight)
- 3 x craft torch NO_RECIPE  (e.g. natural-rg-n1-reg-main: no known recipe for torch (not unlocked yet?))
- 1 x attack skeleton UNREACHABLE  (e.g. combat-rg-c0-reg-main: can't reach the skeleton (72 blocks away))
- 1 x shelter USE_FAILED  (e.g. natural-rg-n0-reg-main: couldn't dig down)
- 1 x attack zombie UNREACHABLE  (e.g. natural-rg-n0-reg-main: lost sight of the zombie)
- 1 x collect flint NO_ROOM  (e.g. natural-rg-n1-reg-main: no free flat spot beside us for the gravel)
- 1 x collect coal NO_PROGRESS  (e.g. natural-rg-n1-reg-main: found only 1 coal)

## Portal cast log, natural-rg-n2-reg-main (last steps)

- bucket: fill water at 898, 13, 126 from 898, 15, 124 hand=bucket accepted=true dist=3.4
