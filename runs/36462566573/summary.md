# Batch 36462566573 @ f74006d

## Milestones (game time since the autopilot started)

| run | m1 | m2 | deaths | length | ended doing |
|---|---|---|---|---|---|
| combat-drill-a-combat-off | - | 0:00 | 0 | ? | iron_tools: collect raw_iron:10 |
| combat-drill-a-combat-on | - | 0:00 | mob, mob | ? | iron_tools: attack skeleton |
| natural-nat-a-natural-all | 0:17 | 2:13 | arrow, mob, mob, arrow | 11:30 | iron_tools: unstuck |
| **natural: reached, median** | 1/1 0:17 | 1/1 2:13 | 4 (4.0 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 4 x mob (attack zombie 3, shelter heal 1)
- 2 x arrow (attack skeleton 2)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| combat-drill-a-combat-off | 208 | 0 | 0 | 0 | 151 | 0 | 0 | 0 | 0 |
| combat-drill-a-combat-on | 162 | 7 | 1 | 0 | 153 | 0 | 29 | 0 | 0 |
| natural-nat-a-natural-all | 285 | 160 | 55 | 28 | 79 | 0 | 19 | 78 | 0 |

## Top failures (action + code, count, one example)

- 18 x attack skeleton UNREACHABLE  (e.g. combat-drill-a-combat-on: can't reach the skeleton (7 blocks away))
- 7 x attack skeleton NOT_FOUND  (e.g. combat-drill-a-combat-on: no reachable skeleton in sight)
- 6 x attack zombie NOT_FOUND  (e.g. combat-drill-a-combat-on: no reachable zombie in sight)
- 5 x retreat NO_PROGRESS  (e.g. combat-drill-a-combat-off: couldn't get away (nearest monster 7 blocks))
- 3 x craft stone_pickaxe UNREACHABLE  (e.g. natural-nat-a-natural-all: couldn't reach open ground to place a crafting_table)
- 3 x shelter NO_ROOM  (e.g. natural-nat-a-natural-all: no safe ground to dig into nearby)
- 2 x collect stone STUCK  (e.g. natural-nat-a-natural-all: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x unstuck STUCK  (e.g. natural-nat-a-natural-all: boxed in: can't walk, tunnel or climb out)
- 2 x shelter heal HAZARD  (e.g. natural-nat-a-natural-all: hit while hiding (health 13))
- 1 x attack spider UNREACHABLE  (e.g. combat-drill-a-combat-off: can't reach the spider (3 blocks away))
