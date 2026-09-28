# Batch 36447868962 @ a63d44e

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-p1-shore-p1 | 0:40 | 2:09 | 18:59 | 0 | 19:30 | nether_portal: collect raw_iron:9 |
| natural-p2-shore-p2 | 0:17 | 1:00 | 5:54 | arrow | 19:30 | nether_portal: goto death |
| natural-p3-shore-p3 | 0:19 | - | - | 0 | 19:30 | stone_tools: shore |
| natural-p4-shore-p4 | 2:55 | - | - | 0 | 19:30 | stone_tools: shore |
| natural-p5-shore-p5 | 0:20 | 1:10 | 5:43 | 0 | 19:30 | nether_portal: collect raw_iron:7 |
| natural-p6-shore-p6 | - | - | - | mob | 19:30 | wood_tools: shore |
| **natural: reached, median** | 5/6 0:30* | 3/6 11:04* | 3/6 19:29* | 2 (0.3 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-p1-shore-p1 | - | - | - | - | - | - |
| natural-p2-shore-p2 | 15:14 | 15:13 | 19:10 | - | - | - |
| natural-p3-shore-p3 | - | - | - | - | - | - |
| natural-p4-shore-p4 | - | - | - | - | - | - |
| natural-p5-shore-p5 | - | - | - | - | - | - |
| natural-p6-shore-p6 | - | - | 4:37 | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x arrow (attack spider 1)
- 1 x mob (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-p1-shore-p1 | 275 | 235 | 128 | 201 | 16 | 0 | 210 | 132 | 0 |
| natural-p2-shore-p2 | 707 | 160 | 21 | 257 | 38 | 0 | 0 | 11 | 0 |
| natural-p3-shore-p3 | 59 | 11 | 0 | 83 | 0 | 0 | 0 | 1046 | 0 |
| natural-p4-shore-p4 | 54 | 7 | 0 | 620 | 0 | 0 | 0 | 517 | 0 |
| natural-p5-shore-p5 | 653 | 60 | 0 | 240 | 0 | 0 | 0 | 189 | 26 |
| natural-p6-shore-p6 | 39 | 0 | 1 | 837 | 73 | 0 | 42 | 203 | 0 |

## Top failures (action + code, count, one example)

- 20 x collect log NOT_FOUND  (e.g. natural-p2-shore-p2: no log seen nearby)
- 14 x craft stone_pickaxe NO_ROOM  (e.g. natural-p3-shore-p3: no room to place a crafting_table)
- 11 x smelt iron_ingot UNREACHABLE  (e.g. natural-p1-shore-p1: couldn't reach the furnace)
- 7 x goto surface UNREACHABLE  (e.g. natural-p1-shore-p1: couldn't find the way up)
- 7 x smelt cooked_beef UNREACHABLE  (e.g. natural-p1-shore-p1: couldn't reach the furnace)
- 6 x retreat NO_PROGRESS  (e.g. natural-p1-shore-p1: couldn't get away (nearest monster 7 blocks))
- 5 x explore log ALREADY_DONE  (e.g. natural-p4-shore-p4: already see log)
- 3 x collect raw_iron STUCK  (e.g. natural-p1-shore-p1: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x shore STUCK  (e.g. natural-p1-shore-p1: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x attack spider NOT_FOUND  (e.g. natural-p2-shore-p2: no reachable spider in sight)
