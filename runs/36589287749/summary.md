# Batch 36589287749 @ 47d5155

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-sp1-base | 0:19 | 4:14 | 9:28 | 0 | 9:30 | nether_portal: collect log:9 |
| natural-sp1-lp2 | 0:16 | 1:37 | 7:15 | mob | 9:30 | nether_portal: collect coal:6 |
| natural-sp1-lp2r4 | 0:18 | 1:46 | - | mob | 9:30 | iron_tools: collect raw_iron:13 |
| natural-sp2-base | 0:18 | - | - | 0 | 9:30 | stone_tools: explore any |
| natural-sp2-lp2 | 1:10 | 3:45 | - | 0 | 9:30 | iron_tools: collect flint:1 |
| natural-sp2-lp2r4 | 0:21 | 6:05 | - | 0 | 9:30 | iron_tools: unstuck |
| **natural: reached, median** | 6/6 0:18 | 5/6 3:59* | 2/6 20:00* | 2 (0.3 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 2 x mob (attack zombie 2)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-sp1-base | 394 | 164 | 0 | 24 | 0 | 0 | 0 | 12 | 0 |
| natural-sp1-lp2 | 291 | 187 | 0 | 84 | 21 | 0 | 0 | 12 | 0 |
| natural-sp1-lp2r4 | 330 | 27 | 0 | 79 | 16 | 0 | 0 | 141 | 0 |
| natural-sp2-base | 216 | 9 | 0 | 328 | 0 | 0 | 0 | 20 | 0 |
| natural-sp2-lp2 | 545 | 25 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| natural-sp2-lp2r4 | 325 | 14 | 0 | 40 | 0 | 0 | 0 | 219 | 0 |

## Top failures (action + code, count, one example)

- 24 x craft torch NO_RECIPE  (e.g. natural-sp2-base: no known recipe for torch (not unlocked yet?))
- 16 x craft stone_pickaxe NO_ROOM  (e.g. natural-sp2-base: no room to place a crafting_table)
- 15 x explore any HAZARD  (e.g. natural-sp2-base: open water ahead; will turn)
- 11 x collect raw_iron STUCK  (e.g. natural-sp1-base: stuck: stayed inside 2.0 blocks for 12 s)
- 11 x collect coal STUCK  (e.g. natural-sp2-base: stuck: stayed inside 2.0 blocks for 12 s)
- 7 x unstuck STUCK  (e.g. natural-sp1-lp2r4: couldn't get out (walked, tunnelled and climbed 0 blocks))
- 7 x explore any STUCK  (e.g. natural-sp1-lp2r4: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x attack zombie NOT_FOUND  (e.g. natural-sp1-lp2: no reachable zombie in sight)
- 3 x goto surface UNREACHABLE  (e.g. natural-sp1-lp2: couldn't find the way up)
- 3 x collect stone STUCK  (e.g. natural-sp1-lp2r4: stuck: stayed inside 2.0 blocks for 12 s)
