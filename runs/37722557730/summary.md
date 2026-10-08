# Batch 37722557730 @ 4769d09

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:17 | 2:28 | - | onFire, onFire | 19:30 | iron_tools: goto death |
| natural-drill-0-0-drill-0-on-0 | 0:34 | 2:10 | 4:20 | onFire | 19:30 | nether_portal: smelt iron_ingot:16 |
| natural-drill-0-1-drill-0-off-1 | 0:34 | - | - | 0 | 19:30 | stone_tools: explore any |
| natural-drill-0-1-drill-0-on-1 | 0:22 | 3:07 | 6:53 | 0 | 19:30 | nether_portal: goto surface |
| natural-drill-0-2-drill-0-off-2 | 0:20 | 2:13 | 18:24 | mob:zombie, mob:zombie | 19:30 | nether_portal: smelt iron_ingot:9 |
| natural-drill-0-2-drill-0-on-2 | 1:35 | 3:26 | 7:54 | onFire, lava, mob:enderman, fall | 19:30 | nether_portal: craft stick:4 |
| **natural: reached, median** | 6/6 0:28 | 5/6 2:47* | 4/6 13:09* | 9 (1.5 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | 15:08 | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | 5:53 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 16:53 | 12:58 | 5:04 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | 4:54 | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x onFire (idle 1, retreat 1, smelt iron_ingot 1, goto surface 1)
- 2 x mob:zombie (attack zombie 2)
- 1 x lava (goto surface 1)
- 1 x mob:enderman (goto surface 1)
- 1 x fall (collect raw_iron 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 937 | 67 | 16 | 28 | 30 | 0 | 0 | 2 | 87 |
| natural-drill-0-0-drill-0-on-0 | 502 | 210 | 32 | 175 | 5 | 0 | 253 | 17 | 0 |
| natural-drill-0-1-drill-0-off-1 | 234 | 14 | 12 | 119 | 0 | 0 | 0 | 819 | 0 |
| natural-drill-0-1-drill-0-on-1 | 516 | 424 | 33 | 116 | 46 | 9 | 0 | 53 | 0 |
| natural-drill-0-2-drill-0-off-2 | 770 | 140 | 17 | 32 | 174 | 0 | 35 | 25 | 0 |
| natural-drill-0-2-drill-0-on-2 | 538 | 154 | 35 | 169 | 110 | 0 | 152 | 26 | 0 |

## Top failures (action + code, count, one example)

- 15 x attack creeper HAZARD  (e.g. natural-drill-0-0-drill-0-off-0: no safe gap after hitting the creeper)
- 12 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-on-0: no free flat spot beside us for the gravel)
- 9 x shore TIMEOUT  (e.g. natural-drill-0-1-drill-0-off-1: timed out after 90 s)
- 6 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-on-0: already see gravel)
- 5 x attack zombie NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no reachable zombie in sight)
- 3 x smelt iron_ingot UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: couldn't reach the furnace)
- 3 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-on-1: no known recipe for torch (not unlocked yet?))
- 3 x collect log NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-drill-0-2-drill-0-on-2: already see log)
- 2 x shore STUCK  (e.g. natural-drill-0-1-drill-0-on-1: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.8
