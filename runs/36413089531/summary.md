# Batch 36413089531 @ 543b377

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a-flint5 | - | - | - | 0 | ? | wood_tools: idle |
| natural-c-log-axe | - | - | - | 0 | ? | wood_tools: idle |
| natural-f | 0:14 | 0:47 | 5:33 | explosion.player | 11:30 | nether_portal: retreat |
| natural-g | 0:49 | 1:41 | 7:09 | 0 | 11:30 | nether_portal: collect coal:1 |
| **natural: reached, median** | 2/2 0:31 | 2/2 1:14 | 2/2 6:21 | 1 (0.5 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 1 x explosion.player (retreat 1)

## Task tests

- natural-a-flint5: collect flint:1 -> ok: flint after 3 gravel breaks after 3 s
- natural-c-log-axe: collect log:6 -> failed NOT_FOUND: no log seen nearby after 0 s

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a-flint5 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-c-log-axe | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-f | 372 | 135 | 8 | 131 | 63 | 0 | 0 | 8 | 0 |
| natural-g | 448 | 123 | 67 | 60 | 17 | 0 | 0 | 3 | 0 |

## Top failures (action + code, count, one example)

- 3 x collect raw_iron STUCK  (e.g. natural-f: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x collect flint NOT_FOUND  (e.g. natural-f: no gravel seen yet (lake and river beds often have it))
- 3 x explore gravel,water ALREADY_DONE  (e.g. natural-f: already see water)
- 3 x retreat NO_PROGRESS  (e.g. natural-f: couldn't get away (nearest monster 7 blocks))
- 2 x collect log NOT_FOUND  (e.g. natural-c-log-axe: no log seen nearby)
- 1 x goto surface UNREACHABLE  (e.g. natural-f: couldn't find the way up)
- 1 x collect flint STUCK  (e.g. natural-f: stuck: stayed inside 2.0 blocks for 12 s)
