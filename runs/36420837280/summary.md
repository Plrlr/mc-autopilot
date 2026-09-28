# Batch 36420837280 @ 0844137

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a | 1:30 | 2:21 | 4:52 | 0 | 9:30 | nether_portal: collect flint:1 |
| natural-b | 0:17 | 1:40 | - | onFire, arrow, arrow | 9:30 | iron_tools: shelter |
| **natural: reached, median** | 2/2 0:53 | 2/2 2:00 | 1/2 7:26* | 3 (1.5 per run) | | fails count as the full run length |

## Deaths by cause (what the bot was doing)

- 2 x arrow (goto death 1, attack skeleton 1)
- 1 x onFire (smelt iron_ingot 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a | 392 | 86 | 0 | 99 | 21 | 0 | 0 | 0 | 0 |
| natural-b | 353 | 116 | 0 | 57 | 54 | 0 | 10 | 0 | 0 |

## Top failures (action + code, count, one example)

- 3 x collect log NOT_FOUND  (e.g. natural-a: no log seen nearby)
- 3 x explore log ALREADY_DONE  (e.g. natural-a: already see log)
- 1 x goto surface UNREACHABLE  (e.g. natural-a: couldn't find the way up)
- 1 x smelt iron_ingot UNREACHABLE  (e.g. natural-b: took too long to reach the furnace)
- 1 x attack skeleton UNREACHABLE  (e.g. natural-b: can't reach the skeleton (5 blocks away))
- 1 x attack skeleton NOT_FOUND  (e.g. natural-b: no reachable skeleton in sight)
