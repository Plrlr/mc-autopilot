# MC Autopilot

A self-learning bot that plays **your own character** in a normal single-player Minecraft survival
world and tries to beat the game. Press **K** in your world, switch the autopilot on, and watch.

**It teaches itself.** A learning loop runs on GitHub's free machines around the clock. Every
generation plays fresh random worlds, races challengers against the current champion on the
same worlds, and keeps only changes that clearly win: its tunable numbers ("genes") and its own
model, trained on every decision it has made (plus human play from OpenAI's public VPT logs).
No language model plays the game. Watch it live: **[plrlr.github.io/mc-autopilot](https://plrlr.github.io/mc-autopilot/)**.
How it works: [docs/learning-loop.md](docs/learning-loop.md).

**Where it stands (the loop's genomes, gens 158-177, 2026-10-04, Easy):** in 160 full natural
runs (the race's fresh random worlds from spawn, 40 game minutes, no staged starts) it got wood
tools in 96%, stone tools in 93%, iron tools in 81%, a diamond pickaxe in 10% and **entered the
Nether in 6 runs (4%)**: more iron than in gens 76-85, fewer diamond pickaxes and Nethers (15%
and 8% then). **Furthest so far: a blaze rod** (milestone 8), in two natural runs (gens 84 and
165). From saved Nether starts, 11 games in gens 139-171 got a rod before their first death, none
got 6, and every game died in the Nether (piglins first, then lava). No run has reached milestone
9 (ender pearls) or later; everything past the first blaze rod is code no run has reached. 75-89%
of runs die in their first life. All 1,735 game hours are analysed in
[docs/lessons.md](docs/lessons.md) ("The whole history"); since 2026-10-04 the loop writes that
analysis itself every generation (`scripts/loop/patterns.py`) and its code step reads it.
Details in [Results so far](#results-so-far).

- **One brain, its own.** A planner lists the sensible next actions in order; the bot's learned
  model re-ranks them as far as the loop has shown it helps. Decisions take microseconds, on
  your PC, with no API keys and no network.
- **Code does the hands.** Walking, mining and pathfinding use [Baritone](https://github.com/cabaletta/baritone).
  Crafting goes through the recipe book, and fighting, eating and sleeping use the normal player controls.
- **Fair play.** No cheats, no commands, no x-ray. Ores are only mined once seen (Baritone runs
  in `legitMine` mode and otherwise branch-mines). Gravel for flint comes only from gravel the AI
  has seen (lake and river beds included), and one gravel is placed and broken until flint drops.
  Logs, sand and ores it has spotted (say, in a cave wall) are mined only from blocks it has
  actually seen; with none in sight it explores, or branch-mines for ores and stone. Baritone is
  never asked to search the loaded chunks, and it draws no path or goal markers on screen.
  In the Nether it reads the F3 pie chart the way speedrunners do (turning and watching the
  spawner slice) to get the direction of a fortress: a direction, never a position.
- **You stay in control.** Any movement key (WASD, space) instantly gives control back.
- **$0.** Everything runs on your PC; the learning loop runs on GitHub's free machines.

## How it works

```
Goal ladder      the lowest unfinished of 13 rungs (wood tools ... kill the Ender Dragon)
     |
Brain            the planner's options in the rules' order, re-ranked by the learned model
     |
Skills+reflexes  plain Java over Baritone: collect, craft, smelt, attack, eat, shelter, build_portal, ...
```

**Planner.** For the current goal the planner knows the tech tree (log → planks → table →
pickaxe → ...) and builds a short list of sensible options, rules' pick first: urgent things
(fight, eat, heal), getting items back from a death spot, upkeep, then the goal's next step.

**Learned model.** For each kind of action it predicts the progress over the next two minutes
from the state (health, inventory, what's known nearby, ...). The loop retrains it every
generation and races how much it may overrule the rules (genes `learned.weight`, `learned.explore`).
Emergencies are always the rules' call.

**Skills and reflexes.** About 20 skills, each plain code with a timeout and a fixed failure
code (NEED_ITEM, NOT_FOUND, UNREACHABLE, ...). Reflexes don't wait for any AI: fight a monster
within reach, back away from creepers, flee or wall in and heal when low on health, eat when
starving, swim up when out of air, jump out of lava.

The planner also does upkeep on the way: it keeps cooked food stocked, hunts and cooks in batches,
picks its crafting table and furnace back up, leaves big furnace loads cooking while it works
nearby, sleeps in a bed at night if it has one, heads back up when it's lost underground, and
walks back for its items after a death.

**The goal ladder:** 1 wood and a crafting table, 2 stone tools, 3 food and a night survived,
4 iron tools, shield and bucket, 5 iron armor, 6 diamond pickaxe, 7 nether portal, 8 blaze rods,
9 ender pearls, 10 eyes of ender, 11 find the stronghold, 12 enter the End, 13 kill the Ender
Dragon. Rungs 3 and 5 are optional. Since 2026-09-29 the portal goes the classic way: iron
pickaxe, down to diamond depth, three diamonds for a diamond pickaxe, obsidian from a lava pool
found down there (a block at a time in a dug pit where the pool is deep), a frame built in a
room dug for it. (Before, it was cast from a lava pool with two buckets; that lit almost no
portals in 64 generations.)

**The trial loop.** Progress is measured, not guessed. A GitHub Actions workflow plays 8 fixed
seeds for 20 game minutes each, one Linux machine per world, and uploads every decision log.
`scripts/cycle` starts a batch, waits, downloads the logs and prints a one-page scoreboard:
milestones and the time each was reached (checked from the world: inventory, dimension, blocks,
never from the bot's own report), deaths by cause, and the most common failure codes. The rule is
to keep a change only if the batches show it helped; run-to-run noise makes that hard (below). Staged scenarios (a ready lava pool, a place in the End)
use commands in a throwaway test world to test late-game skills quickly; their numbers are
reported separately and never counted as results.

## Results so far

**The learning loop (since 2026-09-27).** Full natural runs only: the race's fresh-world games
from spawn (stage starts and combat drills are left out), random seeds, Easy. Game length: 20
game minutes until gen 49, 30 in gens 50-66, 40 since gen 67, so the 30-85 row mixes them.
Counted from `loop/data` on the trial-results branch.

| generations | runs | wood | stone tools | iron tools | diamond pickaxe | Nether | blaze rod | deaths/run |
|---|---|---|---|---|---|---|---|---|
| 30-85 | 495 | 99% | 94% | 62% | 6% | 10 (2%), best 13:24 | 1 | 3.0 |
| 76-85 | 80 | 100%, 0:29 | 96%, 2:18 | 61%, 15:56 | 15%, 27:51 | 6 (8%), 31:57 | 1, 34:58 | 2.0 |
| 158-177 | 160 | 96%, 0:28 | 93%, 3:23 | 81%, 11:38 | 10%, 27:38 | 6 (4%), 29:05 | 1, 32:23 | 2.3 |

Times are medians of the runs that got there, in game minutes. A run that dies keeps going
(respawns), so these are milestones within one 40-minute game, not deathless runs.

**Before the loop (hand-tuned rules, trial batches):**

![Share of runs reaching each milestone, and the median time, per trial batch](docs/progress.svg)

From [docs/batches.md](docs/batches.md): cloud trials, natural runs (no commands), the rules
brain (before the learned model and the loop), Easy. Batches 1-6: 4 seeds, 10-15 game minutes. From batch 8: 8 seeds,
20 game minutes. The chart comes from `scripts/plot_progress`.

| batch | wood | stone tools | iron tools | Nether | deaths |
|---|---|---|---|---|---|
| 6 (4 seeds, 15 min) | 4/4, 0:49 | 4/4, 1:34 | 3/4, 5:30 | 0/4 | 7 |
| 8a (8 seeds, 20 min) | 8/8, 0:41 | 8/8, 1:36 | 8/8, 6:10 | 0/8 | 19 |
| 8b (same code as 8a) | 8/8, 0:48 | 8/8, 1:23 | 4/8, 17:07* | 0/8 | 24 |
| 9 (latest) | 8/8, 0:17 | 8/8, 1:04 | 7/8, 5:26* | 0/8 | 14 |

Times are medians in game minutes. `*`: runs that missed the milestone count as the full run
length.

- **The Nether portal is the wall.** In batch 9, 7 of 8 runs had two buckets and flint and
  steel, 3 saw a lava pool, and none placed any obsidian. In the staged test (a lava pool placed
  next to the player), seed a cast the frame, lit it and entered the Nether at 3:13 once
  (batch 8a) and then died there; seed b stalled scooping the water back.
- **Runs vary a lot.** Batches 8a and 8b ran the same code: iron tools on 8/8 vs 4/8 seeds. The
  difference was deaths (19 vs 24, most while running away). A single batch can't show a small
  improvement; compare two or more.
- **Deaths are frequent:** 14 in batch 9's eight 20-minute runs.
- **Milestones 8-13 have never been reached** in a natural run.

## Setup (Windows)

You need Minecraft Java Edition and Java 25 to build the mod.

1. Install Java 25 (for building): `winget install --id EclipseAdoptium.Temurin.25.JDK -e`
2. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **26.3**.
3. Put these in `%APPDATA%\.minecraft\mods`:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3
   - Baritone **v1.20.0**: `baritone-api-fabric-1.20.0.jar` from the
     [Baritone releases](https://github.com/cabaletta/baritone/releases/tag/v1.20.0)
     (the `api` build; the `standalone` one hides the API our mod needs)
   - this mod: build it with `cd mod` then `.\gradlew.bat build`, and copy `mod\build\libs\mc-autopilot-0.1.0.jar`

   Baritone is not inside our jar and never will be: it's a separate mod (LGPL-3.0), so install it
   yourself from its own release page as above.
4. Start Minecraft with the **fabric-loader-26.3** profile.
5. Make a **new test world** (the AI will dig, die and lose things), press **K**, click **Autopilot: OFF**.

There are no settings or keys to fill in. The loop's current champion genes and learned model
can go in `%APPDATA%\.minecraft\mc-autopilot\` as `params.json` and `learned.json`
(`python scripts/loop/loop.py export`); without them the defaults play.

## Controls

| | |
|---|---|
| **K** | panel: on/off, the goal, what it's doing and its recent decisions |
| **WASD / space** | take control back immediately |
| `!stop` `!start` `!status` | chat commands (never sent to the world) |
| `!goal iron_tools` | force a goal; `!goal` lists them |

## Logs

`%APPDATA%\.minecraft\mc-autopilot\logs\run-YYYY-MM-DD.jsonl` has one line per decision:
`{t, layer, gs, brain, trigger, goal, options, choice, x, idx, prop}` (x: the state features the
learned model trains on), plus
skill results with failure codes, deaths, milestones and portal-path checkpoints.
`progress-<world>.json` keeps the furthest milestone; `lessons.json` counts which actions fail
and why, across runs.

## Honest limitations

- **The Nether is rare.** Since the diamond route (iron kit, diamonds at depth, obsidian from a
  lava pool there, a frame in a dug room) about 8% of natural games enter the Nether, late (25-35
  game minutes), and one has held a blaze rod. The funnel in gens 76-85 (80 games): 44 saw lava,
  22 reached diamond depth, 12 made a diamond pickaxe, 6 mined 10 obsidian, and those 6 built,
  lit and entered a portal.
- **Past the first blaze rod: implemented, never reached.** Ender pearls, stronghold search, filling the end portal
  and the dragon fight have never run in a trial. Known gaps: eyes of ender lead to the
  stronghold but there's no search for the portal room inside it; thrown eyes aren't picked back
  up; the speedrun route never makes a bow, so the dragon's healing crystals can't be shot; angry
  endermen and piglins aren't fought back. Reaching the dragon is a long shot for any AI today.
- **Milestones 8-10 count the first item** (one blaze rod, one pearl, one eye), not the 6/12/12
  the goals need.
- **Deaths:** about 2 per 40-minute game in the loop's recent natural runs (3 per game over gens
  30-85).
- Tool and armor milestones stay reached after a death; the planner rebuilds lost tools on the
  way to the next one instead of starting the ladder over.
- Single-player only on purpose; Baritone on servers can break rules.
- When you quit the game you may get a "shutdown watchdog" crash report. Baritone 1.20.0's worker
  threads keep the game from exiting (fixed upstream only on its 1.21.4 branch, PR #5123). It
  happens after the world has saved, so nothing is lost.
- Only tested on Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.161.0 / Baritone 1.20.0.

## Tests

`cd mod`, then:
- `.\gradlew.bat test`: unit tests.
- `.\gradlew.bat runClientGameTest`: starts the game, makes a fresh survival world, and lets the rules brain play for 5 minutes (`-PtestMinutes=10`).
  `-PtestScenario=portal|cast|stronghold|end` stages one late-game step instead (gear and, for
  `portal`/`cast`, a lava pool), using commands in the throwaway test world only.
  `-PtestTask="craft furnace:1" -PtestGive="cobblestone 8,crafting_table"` runs one skill alone.
  Screenshots and logs end up in `mod/build/run/clientGameTest`.
- **Parallel trials on GitHub (free):** the "Trial runs" workflow plays one world per run on
  GitHub's Linux machines at the same time and uploads each decision log. `scripts/cycle` (bash,
  needs a GitHub token or `gh`) pushes, starts a batch, waits, downloads the logs and prints a
  one-page scoreboard (`scripts/summarize_batch`); results per batch are in `docs/batches.md`,
  and `python scripts/plot_progress` redraws `docs/progress.svg` from it.
- `scripts/local-trial.ps1` runs one trial on your own PC in the same layout.

## Credits

Built on [Fabric](https://fabricmc.net) and [Baritone](https://github.com/cabaletta/baritone) (LGPL-3.0).
Inspired by [Mineflayer](https://github.com/PrismarineJS/mineflayer), [Mindcraft](https://github.com/kolbytn/mindcraft),
[Voyager](https://voyager.minedojo.org), and Jev-driven bots like rudrasingh500/jev_minecraft and rmalde/minecraft-agent.
What's different here: the bot drives *your* character in an untouched vanilla world, plays fair
(no x-ray), and improves itself through a paired-seed learning loop instead of prompting an LLM.
