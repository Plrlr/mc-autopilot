# MC Autopilot

Let Claude Opus play **your own character** in a normal single-player Minecraft survival world
and try to beat the game. Press **K** in your world, switch the autopilot on, and watch.

**It teaches itself.** A learning loop runs on GitHub's free machines around the clock. Every
generation plays fresh random worlds, races challengers against the current champion on the
same worlds, and keeps only changes that clearly win: its tunable numbers, its own model
trained on every decision it has made, and (capped, on your plan) small code changes written by
Claude. Watch it live: **[plrlr.github.io/mc-autopilot](https://plrlr.github.io/mc-autopilot/)**.
How it works: [docs/learning-loop.md](docs/learning-loop.md).

**Where it stands (hand-tuned code, before the loop, Easy):** it reliably gets wood and stone
tools, and iron tools on 7 of 8 seeds in about 6 game minutes. No natural run has reached the
Nether yet; a staged test with a ready lava pool lit a portal and went through once. Everything
past the Nether portal is code that no run has reached. Details in [Results so far](#results-so-far).

- **Opus sets the goals.** Opus 5.5 decides what to work on ("get iron tools", "find a nether
  fortress") and changes course when something fails. Free rules work out the steps. Opus runs
  through Claude Code's headless mode (`claude -p`) on your Claude plan, so you don't need an API
  key, and it's called at most 10 times an hour by default.
- **Code does the hands.** Walking, mining and pathfinding use [Baritone](https://github.com/cabaletta/baritone).
  Crafting goes through the recipe book, and fighting, eating and sleeping use the normal player controls.
- **Fair play.** No cheats, no commands, no x-ray. Ores are only mined once seen (Baritone runs
  in `legitMine` mode and otherwise branch-mines). Gravel for flint comes only from gravel the AI
  has seen (lake and river beds included), and one gravel is placed and broken until flint drops.
  Logs, sand and ores it has spotted (say, in a cave wall) are mined only from blocks it has
  actually seen; with none in sight it explores, or branch-mines for ores and stone. Baritone is
  never asked to search the loaded chunks, and it draws no path or goal markers on screen.
- **You stay in control.** Any movement key (WASD, space) instantly gives control back.
- **$0.** Opus uses your existing plan. The per-action choices go to a free AI (Groq, Cerebras or
  Gemini) when you paste a free key into the settings file, and to free rules otherwise.

## How it works

```
Strategist (Opus via claude -p)   picks the goal from a 13-rung ladder: on events (goal done, failing,
        |                         stuck, death, new dimension) and at most every 10 minutes otherwise
Tactician (auto: a free AI if a key is set, else rules)   picks the next action from the planner's list
        |
Skills + reflexes (plain Java, Baritone)   collect, craft, smelt, attack, eat, shelter, build_portal, ...
```

**Strategist.** Opus gets a short game-state summary (position, health, inventory, what's in
view, the last result) and answers with a goal, a one-sentence reason and a
few steps. With goals set by rules instead (`!opus off`), the same ladder is walked in order.

**Tactician.** For the current goal the **planner** knows the tech tree (log → planks → table →
pickaxe → ...) and builds a short list of sensible options, rules' pick first: urgent things
(fight, eat, heal), getting items back from a death spot, upkeep, then the goal's next step. The
tactician chooses one, so an LLM can't invent impossible actions. A bad answer gets one retry,
then the rules decide.

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
Dragon. The rules take a speedrun route: rungs 3, 5 and 6 are optional, and the portal is cast
from a lava pool with two buckets (no diamonds needed).

**The trial loop.** Progress is measured, not guessed. A GitHub Actions workflow plays 8 fixed
seeds for 20 game minutes each, one Linux machine per world, and uploads every decision log.
`scripts/cycle` starts a batch, waits, downloads the logs and prints a one-page scoreboard:
milestones and the time each was reached (checked from the world: inventory, dimension, blocks,
never from the bot's own report), deaths by cause, and the most common failure codes. The rule is
to keep a change only if the batches show it helped; run-to-run noise makes that hard (below). Staged scenarios (a ready lava pool, a place in the End)
use commands in a throwaway test world to test late-game skills quickly; their numbers are
reported separately and never counted as results.

## Results so far

![Share of runs reaching each milestone, and the median time, per trial batch](docs/progress.svg)

From [docs/batches.md](docs/batches.md): cloud trials, natural runs (no commands), rules brain
for both goals and actions, Easy. Batches 1-6: 4 seeds, 10-15 game minutes. From batch 8: 8 seeds,
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
- **Only the rules brain has been measured.** Opus as strategist and the free LLM tacticians
  are implemented, but haven't been benchmarked against the rules yet. Milestones 8-13 have never been
  reached, in any setup.

## Setup (Windows)

You need Minecraft Java Edition, a Claude plan with [Claude Code](https://code.claude.com) installed and
logged in (`claude` works in PowerShell), and Java 25 to build the mod.

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

The first start writes `%APPDATA%\.minecraft\config\mc-autopilot.env`. Open it in Notepad to set
the brain, Opus call caps, and optional free API keys. That file lives outside this project, so
keys can't end up on GitHub. Each key needs a free account that you make yourself:

| Provider | Get a key | Default model |
|---|---|---|
| Groq | [console.groq.com/keys](https://console.groq.com/keys) | `openai/gpt-oss-20b` |
| Cerebras | [cloud.cerebras.ai](https://cloud.cerebras.ai) | `gpt-oss-120b` |
| Gemini | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | `gemini-3.5-flash-lite` |

Free tiers allow a limited number of requests per minute and per day; check each provider's page
for the current numbers. Paste a key after `GROQ_API_KEY=` (or the Cerebras or Gemini line) and
restart Minecraft. With `TACTICIAN=auto` the first provider with a key is used, the others are the
fallback when it hits a limit, and the rules take over when all of them are out.

## Controls

| | |
|---|---|
| **K** | panel: on/off, action brain (auto / mock / groq / cerebras / gemini / opus), goals by Opus or rules, what it's thinking |
| **WASD / space** | take control back immediately |
| `!stop` `!start` `!status` | chat commands (never sent to the world) |
| `!brain auto\|mock\|groq\|cerebras\|gemini\|opus` | switch the action brain (`mock` is the free rules) |
| `!goal iron_tools` | force a goal; `!goal` lists them |
| `!opus on\|off` | goals by Opus or by rules |

## Opus and your plan

Every Opus call counts toward your Claude plan's usage limits. A measured call took about 7 s.
The mod starts `claude` in an empty folder with its own short system prompt and no tools, to keep
calls small. By default Opus only sets goals: it's asked when a goal is done, when things keep
failing or get stuck, after a death or dimension change, and every 10 minutes otherwise, never
more than **10** times an hour (`OPUS_MAX_CALLS_PER_HOUR`; settings files made before this change
keep their old value of 30 until you edit them). Actions go to free brains, never to Opus unless
you ask. If you do switch actions to Opus (`!brain opus`), those calls are capped separately at
120/hour (`OPUS_TACTICIAN_MAX_CALLS_PER_HOUR`). When a cap or plan limit is hit, the rules take
over and the panel says so.

## Logs

`%APPDATA%\.minecraft\mc-autopilot\logs\run-YYYY-MM-DD.jsonl` has one line per decision:
`{t, layer, brain, trigger, options, choice, valid, latency_ms, tokens_in, tokens_out}`, plus
skill results with failure codes, deaths, milestones and portal-path checkpoints.
`progress-<world>.json` keeps the furthest milestone; `lessons.json` counts which actions fail
and why, across runs.

## Honest limitations

- **It hasn't reached the Nether on its own.** Casting the portal works on a staged lava pool,
  once so far; real pools with odd shapes, lava deep in caves and nights spent on other upkeep
  have stopped every natural run.
- **Nether and later: implemented, never reached.** Fortress search and blaze fights are new
  code that hasn't been in a trial batch yet; ender pearls, stronghold search, filling the end portal
  and the dragon fight have never run in a trial. Known gaps: eyes of ender lead to the
  stronghold but there's no search for the portal room inside it; thrown eyes aren't picked back
  up; the speedrun route never makes a bow, so the dragon's healing crystals can't be shot; angry
  endermen and piglins aren't fought back. Reaching the dragon is a long shot for any AI today.
- **Milestones 8-10 count the first item** (one blaze rod, one pearl, one eye), not the 6/12/12
  the goals need.
- **Deaths:** 14 in batch 9's eight 20-minute runs (in batch 8b, 16 of 24 happened while fleeing).
- **Only the rules brain is benchmarked.** No numbers yet for Opus goals or the free LLMs.
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
  Add `-PtestOpus=true` to also run Opus. Screenshots and logs end up in `mod/build/run/clientGameTest`.
- **Parallel trials on GitHub (free):** the "Trial runs" workflow plays one world per run on
  GitHub's Linux machines at the same time and uploads each decision log. `scripts/cycle` (bash,
  needs a GitHub token or `gh`) pushes, starts a batch, waits, downloads the logs and prints a
  one-page scoreboard (`scripts/summarize_batch`); results per batch are in `docs/batches.md`,
  and `python scripts/plot_progress` redraws `docs/progress.svg` from it.
- `scripts/local-trial.ps1` runs one trial on your own PC in the same layout (and can use Opus).

## Credits

Built on [Fabric](https://fabricmc.net) and [Baritone](https://github.com/cabaletta/baritone) (LGPL-3.0).
Inspired by [Mineflayer](https://github.com/PrismarineJS/mineflayer), [Mindcraft](https://github.com/kolbytn/mindcraft),
[Voyager](https://voyager.minedojo.org), and Jev-driven bots like rudrasingh500/jev_minecraft and rmalde/minecraft-agent.
What's different here: the AI drives *your* character in an untouched vanilla world, with Opus as the
decision-maker through your own Claude plan.
