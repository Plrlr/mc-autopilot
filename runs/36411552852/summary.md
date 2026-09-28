# Batch 36411552852 @ 7b7536b

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 1:15 | 4:04 | 10:59 | 0 | 11:30 | nether_portal: collect raw_iron:5 |
| natural-b | 0:42 | 2:00 | 7:02 | arrow | 11:30 | nether_portal: collect raw_iron:13 |
| natural-c-log | - | - | - | 0 | ? | wood_tools: idle |
| natural-d-stone | 0:00 | - | - | 0 | ? | stone_tools: idle |
| natural-e-iron | - | 0:00 | - | 0 | 1:00 | iron_tools: attack zombie |
| **natural: reached, median** | 2/2 0:58 | 2/2 3:02 | 2/2 9:00 | 1 (0.5 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 1 x arrow (attack skeleton 1)

## Task tests

- natural-c-log: collect log:5 -> failed NOT_FOUND: no log seen nearby after 0 s
- natural-d-stone: collect stone:8 -> ok: collected 8 stone after 15 s
- natural-e-iron: collect raw_iron:3 -> failed INTERRUPTED: interrupted by reflex_fight after 66 s

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 462 | 69 | 74 | 90 | 15 | 0 | 4 | 2 | 0 |
| natural-b | 494 | 131 | 0 | 57 | 9 | 0 | 0 | 25 | 0 |
| natural-c-log | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-d-stone | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-e-iron | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## Top failures (action + code, count, one example)

- 4 x collect log NOT_FOUND  (e.g. natural-a: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-a: already see log)
- 2 x collect raw_iron STUCK  (e.g. natural-a: stuck: stayed inside 2.0 blocks for 12 s)
- 1 x retreat TIMEOUT  (e.g. natural-a: timed out after 15 s)
- 1 x smelt iron_ingot NO_PROGRESS  (e.g. natural-a: furnace stopped (out of fuel?))
- 1 x collect flint STUCK  (e.g. natural-b: stuck: stayed inside 2.0 blocks for 12 s)
- 1 x collect stone STUCK  (e.g. natural-b: stuck: stayed inside 2.0 blocks for 12 s)
