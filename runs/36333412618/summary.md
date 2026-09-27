# Batch 36333412618 @ 3087fdf

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-c-perf | 0:58 | 1:46 | 7:03 | 0 | 10:00 | nether_portal: collect raw_iron:8 |
| natural-c-perfsmall | 0:35 | 0:58 | 4:24 | 0 | 10:00 | nether_portal: smelt iron_ingot:4 |
| **natural: reached, median** | 2/2 0:46 | 2/2 1:22 | 2/2 5:43 | 0 (0.0 per run) | | fails count as the full run length |

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-c-perf | 337 | 128 | 73 | 40 | 8 | 0 | 0 | 12 | 0 |
| natural-c-perfsmall | 346 | 139 | 65 | 24 | 0 | 0 | 0 | 24 | 0 |

## Top failures (action + code, count, one example)

- 2 x collect stone STUCK  (e.g. natural-c-perf: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x collect raw_iron STUCK  (e.g. natural-c-perf: stuck: stayed inside 2.0 blocks for 12 s)
- 2 x collect log STUCK  (e.g. natural-c-perf: stuck: stayed inside 2.0 blocks for 12 s)
- 1 x pickup stations STUCK  (e.g. natural-c-perf: stuck: stayed inside 2.0 blocks for 12 s)
- 1 x collect coal STUCK  (e.g. natural-c-perfsmall: stuck: stayed inside 2.0 blocks for 12 s)
- 1 x goto surface UNREACHABLE  (e.g. natural-c-perfsmall: couldn't find the way up)
- 1 x collect flint STUCK  (e.g. natural-c-perfsmall: stuck: stayed inside 2.0 blocks for 12 s)

## Screenshots (open only if the numbers above point at something visual)

- natural-c-perf: final, milestone-1, milestone-2, milestone-3
- natural-c-perfsmall: final, milestone-1, milestone-2, milestone-3
