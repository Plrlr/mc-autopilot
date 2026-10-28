# Batch 40610114970 @ b94f055

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m3 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 1:01 | 2:28 | - | 11:25 | 0 | 19:30 | nether_portal: sleep |
| natural-drill-0-0-drill-0-on-0 | 1:03 | - | - | - | 0 | 19:30 | stone_tools: shore |
| natural-drill-0-1-drill-0-off-1 | 1:22 | 10:18 | 12:33 | 16:00 | 0 | 19:30 | nether_portal: idle |
| natural-drill-0-1-drill-0-on-1 | 0:18 | 5:17 | - | - | mob:zombie, explosion.player:creeper, mob:zombie | 19:30 | iron_tools: unstuck |
| natural-drill-0-2-drill-0-off-2 | 0:18 | 2:53 | - | 7:13 | mob:zombie | 19:30 | nether_portal: attack zombie_villager |
| natural-drill-0-2-drill-0-on-2 | 0:16 | 2:47 | - | 9:07 | arrow:skeleton, explosion.player:creeper | 19:30 | nether_portal: collect log:3 |
| **natural: reached, median** | 6/6 0:39 | 5/6 4:05* | 1/6 20:00* | 4/6 13:42* | 6 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | - | - | 17:24 | - | - | - |
| natural-drill-0-0-drill-0-on-0 | - | - | - | - | - | - |
| natural-drill-0-1-drill-0-off-1 | - | - | 10:35 | - | - | - |
| natural-drill-0-1-drill-0-on-1 | - | - | 2:42 | - | - | - |
| natural-drill-0-2-drill-0-off-2 | - | - | 13:03 | - | - | - |
| natural-drill-0-2-drill-0-on-2 | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 3 x mob:zombie (shelter heal 1, attack zombie 1, retreat 1)
- 2 x explosion.player:creeper (attack spider 1, retreat 1)
- 1 x arrow:skeleton (pickup stations 1)

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-drill-0-0-drill-0-off-0 | 457 | 228 | 97 | 280 | 87 | 0 | 24 | 23 | 0 |
| natural-drill-0-0-drill-0-on-0 | 137 | 24 | 36 | 61 | 43 | 0 | 0 | 890 | 0 |
| natural-drill-0-1-drill-0-off-1 | 359 | 138 | 112 | 205 | 5 | 0 | 17 | 357 | 0 |
| natural-drill-0-1-drill-0-on-1 | 459 | 136 | 32 | 12 | 101 | 0 | 331 | 116 | 0 |
| natural-drill-0-2-drill-0-off-2 | 547 | 147 | 1 | 379 | 97 | 0 | 0 | 23 | 0 |
| natural-drill-0-2-drill-0-on-2 | 687 | 126 | 29 | 69 | 67 | 0 | 206 | 6 | 0 |

## Top failures (action + code, count, one example)

- 19 x collect flint NO_ROOM  (e.g. natural-drill-0-0-drill-0-off-0: no free flat spot beside us for the gravel)
- 18 x explore gravel,water ALREADY_DONE  (e.g. natural-drill-0-0-drill-0-off-0: already see gravel)
- 12 x craft torch NO_RECIPE  (e.g. natural-drill-0-1-drill-0-off-1: no known recipe for torch (not unlocked yet?))
- 11 x shore TIMEOUT  (e.g. natural-drill-0-0-drill-0-on-0: timed out after 90 s)
- 9 x retreat NO_PROGRESS  (e.g. natural-drill-0-0-drill-0-off-0: couldn't get away (nearest monster 10 blocks))
- 7 x attack zombie UNREACHABLE  (e.g. natural-drill-0-0-drill-0-off-0: lost sight of the zombie)
- 7 x attack zombie NOT_FOUND  (e.g. natural-drill-0-0-drill-0-off-0: no reachable zombie in sight)
- 4 x craft iron_pickaxe NO_RECIPE  (e.g. natural-drill-0-1-drill-0-off-1: no known recipe for iron_pickaxe (not unlocked yet?))
- 4 x collect log STUCK  (e.g. natural-drill-0-1-drill-0-on-1: stuck: stayed inside 2.0 blocks for 12 s)
- 3 x sleep NO_ROOM  (e.g. natural-drill-0-1-drill-0-off-1: no flat room for a bed nearby)
