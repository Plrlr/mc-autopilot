# Batch 39386393931 @ e2a8a01

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:19 | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-0-drill-0-on-0 | 0:34 | 2:42 | - | mob:zombie, mob:zombie | 19:30 | iron_tools: goto surface |
| natural-drill-0-1-drill-0-off-1 | 0:54 | 2:49 | 9:12 | fall | 19:30 | nether_portal: collect raw_iron:13 |
| natural-drill-0-1-drill-0-on-1 | 0:47 | - | - | 0 | 19:30 | stone_tools: idle |
| natural-drill-0-2-drill-0-off-2 | 0:16 | 4:04 | 9:01 | arrow:skeleton | 19:30 | nether_portal: collect raw_iron:13 |
| natural-drill-0-2-drill-0-on-2 | 0:16 | 2:16 | 5:07 | arrow:skeleton, arrow:skeleton, explosion.player:creeper, mob:zombie | 19:30 | nether_portal: craft iron_pickaxe:1 |
| **natural: reached, median** | 6/6 0:26 | 4/6 3:26* | 3/6 14:36* | 8 (1.3 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 10:58 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | 11:10 | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob:zombie (attack zombie 2, retreat 1)
- 3 x arrow:skeleton (attack skeleton 2, retreat 1)
- 1 x fall (attack skeleton 1)
- 1 x explosion.player:creeper (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 135 | 8 | 5 | 211 | 0 | 0 | 0 | 839 | 0 |
| natural-drill-0-0-drill-0-on-0 | 312 | 22 | 26 | 68 | 77 | 0 | 88 | 534 | 40 |
| natural-drill-0-1-drill-0-off-1 | 763 | 146 | 144 | 35 | 21 | 0 | 8 | 78 | 0 |
| natural-drill-0-1-drill-0-on-1 | 30 | 12 | 31 | 302 | 0 | 0 | 0 | 739 | 53 |
| natural-drill-0-2-drill-0-off-2 | 756 | 134 | 66 | 84 | 93 | 0 | 0 | 59 | 0 |
| natural-drill-0-2-drill-0-on-2 | 408 | 185 | 29 | 199 | 300 | 0 | 60 | 2 | 0 |

## Top failures (action + code, count, one example)

- 28 x craft stone_pickaxe NO_ROOM  (e.g. natural-drill-0-1-drill-0-on-1: no room to place a crafting_table)
- 26 x explore any STUCK  (e.g. natural-drill-0-1-drill-0-on-1: stuck: stayed inside 2.0 blocks for 12 s)
- 15 x sleep NO_ROOM  (e.g. natural-drill-0-1-drill-0-on-1: no flat room for a bed nearby)
- 10 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-off-0: timed out after 90 s)
- 8 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-on-0: no reachable zombie in sight)
- 6 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-on-0: couldn't get away (nearest monster 2 blocks))
- 4 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no reachable skeleton in sight)
- 3 x attack skeleton UNREACHABLE  (e.g. natural-drill-0-2-drill-0-off-2: lost sight of the skeleton)
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-off-2: already see log)
