# Batch 36348302856 @ 55d6b07

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-a-surv | 0:21 | 1:07 | 4:37 | - | 0 | 14:30 | nether_portal: collect raw_iron:5 |
| natural-d-surv2 | 0:20 | 0:59 | 6:34 | - | arrow | 14:30 | nether_portal: goto death |
| nether-a-ghast | - | - | - | 0:00 | lava, mob | 9:30 | nether_portal: collect raw_iron:13 |
| **natural: reached, median** | 2/2 0:20 | 2/2 1:03 | 2/2 5:35 | 0/2 - | 1 (0.5 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a-surv | - | - | - | - | - | - |
| natural-d-surv2 | - | - | 8:26 | - | - | - |
| nether-a-ghast | - | 0:00 | 0:01 | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x arrow (retreat 1)
- 1 x lava (eat 1)
- 1 x mob (attack cave_spider 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a-surv | 423 | 171 | 17 | 33 | 42 | 0 | 193 | 19 | 0 |
| natural-d-surv2 | 285 | 117 | 18 | 361 | 61 | 0 | 46 | 5 | 0 |
| nether-a-ghast | 241 | 24 | 13 | 123 | 48 | 0 | 0 | 140 | 0 |

## Top failures (action + code, count, one example)

- 5 x collect log STUCK  (e.g. natural-a-surv: stuck: stayed inside 2.0 blocks for 12 s)
- 5 x collect log NOT_FOUND  (e.g. nether-a-ghast: no log in sight underground)
- 5 x explore log ALREADY_DONE  (e.g. nether-a-ghast: already see log)
- 3 x retreat NO_PROGRESS  (e.g. natural-d-surv2: couldn't get away (nearest monster 4 blocks))
- 3 x attack zombie NOT_FOUND  (e.g. natural-d-surv2: no reachable zombie in sight)
- 3 x attack cave_spider NOT_FOUND  (e.g. nether-a-ghast: no reachable cave_spider in sight)
- 2 x collect stone STUCK  (e.g. natural-a-surv: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x collect raw_iron STUCK  (e.g. natural-a-surv: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x goto surface UNREACHABLE  (e.g. natural-a-surv: couldn't find the way up)
- 1 x explore cow,pig,sheep,chicken HAZARD  (e.g. natural-d-surv2: open water ahead; will turn)
