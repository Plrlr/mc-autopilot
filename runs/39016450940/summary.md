# Batch 39016450940 @ f1230b3

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:16 | 1:32 | 6:06 | lava | 19:30 | nether_portal: shore |
| natural-drill-0-0-drill-0-on-0 | 1:14 | 3:07 | 7:23 | 0 | 19:30 | nether_portal: explore cow |
| natural-drill-0-1-drill-0-off-1 | 0:46 | 1:58 | 14:57 | onFire | 19:30 | nether_portal: goto surface |
| natural-drill-0-1-drill-0-on-1 | 0:34 | 3:30 | 6:39 | 0 | 19:30 | nether_portal: smelt cooked_chicken:1 |
| natural-drill-0-2-drill-0-off-2 | 0:18 | 2:32 | 5:47 | mob:zombie | 19:30 | nether_portal: collect coal:6 |
| natural-drill-0-2-drill-0-on-2 | 0:15 | 3:12 | 9:18 | arrow:skeleton | 19:30 | nether_portal: collect log:9 |
| **natural: reached, median** | 6/6 0:26 | 6/6 2:49 | 6/6 7:01 | 4 (0.7 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | 2:20 | - | - | - |
| natural-drill-0-0-drill-0-on-0 | 14:57 | 14:13 | 17:27 | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 2:23 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 13:01 | 12:59 | 3:51 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | 10:14 | 10:14 | - | - | - | - |
| natural-drill-0-2-drill-0-on-2 | 14:09 | 14:08 | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x lava (idle 1)
- 1 x onFire (idle 1)
- 1 x mob:zombie (attack zombie 1)
- 1 x arrow:skeleton (attack skeleton 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 402 | 90 | 53 | 254 | 36 | 0 | 0 | 318 | 13 |
| natural-drill-0-0-drill-0-on-0 | 487 | 288 | 5 | 217 | 57 | 76 | 0 | 67 | 0 |
| natural-drill-0-1-drill-0-off-1 | 546 | 208 | 55 | 321 | 21 | 0 | 0 | 40 | 0 |
| natural-drill-0-1-drill-0-on-1 | 436 | 294 | 130 | 95 | 74 | 22 | 133 | 12 | 0 |
| natural-drill-0-2-drill-0-off-2 | 470 | 235 | 107 | 178 | 69 | 15 | 8 | 111 | 0 |
| natural-drill-0-2-drill-0-on-2 | 545 | 271 | 158 | 45 | 76 | 51 | 10 | 38 | 0 |

## Top failures (action + code, count, one example)

- 35 x explore any UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: couldn't make headway that way; will turn)
- 8 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no free flat spot beside us for the gravel)
- 8 x shore STUCK  (e.g. natural-drill-0-0-drill-0-off-0: stuck: stayed inside 2.0 blocks for 12 s)
- 8 x unstuck STUCK  (e.g. natural-drill-0-0-drill-0-off-0: boxed in: can't walk, tunnel or climb out)
- 6 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-on-1: no known recipe for torch (not unlocked yet?))
- 5 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-off-0: already see gravel)
- 4 x collect log NOT_FOUND  (e.g. natural-drill-0-1-drill-0-off-1: no log seen nearby)
- 3 x explore water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-on-0: already see water)
- 3 x explore cow,pig,sheep,chicken ALREADY_DONE  (e.g. natural-drill-0-1-drill-0-on-1: already see cow)
- 3 x attack skeleton NOT_FOUND  (e.g. natural-drill-0-2-drill-0-on-2: no reachable skeleton in sight)

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 51, 22, -40 from 53, 21, -42 hand=bucket accepted=true dist=2.8

## Portal cast log, natural-drill-0-2-drill-0-off-2 (last steps)

- bucket: fill water at -3, 62, -193 from -3, 62, -190 hand=bucket accepted=true dist=3.6

## Portal cast log, natural-drill-0-2-drill-0-on-2 (last steps)

- bucket: fill water at -203, 62, -182 from -203, 62, -179 hand=bucket accepted=true dist=3.4
