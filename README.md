# MC Autopilot

Let Claude Opus play **your own character** in a normal single-player Minecraft survival world
and try to beat the game. Press **K** in your world, switch the autopilot on, and watch.

- **Opus sets the goals.** Opus 5.5 decides what to work on ("get iron tools", "find a nether
  fortress") and changes course when something fails. Once the goal is set, free rules work out
  the steps. Opus runs through Claude Code's headless mode (`claude -p`) on your Claude plan, so
  you don't need an API key, and it's called at most 10 times an hour by default.
- **Code does the hands.** Walking, mining and pathfinding use [Baritone](https://github.com/cabaletta/baritone).
  Crafting goes through the recipe book, and fighting, eating and sleeping use the normal player controls.
- **Fair play.** No cheats, no commands, no x-ray. Ores are only mined once seen (Baritone runs
  in `legitMine` mode and otherwise branch-mines). One documented exception: for surface blocks
  (logs, stone, sand, gravel) Baritone searches the chunks the game has loaded, not only blocks
  the AI has looked at. A player scanning the horizon finds trees just as fast, but it is wider
  knowledge than strict line of sight.
- **You stay in control.** Any movement key (WASD, space) instantly gives control back.
- **$0.** Opus uses your existing plan. The per-action choices go to a free AI (Groq, Cerebras or
  Gemini) when you paste a free key into the settings file, and to free rules otherwise.

## How it works

```
Strategist (Opus via claude -p)   picks the goal from a 13-step ladder, ~once a minute or on events
        |
Tactician (auto: a free AI if a key is set, else rules)   picks the next action from the planner's list
        |
Skills + reflexes (plain Java, Baritone)   collect, craft, smelt, attack, eat, shelter, build_portal, ...
```

The **planner** knows the tech tree (log → planks → table → pickaxe → ...). For the current goal
it builds a short list of sensible options, with the rules' pick first. The tactician chooses one
of them, so an LLM can't invent impossible actions. Each answer is checked against the list;
a bad answer gets one retry, and after that the rules decide.

**Upkeep** is built into the planner, the way a good player does it on the way: eat and heal
after fights, hunt a nearby animal when food runs low and cook the meat, kill sheep for a bed and
sleep every night (the bed is packed up again in the morning), grab coal that's in view, and walk
back for the items after a death. Without a bed, it digs a covered shelter only when it's on the
surface at night with little armor; underground or armored, it keeps working.

**Exploring** heads in a straight line into ground it hasn't visited, stops the moment it sees
what it's looking for (animals, trees, a fortress), and turns away from open water.

**Reflexes** don't wait for any AI: fight back when hit, back away from creepers and when low on
health, eat when starving, swim up when out of air, and jump out of lava.

The goal ladder: 1 wooden tools, 2 stone tools, 3 food and survive a night, 4 iron tools,
5 iron armor, 6 diamond pickaxe, 7 nether portal, 8 blaze rods, 9 ender pearls, 10 eyes of ender,
11 find the stronghold, 12 enter the End, 13 kill the Ender Dragon.

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

| Provider | Get a key | Free limits (Sept 2026) |
|---|---|---|
| Groq | [console.groq.com/keys](https://console.groq.com/keys) | 30 requests/min, 1,000/day |
| Cerebras | [cloud.cerebras.ai](https://cloud.cerebras.ai) | 5 requests/min, 1M tokens/day |
| Gemini | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | Flash-Lite, a few requests/min |

Paste a key after `GROQ_API_KEY=` (or the Cerebras or Gemini line) and restart Minecraft. With
`TACTICIAN=auto` the first provider with a key is used, the others are the fallback when it hits
a limit, and the rules take over when all of them are out.

## Controls

| | |
|---|---|
| **K** | panel: on/off, action brain (auto / mock / groq / cerebras / gemini / opus), goals by Opus or rules, what it's thinking |
| **WASD / space** | take control back immediately |
| `!stop` `!start` `!status` | chat commands (never sent to the world) |
| `!brain auto\|mock\|groq\|cerebras\|gemini\|opus` | switch the action brain |
| `!goal iron_tools` | force a goal; `!goal` lists them |
| `!opus on\|off` | goals by Opus or by rules |

## Opus and your plan

Every Opus call counts toward your Claude plan's usage limits. One call is ~10k tokens in
(mostly cached) and ~250 out, and takes ~7-13 s. By default Opus only sets goals: it's asked when
a goal is done, when things keep failing or get stuck, after a death or dimension change, and
every 10 minutes otherwise, never more than **10** times an hour (`OPUS_MAX_CALLS_PER_HOUR`;
settings files made before this change keep their old value of 30 until you edit them).
In testing, the rules picked the same action as Opus most of the time, which is why actions
go to free brains, never to Opus unless you ask. If you do switch actions to Opus (`!brain opus`), those calls are capped
separately at 120/hour (`OPUS_TACTICIAN_MAX_CALLS_PER_HOUR`). When a cap or plan limit is hit,
the rules take over and the panel says so.

## Logs

`%APPDATA%\.minecraft\mc-autopilot\logs\run-YYYY-MM-DD.jsonl` has one line per decision:
`{t, layer, brain, trigger, options, choice, valid, latency_ms, tokens_in, tokens_out}`, plus
skill results, deaths and milestones. `progress-<world>.json` keeps the furthest milestone.

## Honest limitations

- Tool and armor milestones stay reached after a death; the planner rebuilds lost tools on the
  way to the next one instead of starting the ladder over.
- Early game (wood → stone → iron → armor) is where it's reliable. Diamonds take a long time
  with fair (non-x-ray) branch mining.
- Nether portal: without a diamond pickaxe it casts the frame in place next to a lava pool
  (a lava bucket and a water bucket against a wall of cobblestone, one obsidian at a time).
  With a diamond pickaxe it can instead harden a pool with water and mine 10 obsidian. Both
  are tested only on a staged lava pool so far; real pools with odd shapes may defeat them.
- The dragon fight needs a bow and arrows to destroy the healing crystals, and the planner
  doesn't go out of its way to get them. Without a bow it only hits the dragon when it perches.
- Nether fortress search, stronghold triangulation and the dragon fight are implemented but
  simple. Expect deaths. Reaching the dragon on its own is a long shot for any AI today.
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
  one-page scoreboard (`scripts/summarize_batch`); results per batch are in `docs/batches.md`.

## Credits

Built on [Fabric](https://fabricmc.net) and [Baritone](https://github.com/cabaletta/baritone) (LGPL-3.0).
Inspired by [Mineflayer](https://github.com/PrismarineJS/mineflayer), [Mindcraft](https://github.com/kolbytn/mindcraft),
[Voyager](https://voyager.minedojo.org), and Jev-driven bots like rudrasingh500/jev_minecraft and rmalde/minecraft-agent.
What's different here: the AI drives *your* character in an untouched vanilla world, with Opus as the
decision-maker through your own Claude plan.
