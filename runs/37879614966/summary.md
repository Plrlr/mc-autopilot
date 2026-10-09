# Batch 37879614966 @ 9474d6e

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 1:11 | 4:02 | 9:08 | mob:zombie, mob:slime, mob:slime, mob:slime | 19:30 | nether_portal: collect log:3 |
| natural-drill-0-0-drill-0-on-0 | 0:44 | 2:19 | - | 0 | 19:30 | iron_tools: shore |
| natural-drill-0-1-drill-0-off-1 | 0:50 | 3:39 | 7:56 | 0 | 19:30 | nether_portal: diamond_hunt 1 |
| natural-drill-0-1-drill-0-on-1 | 1:48 | 2:41 | 13:37 | mob:zombie | 19:30 | nether_portal: explore cow |
| natural-drill-0-2-drill-0-off-2 | 0:15 | 2:13 | 7:12 | 0 | 19:30 | nether_portal: smelt iron_ingot:3 |
| natural-drill-0-2-drill-0-on-2 | 0:17 | 2:17 | 6:09 | 0 | 19:30 | nether_portal: smelt iron_ingot:4 |
| **natural: reached, median** | 6/6 0:47 | 6/6 2:30 | 5/6 8:32* | 5 (0.8 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | 12:57 | 12:56 | 1:56 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 18:36 | 18:04 | 4:04 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | 9:32 | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob:slime (attack slime 3)
- 2 x mob:zombie (retreat 1, shelter heal 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 574 | 164 | 3 | 62 | 92 | 0 | 230 | 53 | 0 |
| natural-drill-0-0-drill-0-on-0 | 133 | 17 | 27 | 221 | 0 | 0 | 0 | 800 | 0 |
| natural-drill-0-1-drill-0-off-1 | 388 | 308 | 117 | 67 | 36 | 73 | 9 | 200 | 0 |
| natural-drill-0-1-drill-0-on-1 | 545 | 229 | 139 | 181 | 60 | 0 | 5 | 35 | 0 |
| natural-drill-0-2-drill-0-off-2 | 518 | 181 | 69 | 412 | 0 | 0 | 10 | 7 | 0 |
| natural-drill-0-2-drill-0-on-2 | 457 | 152 | 12 | 455 | 48 | 0 | 71 | 2 | 0 |

## Top failures (action + code, count, one example)

- 15 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no free flat spot beside us for the gravel)
- 15 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-1-drill-0-on-1: already see gravel)
- 8 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-on-0: timed out after 90 s)
- 6 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-off-1: no known recipe for torch (not unlocked yet?))
- 4 x attack slime NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable slime in sight)
- 4 x craft stone_sword NO_ROOM  (e.g. natural-drill-0-0-drill-0-on-0: no room to place a crafting_table)
- 3 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 3 x diamond_hunt 3 NOT_FOUND  (e.g. natural-drill-0-1-drill-0-off-1: found 0 of 3 diamonds)
- 3 x attack sheep NOT_FOUND  (e.g. natural-drill-0-1-drill-0-on-1: no reachable sheep in sight)
- 3 x sleep NO_ROOM  (e.g. natural-drill-0-2-drill-0-off-2: no flat room for a bed nearby)

## Portal cast log, natural-drill-0-1-drill-0-off-1 (last steps)

- bucket: fill water at 50, 62, -45 from 52, 62, -47 hand=bucket accepted=true dist=3.5

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 17, 62, -90 from 17, 63, -89 hand=bucket accepted=true dist=2.2
