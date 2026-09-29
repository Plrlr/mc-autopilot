# Batch 36631984972 @ ad8e8a4

## Milestones (game time since the autopilot started)

| run | m6 | deaths | length | ended doing |
|---|---|---|---|---|
| cast-audit-cast-budget | - | 0 | ? | nether_portal: idle |
| mold-audit-a-remainder | 0:00 | 0 | ? | nether_portal: idle |
| mold_spare-audit-a-two-water | 0:00 | 0 | 2:00 | nether_portal: idle |
| placed_room-audit-room-hard-roof | 0:00 | 0 | ? | blaze_rods: idle |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|

## Task tests

- cast-audit-cast-budget: cast_portal -> failed NOT_FOUND: no known lava pool to cast from after 0 s
- mold-audit-a-remainder: obsidian_mold 1 -> ok: made 1 obsidian in the mold after 18 s
- mold_spare-audit-a-two-water: obsidian_mold 1 -> ok: made 1 obsidian in the mold after 133 s
- placed_room-audit-room-hard-roof: dig_portal -> ok: nether portal lit after 19 s

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| cast-audit-cast-budget | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| mold-audit-a-remainder | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0 |
| mold_spare-audit-a-two-water | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 133 | 0 |
| placed_room-audit-room-hard-roof | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0 |

## Top failures (action + code, count, one example)

- 1 x cast_portal NOT_FOUND  (e.g. cast-audit-cast-budget: no known lava pool to cast from)

## Portal cast log, mold-audit-a-remainder (last steps)

- bucket: fill lava at 15, 77, 2 from 17, 78, 2 hand=lava_bucket accepted=true dist=2.7

## Portal cast log, mold_spare-audit-a-two-water (last steps)

- bucket: fill lava at 15, 77, 4 from 17, 78, 4 hand=lava_bucket accepted=true dist=2.7
