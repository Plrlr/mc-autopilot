# Batch 41086251401 @ 79f9dee

## Milestones (game time since the autopilot started)

| run | m2 | deaths | length | ended doing |
|---|---|---|---|---|
| combat-drill-0-0-drill-0-off-0 | 0:00 | mob:zombie, mob:zombie, mob:zombie | ? | iron_tools: collect log:3 |
| combat-drill-0-0-drill-0-on-0 | 0:00 | mob:zombie, mob:zombie, mob:zombie | ? | iron_tools: attack skeleton |
| combat-drill-0-1-drill-0-off-1 | 0:00 | 0 | ? | iron_tools: retreat |
| combat-drill-0-1-drill-0-on-1 | 0:00 | 0 | ? | iron_tools: collect raw_iron:13 |
| combat-drill-0-2-drill-0-off-2 | 0:01 | 0 | ? | iron_tools: attack skeleton |
| combat-drill-0-2-drill-0-on-2 | 0:00 | mob:zombie | ? | iron_tools: attack skeleton |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| combat-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| combat-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| combat-drill-0-1-drill-0-off-1 | - | - | 1:40 | - | - | - |
| combat-drill-0-1-drill-0-on-1 | - | - | 1:33 | - | - | - |
| combat-drill-0-2-drill-0-off-2 | - | - | 7:16 | - | - | - |
| combat-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 7 x mob:zombie (attack zombie 7)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| combat-drill-0-0-drill-0-off-0 | 425 | 25 | 5 | 0 | 117 | 0 | 0 | 17 | 0 |
| combat-drill-0-0-drill-0-on-0 | 138 | 13 | 3 | 0 | 310 | 0 | 125 | 0 | 0 |
| combat-drill-0-1-drill-0-off-1 | 322 | 0 | 0 | 13 | 260 | 0 | 0 | 4 | 0 |
| combat-drill-0-1-drill-0-on-1 | 331 | 0 | 2 | 13 | 241 | 0 | 0 | 11 | 0 |
| combat-drill-0-2-drill-0-off-2 | 411 | 0 | 0 | 0 | 185 | 0 | 5 | 0 | 0 |
| combat-drill-0-2-drill-0-on-2 | 273 | 0 | 0 | 0 | 257 | 0 | 65 | 0 | 0 |

## Top failures (action + code, count, one example)

- 16 x retreat NO_PROGRESS  (e.g. combat-drill-0-0-drill-0-on-0: couldn't get away (nearest monster 8 blocks))
- 14 x attack zombie NOT_FOUND  (e.g. combat-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 2 x attack zombie UNREACHABLE  (e.g. combat-drill-0-2-drill-0-off-2: no sightline to the zombie)
- 1 x collect raw_iron STUCK  (e.g. combat-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 1 x shelter PLACE_FAILED  (e.g. combat-drill-0-2-drill-0-off-2: couldn't cover the shelter)
- 1 x collect log NOT_FOUND  (e.g. combat-drill-0-2-drill-0-on-2: no log seen nearby)
