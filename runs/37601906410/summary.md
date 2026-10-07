# Batch 37601906410 @ 8bb4d22

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | m6 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 0:58 | 2:42 | 11:29 | - | arrow:skeleton, mob:zombie, mob:zombie, mob:zombie, mob:zombie | 19:30 | nether_portal: collect raw_iron:4 |
| natural-drill-0-0-drill-0-on-0 | 0:14 | - | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-1-drill-0-off-1 | 0:17 | 4:26 | 7:42 | - | arrow:skeleton | 19:30 | nether_portal: shelter |
| natural-drill-0-1-drill-0-on-1 | 0:52 | 3:01 | 8:57 | - | 0 | 19:30 | nether_portal: diamond_hunt 3 |
| natural-drill-0-2-drill-0-off-2 | 0:19 | 3:02 | 6:21 | 15:49 | 0 | 19:30 | nether_portal: eat |
| natural-drill-0-2-drill-0-on-2 | 0:20 | 2:14 | 5:32 | - | 0 | 19:30 | nether_portal: smelt cooked_porkchop:2 |
| **natural: reached, median** | 6/6 0:19 | 5/6 3:01* | 5/6 8:19* | 1/6 20:00* | 6 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | - | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 4:02 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | 15:56 | 15:55 | 6:12 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | 10:40 | 10:39 | 13:57 | - | - | - |
| natural-drill-0-2-drill-0-on-2 | 11:50 | 11:18 | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 4 x mob:zombie (attack zombie 3, retreat 1)
- 2 x arrow:skeleton (attack skeleton 1, shelter 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 721 | 225 | 68 | 42 | 60 | 0 | 4 | 54 | 0 |
| natural-drill-0-0-drill-0-on-0 | 193 | 8 | 0 | 145 | 0 | 0 | 0 | 851 | 0 |
| natural-drill-0-1-drill-0-off-1 | 441 | 132 | 5 | 317 | 36 | 0 | 227 | 37 | 0 |
| natural-drill-0-1-drill-0-on-1 | 539 | 285 | 108 | 0 | 17 | 94 | 0 | 155 | 0 |
| natural-drill-0-2-drill-0-off-2 | 481 | 241 | 84 | 28 | 152 | 17 | 1 | 192 | 0 |
| natural-drill-0-2-drill-0-on-2 | 429 | 261 | 61 | 221 | 43 | 11 | 170 | 0 | 0 |

## Top failures (action + code, count, one example)

- 9 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-on-0: timed out after 90 s)
- 8 x attack creeper HAZARD  (e.g. natural-drill-0-1-drill-0-off-1: creeper started its fuse before the hit)
- 6 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-off-1: no known recipe for torch (not unlocked yet?))
- 6 x collect flint NO_ROOM  (e.g. natural-drill-0-1-drill-0-off-1: no free flat spot beside us for the gravel)
- 5 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 5 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-1-drill-0-off-1: already see gravel)
- 5 x fill_bucket water NOT_FOUND  (e.g. natural-drill-0-2-drill-0-off-2: no known water source with a bank to stand on)
- 4 x shore STUCK  (e.g. natural-drill-0-1-drill-0-off-1: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x retreat NO_PROGRESS  (e.g. natural-drill-0-1-drill-0-off-1: couldn't get away (nearest monster 1 blocks))
- 3 x collect log STUCK  (e.g. natural-drill-0-1-drill-0-off-1: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-drill-0-1-drill-0-on-1 (last steps)

- bucket: fill water at 50, 62, -60 from 48, 62, -62 hand=bucket accepted=true dist=3.4

## Portal cast log, natural-drill-0-2-drill-0-off-2 (last steps)

- bucket: fill water at -2, 62, -192 from -4, 63, -194 hand=bucket accepted=true dist=3.4
- bucket: fill lava at -43, -55, -191 from -41, -53, -190 hand=lava_bucket accepted=true dist=3.7
- bucket: fill lava at -42, -55, -191 from -41, -53, -189 hand=lava_bucket accepted=true dist=3.6

## Portal cast log, natural-drill-0-2-drill-0-on-2 (last steps)

- bucket: fill water at -180, 62, 39 from -179, 63, 37 hand=bucket accepted=true dist=3.2
