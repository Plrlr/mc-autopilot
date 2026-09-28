# Batch 36462566573 @ f74006d

## Milestones (game time since the autopilot started)

| run | m2 | deaths | length | ended doing |
|---|---|---|---|---|
| combat-drill-a-combat-off | 0:00 | 0 | ? | iron_tools: collect raw_iron:10 |
| combat-drill-a-combat-on | 0:00 | mob, mob | ? | iron_tools: attack skeleton |
| natural-nat-a-natural-all | - | 0 | ? | no FINAL line |
| **natural: reached, median** | 0/1 - | 0 (0.0 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 2 x mob (shelter heal 1, attack zombie 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| combat-drill-a-combat-off | 208 | 0 | 0 | 0 | 151 | 0 | 0 | 0 | 0 |
| combat-drill-a-combat-on | 162 | 7 | 1 | 0 | 153 | 0 | 29 | 0 | 0 |
| natural-nat-a-natural-all | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## Top failures (action + code, count, one example)

- 18 x attack skeleton UNREACHABLE  (e.g. combat-drill-a-combat-on: can't reach the skeleton (7 blocks away))
- 5 x retreat NO_PROGRESS  (e.g. combat-drill-a-combat-off: couldn't get away (nearest monster 7 blocks))
- 3 x attack zombie NOT_FOUND  (e.g. combat-drill-a-combat-on: no reachable zombie in sight)
- 2 x attack skeleton NOT_FOUND  (e.g. combat-drill-a-combat-on: no reachable skeleton in sight)
- 1 x attack spider UNREACHABLE  (e.g. combat-drill-a-combat-off: can't reach the spider (3 blocks away))
- 1 x attack creeper HAZARD  (e.g. combat-drill-a-combat-on: creeper started its fuse before the hit)
