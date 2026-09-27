# Batch 36332354440 @ 38da652

## Milestones (game time since the autopilot started)

| run | m12 | deaths | length | ended doing |
|---|---|---|---|---|
| end-a-d3 | 0:00 | outOfWorld | 12:00 | enter_end: explore blaze |
| end-b-d4 | 0:00 | indirectMagic, explosion.player | 12:00 | enter_end: explore blaze |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| end-a-d3 | - | - | - | - | - | - |
| end-b-d4 | - | - | 4:43 | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x outOfWorld (dragon 1)
- 1 x indirectMagic (shoot end_crystal 1)
- 1 x explosion.player (retreat 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| end-a-d3 | 18 | 4 | 130 | 474 | 75 | 0 | 9 | 3 | 3 |
| end-b-d4 | 0 | 1 | 37 | 634 | 40 | 0 | 0 | 0 | 6 |

## Top failures (action + code, count, one example)

- 5 x shoot end_crystal USE_FAILED  (e.g. end-a-d3: missed 10 shots)
- 3 x explore blaze HAZARD  (e.g. end-a-d3: open water ahead; will turn)
- 3 x explore cow,pig,sheep,chicken HAZARD  (e.g. end-b-d4: open water ahead; will turn)
- 2 x shelter NO_ROOM  (e.g. end-b-d4: no safe ground to dig into nearby)
- 1 x explore any UNREACHABLE  (e.g. end-a-d3: couldn't make headway that way; will turn)
- 1 x attack pillager UNREACHABLE  (e.g. end-a-d3: can't reach the pillager (4 blocks away))
- 1 x attack pillager NOT_FOUND  (e.g. end-a-d3: no reachable pillager in sight)
- 1 x retreat NO_PROGRESS  (e.g. end-b-d4: couldn't get away (nearest monster 8 blocks))

## Screenshots (open only if the numbers above point at something visual)

- end-a-d3: death-1, final, milestone-1
- end-b-d4: death-1, death-2, final, milestone-1
