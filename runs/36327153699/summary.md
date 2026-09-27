# Batch 36327153699 @ 71c1c14

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-c-lean | 0:33 | 2:20 | 6:40 | mob | 15:00 | nether_portal: shelter |
| natural-c-leansmall | 0:35 | 1:13 | 5:41 | 0 | 15:00 | nether_portal: smelt iron_ingot:5 |
| natural-c-plain | 0:55 | 1:28 | - | mob | 15:00 | iron_tools: attack chicken |
| natural-c-small | 0:35 | 1:40 | - | 0 | 15:00 | iron_tools: shelter |
| **natural: reached, median** | 4/4 0:35 | 4/4 1:34 | 2/4 13:20* | 2 (0.5 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 2 x mob (attack zombie 1, attack skeleton 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-c-lean | 366 | 163 | 34 | 30 | 31 | 0 | 234 | 36 | 2 |
| natural-c-leansmall | 471 | 147 | 71 | 59 | 65 | 0 | 20 | 64 | 0 |
| natural-c-plain | 503 | 60 | 41 | 194 | 44 | 0 | 0 | 53 | 2 |
| natural-c-small | 244 | 57 | 70 | 147 | 0 | 0 | 173 | 206 | 0 |

## Top failures (action + code, count, one example)

- 9 x collect raw_iron STUCK  (e.g. natural-c-leansmall: stuck: stayed inside 2.0 blocks for 12 s)
- 6 x unstuck STUCK  (e.g. natural-c-leansmall: couldn't get out (walked, tunnelled and climbed 0 blocks))
- 6 x collect log NOT_FOUND  (e.g. natural-c-plain: no log in sight underground)
- 5 x attack zombie NOT_FOUND  (e.g. natural-c-lean: no reachable zombie in sight)
- 4 x collect stone STUCK  (e.g. natural-c-lean: stuck: stayed inside 2.0 blocks for 12 s)
- 4 x collect log STUCK  (e.g. natural-c-lean: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x goto surface UNREACHABLE  (e.g. natural-c-lean: couldn't find the way up)
- 2 x collect coal STUCK  (e.g. natural-c-lean: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x retreat NO_PROGRESS  (e.g. natural-c-leansmall: couldn't get away (nearest monster 6 blocks))
- 2 x explore log HAZARD  (e.g. natural-c-plain: open water ahead; will turn)

## Screenshots (open only if the numbers above point at something visual)

- natural-c-lean: death-1, final, milestone-1, milestone-2, milestone-3
- natural-c-leansmall: final, milestone-1, milestone-2, milestone-3
- natural-c-plain: death-1, final, milestone-1, milestone-2
- natural-c-small: final, milestone-1, milestone-2
