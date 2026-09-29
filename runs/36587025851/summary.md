# Batch 36587025851 @ 9c5a7ea

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-mv1-moveOFF | 0:26 | 4:10 | - | 0 | 24:30 | iron_tools: goto surface |
| natural-mv1-moveON | 0:19 | 13:00 | 18:34 | mob, mob, mob, mob | 24:30 | nether_portal: goto surface |
| natural-mv2-moveOFF | 0:39 | 2:55 | 14:01 | mob, arrow, mob | 24:30 | nether_portal: collect raw_iron:4 |
| natural-mv2-moveON | 0:26 | 3:00 | 9:07 | mob, mob, arrow | 24:30 | nether_portal: collect coal:6 |
| natural-mv3-moveOFF | 10:40 | 13:16 | 20:09 | mob, mob, mob, explosion.player, mob, arrow, mob | 24:30 | nether_portal: collect stone:13 |
| natural-mv3-moveON | 0:17 | 3:52 | - | 0 | 24:30 | iron_tools: goto surface |
| natural-mv4-moveOFF | 0:23 | 1:33 | - | explosion.player | 24:30 | iron_tools: collect coal:6 |
| natural-mv4-moveON | 0:24 | 1:20 | 9:17 | mob, lava, mob, arrow | 24:30 | nether_portal: retreat |
| natural-mv5-moveOFF | 15:37 | 20:36 | - | mob | 24:30 | iron_tools: collect coal:6 |
| natural-mv5-moveON | 16:15 | 24:29 | - | 0 | 24:30 | iron_tools: collect raw_iron:13 |
| natural-mv6-moveOFF | - | - | - | 0 | 24:30 | wood_tools: idle |
| natural-mv6-moveON | 0:36 | 1:52 | - | arrow, mob, arrow, mob, mob, mob, mob, arrow, mob | 24:30 | iron_tools: craft wooden_pickaxe:1 |
| **natural: reached, median** | 11/12 0:31* | 11/12 4:01* | 5/12 20:00* | 32 (2.7 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-mv1-moveOFF | - | - | - | - | - | - |
| natural-mv1-moveON | - | - | - | - | - | - |
| natural-mv2-moveOFF | - | - | - | - | - | - |
| natural-mv2-moveON | - | - | - | - | - | - |
| natural-mv3-moveOFF | - | - | 18:02 | - | - | - |
| natural-mv3-moveON | - | - | - | - | - | - |
| natural-mv4-moveOFF | - | - | - | - | - | - |
| natural-mv4-moveON | - | - | - | - | - | - |
| natural-mv5-moveOFF | - | - | - | - | - | - |
| natural-mv5-moveON | - | - | - | - | - | - |
| natural-mv6-moveOFF | - | - | - | - | - | - |
| natural-mv6-moveON | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 22 x mob (attack zombie 8, retreat 7, attack skeleton 2, shelter heal 2, attack drowned 1, attack spider 1, attack zombie_villager 1)
- 7 x arrow (retreat 3, attack skeleton 2, shelter 1, shelter heal 1)
- 2 x explosion.player (shelter 1, attack skeleton 1)
- 1 x lava (idle 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-mv1-moveOFF | 512 | 319 | 40 | 624 | 0 | 0 | 0 | 3 | 0 |
| natural-mv1-moveON | 725 | 113 | 87 | 429 | 131 | 0 | 1 | 0 | 0 |
| natural-mv2-moveOFF | 609 | 327 | 141 | 65 | 103 | 0 | 20 | 219 | 0 |
| natural-mv2-moveON | 864 | 249 | 39 | 56 | 68 | 0 | 206 | 0 | 0 |
| natural-mv3-moveOFF | 457 | 52 | 5 | 681 | 235 | 0 | 14 | 31 | 0 |
| natural-mv3-moveON | 388 | 25 | 0 | 1014 | 0 | 0 | 0 | 0 | 41 |
| natural-mv4-moveOFF | 950 | 129 | 7 | 311 | 47 | 0 | 0 | 22 | 1 |
| natural-mv4-moveON | 1023 | 115 | 29 | 80 | 216 | 0 | 19 | 2 | 0 |
| natural-mv5-moveOFF | 324 | 75 | 3 | 885 | 70 | 0 | 0 | 137 | 0 |
| natural-mv5-moveON | 507 | 14 | 0 | 972 | 0 | 0 | 0 | 0 | 0 |
| natural-mv6-moveOFF | 1043 | 0 | 0 | 69 | 0 | 0 | 0 | 0 | 356 |
| natural-mv6-moveON | 758 | 342 | 1 | 190 | 161 | 0 | 9 | 8 | 0 |

## Top failures (action + code, count, one example)

- 64 x explore any HAZARD  (e.g. natural-mv1-moveON: open water ahead; will turn)
- 62 x collect log WRONG_PLACE  (e.g. natural-mv3-moveOFF: in water: log is dug from dry ground)
- 37 x explore log HAZARD  (e.g. natural-mv3-moveOFF: open water ahead; will turn)
- 31 x attack zombie NOT_FOUND  (e.g. natural-mv1-moveON: no reachable zombie in sight)
- 30 x retreat NO_PROGRESS  (e.g. natural-mv2-moveOFF: couldn't get away (nearest monster 5 blocks))
- 22 x shelter NO_ROOM  (e.g. natural-mv1-moveON: no safe ground to dig into nearby)
- 16 x collect log NOT_FOUND  (e.g. natural-mv3-moveOFF: no log seen nearby)
- 14 x explore log ALREADY_DONE  (e.g. natural-mv3-moveOFF: already see log)
- 13 x craft torch NO_RECIPE  (e.g. natural-mv1-moveOFF: no known recipe for torch (not unlocked yet?))
- 10 x collect raw_iron STUCK  (e.g. natural-mv1-moveOFF: stuck: stayed inside 2.0 blocks for 12 s)
