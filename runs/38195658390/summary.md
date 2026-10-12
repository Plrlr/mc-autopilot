# Batch 38195658390 @ e7dc126

## Milestones (game time since the autopilot started)

| run | m2 | deaths | length | ended doing |
|---|---|---|---|---|
| combat-drill-0-0-drill-0-off-0 | 0:00 | mob:zombie, arrow:skeleton, fall, mob:zombie, mob:zombie | ? | iron_tools: attack spider |
| combat-drill-0-0-drill-0-on-0 | 0:00 | 0 | ? | iron_tools: shelter |
| combat-drill-0-1-drill-0-off-1 | 0:00 | 0 | ? | iron_tools: attack skeleton |
| combat-drill-0-1-drill-0-on-1 | 0:00 | 0 | ? | iron_tools: collect raw_iron:13 |
| combat-drill-0-2-drill-0-off-2 | 0:01 | 0 | ? | iron_tools: attack skeleton |
| combat-drill-0-2-drill-0-on-2 | 0:00 | 0 | ? | iron_tools: attack zombie |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| combat-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| combat-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| combat-drill-0-1-drill-0-off-1 | - | - | 1:03 | - | - | - |
| combat-drill-0-1-drill-0-on-1 | - | - | 1:18 | - | - | - |
| combat-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| combat-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob:zombie (attack zombie 3)
- 1 x arrow:skeleton (attack skeleton 1)
- 1 x fall (attack spider 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| combat-drill-0-0-drill-0-off-0 | 306 | 27 | 9 | 0 | 205 | 0 | 32 | 3 | 0 |
| combat-drill-0-0-drill-0-on-0 | 332 | 0 | 0 | 0 | 182 | 0 | 34 | 50 | 0 |
| combat-drill-0-1-drill-0-off-1 | 324 | 0 | 0 | 0 | 233 | 0 | 10 | 30 | 0 |
| combat-drill-0-1-drill-0-on-1 | 287 | 0 | 0 | 18 | 241 | 0 | 0 | 52 | 0 |
| combat-drill-0-2-drill-0-off-2 | 282 | 0 | 0 | 0 | 318 | 0 | 0 | 0 | 0 |
| combat-drill-0-2-drill-0-on-2 | 287 | 0 | 0 | 18 | 293 | 0 | 0 | 0 | 0 |

## Top failures (action + code, count, one example)

- 10 x attack creeper HAZARD  (e.g. combat-drill-0-0-drill-0-off-0: no safe gap after hitting the creeper)
- 3 x attack zombie NOT_FOUND  (e.g. combat-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 3 x retreat NO_PROGRESS  (e.g. combat-drill-0-0-drill-0-off-0: couldn't get away (nearest monster 9 blocks))
- 3 x attack skeleton UNREACHABLE  (e.g. combat-drill-0-0-drill-0-on-0: lost sight of the skeleton)
- 3 x shore STUCK  (e.g. combat-drill-0-1-drill-0-off-1: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x attack skeleton NOT_FOUND  (e.g. combat-drill-0-0-drill-0-off-0: no reachable skeleton in sight)
- 2 x attack zombie UNREACHABLE  (e.g. combat-drill-0-0-drill-0-off-0: lost sight of the zombie)
- 2 x collect raw_iron STUCK  (e.g. combat-drill-0-0-drill-0-on-0: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x collect log NOT_FOUND  (e.g. combat-drill-0-2-drill-0-off-2: no log seen nearby)
- 1 x shelter USE_FAILED  (e.g. combat-drill-0-0-drill-0-off-0: couldn't dig down)
