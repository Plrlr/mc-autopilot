# Batch 37212430740 @ 35f252d

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| combat-rg-c0-reg-stack | - | 0:00 | - | 0 | ? | iron_tools: collect raw_iron:13 |
| natural-rg-n0-reg-stack | 1:29 | 3:26 | 9:05 | 0 | 19:30 | nether_portal: shore |
| natural-rg-n1-reg-stack | 0:20 | 2:13 | 9:32 | 0 | 19:30 | nether_portal: diamond_hunt 3 |
| natural-rg-n2-reg-stack | 0:17 | 2:03 | 4:34 | arrow:skeleton | 19:30 | nether_portal: collect raw_iron:7 |
| **natural: reached, median** | 3/3 0:20 | 3/3 2:13 | 3/3 9:05 | 1 (0.3 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| combat-rg-c0-reg-stack | - | - | - | - | - | - |
| natural-rg-n0-reg-stack | 15:36 | 15:35 | - | - | - | - |
| natural-rg-n1-reg-stack | 15:24 | 14:38 | 7:13 | - | - | - |
| natural-rg-n2-reg-stack | 12:07 | 12:06 | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x arrow:skeleton (explore cow,pig,sheep,chicken 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| combat-rg-c0-reg-stack | 386 | 0 | 0 | 0 | 200 | 0 | 0 | 13 | 0 |
| natural-rg-n0-reg-stack | 524 | 245 | 100 | 147 | 51 | 42 | 0 | 90 | 0 |
| natural-rg-n1-reg-stack | 427 | 299 | 31 | 218 | 133 | 16 | 0 | 74 | 0 |
| natural-rg-n2-reg-stack | 625 | 384 | 1 | 90 | 58 | 8 | 4 | 24 | 0 |

## Top failures (action + code, count, one example)

- 6 x collect flint NO_ROOM  (e.g. natural-rg-n0-reg-stack: no free flat spot beside us for the gravel)
- 4 x attack creeper HAZARD  (e.g. combat-rg-c0-reg-stack: no safe gap after hitting the creeper)
- 4 x collect raw_iron STUCK  (e.g. combat-rg-c0-reg-stack: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x collect log NOT_FOUND  (e.g. natural-rg-n0-reg-stack: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-rg-n0-reg-stack: already see log)
- 3 x craft torch NO_RECIPE  (e.g. natural-rg-n1-reg-stack: no known recipe for torch (not unlocked yet?))
- 3 x explore gravel,water ALREADY_DONE  (e.g. natural-rg-n1-reg-stack: already see gravel)
- 2 x retreat NO_PROGRESS  (e.g. combat-rg-c0-reg-stack: couldn't get away (nearest monster 3 blocks))
- 2 x attack zombie UNREACHABLE  (e.g. natural-rg-n0-reg-stack: lost sight of the zombie)
- 2 x explore cow,pig,sheep,chicken STUCK  (e.g. natural-rg-n0-reg-stack: stuck: stayed inside 2.0 blocks for 12 s)

## Portal cast log, natural-rg-n0-reg-stack (last steps)

- bucket: fill water at 9, 62, 28 from 11, 63, 29 hand=bucket accepted=true dist=3.0

## Portal cast log, natural-rg-n1-reg-stack (last steps)

- bucket: fill water at 70, 16, 76 from 70, 16, 79 hand=bucket accepted=true dist=3.6

## Portal cast log, natural-rg-n2-reg-stack (last steps)

- bucket: fill water at 896, 13, 126 from 894, 13, 125 hand=bucket accepted=true dist=2.3
