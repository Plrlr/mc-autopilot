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
| 36280008994 | f3f5a88 | 8/8 0:48 | 8/8 1:23 | 4/8 17:07* | 0/8 - | 24 | - | - | shelter NO_ROOM x13 | Batch 8b: same commit as 8a, to measure run-to-run noise. Same commit as 8a: iron 4/8 vs 8/8, deaths 24 vs 19 (16 while retreating). Run-to-run noise is large and mostly deaths. |
| 36279979624 | f3f5a88 | 8/8 0:41 | 8/8 1:36 | 8/8 6:10 | 0/8 - | 19 | - | cast-a: m7; cast-b: m4 | explore any UNREACHABLE x20 | Batch 8a (8 seeds x 20 min): cast scoop LOS, far lava scan, fight fixes, fixed need order. Noise twin of 8b. **Staged cast on seed a: frame cast, lit, entered the Nether at 3:13 (first time)**, then died there. Cast b stalled on the water scoop. Natural: iron 8/8 (median 6:10), no lava found. |
