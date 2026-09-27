# Batch 36328089342 @ 71c1c14

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m7 | m8 | m12 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|---|
| blaze-a-b1 | - | - | 0:01 | 0:38 | - | 0 | 8:00 | blaze_rods: explore any |
| end-a-e1 | - | - | - | - | 0:00 | outOfWorld | 10:00 | enter_end: collect log:3 |
| end-b-e2 | - | - | - | - | 0:00 | mob | 10:00 | enter_end: explore cow |
| nether-c-n1 | 1:19 | 1:50 | - | - | - | fall | 12:00 | iron_tools: explore any |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| blaze-a-b1 | - | 0:01 | 1:48 | - | - | - |
| end-a-e1 | - | - | 7:56 | - | - | - |
| end-b-e2 | - | - | - | - | - | - |
| nether-c-n1 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x outOfWorld (goto end_center 1)
- 1 x mob (shoot ender_dragon 1)
- 1 x fall (? 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| blaze-a-b1 | 0 | 0 | 7 | 391 | 13 | 0 | 0 | 67 | 0 |
| end-a-e1 | 2 | 1 | 108 | 428 | 56 | 0 | 0 | 0 | 3 |
| end-b-e2 | 0 | 1 | 14 | 577 | 4 | 0 | 0 | 0 | 2 |
| nether-c-n1 | 295 | 19 | 42 | 350 | 0 | 0 | 0 | 9 | 2 |

## Top failures (action + code, count, one example)

- 15 x fortress blazes NOT_FOUND  (e.g. blaze-a-b1: no fortress or blaze known)
- 13 x craft furnace NO_ROOM  (e.g. nether-c-n1: no room to place a crafting_table)
- 10 x explore any HAZARD  (e.g. end-a-e1: open water ahead; will turn)
- 6 x explore blaze HAZARD  (e.g. end-a-e1: open water ahead; will turn)
- 5 x shelter NO_ROOM  (e.g. nether-c-n1: no safe ground to dig into nearby)
- 4 x explore cow,pig,sheep,chicken HAZARD  (e.g. end-b-e2: open water ahead; will turn)
- 3 x shoot ender_dragon USE_FAILED  (e.g. end-a-e1: missed 10 shots)
- 3 x shoot ender_dragon NOT_FOUND  (e.g. end-a-e1: no ender_dragon in sight)
- 1 x explore any UNREACHABLE  (e.g. end-a-e1: couldn't make headway that way; will turn)
- 1 x collect stone STUCK  (e.g. nether-c-n1: stuck: stayed inside 2.0 blocks for 12 s)

## Screenshots (open only if the numbers above point at something visual)

- blaze-a-b1: final, milestone-1, milestone-2
- end-a-e1: death-1, final, milestone-1
- end-b-e2: death-1, final, milestone-1
- nether-c-n1: death-1, final, milestone-1, milestone-2
