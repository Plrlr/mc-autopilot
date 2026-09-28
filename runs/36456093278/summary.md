# Batch 36456093278 @ 80a558a

## Milestones (game time since the autopilot started)

| run | m1 | m2 | m4 | deaths | length | ended doing |
|---|---|---|---|---|---|---|
| natural-a-prep | 1:08 | 2:23 | - | lava | 7:30 | iron_tools: attack pig |
| natural-armor-chest | 0:00 | - | - | 0 | ? | stone_tools: idle |
| natural-armor-start-armor-route | - | 0:00 | 0:01 | fall | 3:30 | nether_portal: craft stone_pickaxe:1 |
| natural-armor-sword | - | - | - | 0 | ? | wood_tools: idle |
| natural-armor-wear | - | - | - | 0 | ? | wood_tools: idle |
| natural-food-cook | 0:00 | - | - | 0 | ? | stone_tools: idle |
| natural-shield-hold | - | - | - | 0 | ? | wood_tools: idle |
| natural-shield-make | - | - | - | 0 | ? | wood_tools: idle |
| **natural: reached, median** | 1/2 10:34* | 2/2 1:11 | 1/2 10:00* | 2 (1.0 per run) | | fails count as the full run length |

## Portal path (game time each step was first reached)

| run | 2 buckets | flint+steel | lava seen | obsidian | frame | lit |
|---|---|---|---|---|---|---|
| natural-a-prep | - | - | 5:48 | - | - | - |
| natural-armor-start-armor-route | - | - | - | - | - | - |

## Deaths by cause (what the bot was doing)

- 1 x lava (idle 1)
- 1 x fall (attack zombie 1)

## Task tests

- natural-armor-chest: craft iron_chestplate:1 -> ok: crafted 1 iron_chestplate after 1 s
- natural-armor-sword: craft iron_sword:1 -> ok: crafted 1 iron_sword after 0 s
- natural-armor-wear: equip armor -> ok: wearing the best armor after 0 s
- natural-food-cook: smelt cooked_beef:3 -> ok: smelted 3 cooked_beef after 15 s
- natural-shield-hold: equip shield -> ok: shield in off hand after 0 s
- natural-shield-make: craft shield:1 -> ok: crafted 1 shield after 1 s

## Where the time went (seconds)

| run | mining | crafting | food | travel | combat | building | night | other | idle |
|---|---|---|---|---|---|---|---|---|---|
| natural-a-prep | 202 | 47 | 182 | 43 | 0 | 0 | 0 | 0 | 0 |
| natural-armor-chest | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-armor-start-armor-route | 165 | 28 | 0 | 0 | 30 | 0 | 0 | 11 | 0 |
| natural-armor-sword | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-armor-wear | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-food-cook | 0 | 0 | 15 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-shield-hold | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| natural-shield-make | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## Top failures (action + code, count, one example)

- 1 x collect log NOT_FOUND  (e.g. natural-a-prep: no log seen nearby)
- 1 x goto surface UNREACHABLE  (e.g. natural-a-prep: couldn't find the way up)
- 1 x collect raw_iron STUCK  (e.g. natural-armor-start-armor-route: stuck: stayed inside 2.0 blocks for 12 s)
