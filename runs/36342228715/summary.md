# Batch 36342228715 @ 14e03e3

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m7 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-c | 2:07 | 3:17 | 8:35 | - | mob, arrow | 9:30 | nether_portal: collect log:10 |
| natural-e | 0:23 | 1:18 | 5:59 | - | 0 | 9:30 | nether_portal: collect raw_iron:5 |
| nether-a-sight | - | - | - | 0:00 | lava | 9:30 | nether_portal: smelt iron_ingot:5 |
| **natural: reached, median** | 2/2 1:15 | 2/2 2:17 | 2/2 7:17 | 0/2 - | 2 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-c | - | - | - | - | - | - |
| natural-e | - | - | - | - | - | - |
| nether-a-sight | - | 0:00 | 0:02 | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x mob (attack zombie 1)
- 1 x arrow (attack zombie 1)
- 1 x lava (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-c | 255 | 130 | 67 | 85 | 40 | 0 | 0 | 14 | 0 |
| natural-e | 438 | 66 | 0 | 55 | 0 | 0 | 0 | 39 | 0 |
| nether-a-sight | 245 | 201 | 1 | 0 | 14 | 0 | 0 | 131 | 0 |

## Top failures (action + code, count, one example)

- 3 x collect log STUCK  (e.g. natural-c: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x collect flint STUCK  (e.g. natural-e: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x attack skeleton NOT_FOUND  (e.g. natural-c: no reachable skeleton in sight)
- 2 x collect stone STUCK  (e.g. natural-c: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x goto surface UNREACHABLE  (e.g. natural-c: couldn't find the way up)
