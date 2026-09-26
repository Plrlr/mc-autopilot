# Trial batches

One line per batch, newest last. `scripts/cycle` appends the numbers; add the note by hand:
what changed since the last batch and what the batch taught. Milestone columns show how many
natural (full, honest) runs reached it and the median game time, with runs that didn't reach it
counted as the full run length (a `*` marks medians that include such runs). Scenario and task
runs are listed separately. A fresh session can resume from this file plus docs/lessons.md.

Easy, rules brain (mock). Batches 1-7: seeds a-d, 10 game minutes unless noted. From batch 8:
seeds a-h, 20 game minutes (night included). Medians before batch 8 counted only runs that
reached the milestone.

| run | commit | m1 wood | m2 stone | m4 iron tools | m7 nether | deaths | portal path (runs reaching each step) | scenarios / tasks | top failure | note |
|---|---|---|---|---|---|---|---|---|---|---|
| 36273548847 | 90f48dd | 4/4 0:37 | 4/4 1:17 | 0/4 - | 0/4 - | 1 | - | - | explore cow,pig,sheep,chicken x6 | Batch 1. Food rung ate 3-9 min; table+furnace rebuilt every trip; iron in 2-3 piece trips. m4 not counted then (needed m3). |
| 36274555330 | e8683ce | 4/4 0:22 | 4/4 1:13 | 0/4 - | 0/4 - | 0 | - | cast-a: m2 | craft furnace x18 | Batch 2. Cast scenario's forced goal was overwritten a tick later; "couldn't place" the #1 failure. |
| 36275608236 | 041bd49 | 4/4 0:52 | 4/4 1:27 | 1/4 8:15 | 0/4 - | 2 | - | cast-a: m4 | build_portal x15 | Batch 3: food by need, carried stations, one iron trip. Click logging showed every failed placement held the pickaxe (Baritone swaps it back into hotbar slot 0). |
| 36276814180 | 81b1d3e | 4/4 0:47 | 4/4 1:21 | 2/4 4:34 | 0/4 - | 1 | - | cast-a: m4 | fill_bucket water x5 | Batch 4: hotbar fix (26/26 placements OK), nearest-first memory, unreachable mobs. Cast: wall + lava + water done, water scoop failed. Legit branch mining wandered the surface at y 60. |
| 36277649370 | 77aa468 | 4/4 0:53 | 4/4 1:39 | 3/4 5:25 | 0/4 - | 2 | - | cast-a: m4 | build_portal text x5 | Batch 5: collect digs down to ore depth (GoalYLevel) before branch mining. |
| 36278282839 | 454814b | 4/4 0:49 | 4/4 1:34 | 3/4 5:30 | 0/4 - | 7 | - | cast-a: m4; natural-a-furnace: ok: crafted 1 furnace after 1 s | attack zombie NOT_FOUND x10 | Batch 6 (15 min): failure codes, dig to ore depth, flint progress, cast scoop fixes. Cast: first obsidian cast in place (2 blocks), then the scoop ray was blocked by the new obsidian. Task test mode works. Night deaths (7). Natural seeds never found lava: memory saw only 16 blocks. |
