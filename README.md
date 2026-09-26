# MC Autopilot

Let Claude Opus play **your own character** in a normal single-player Minecraft survival world
and try to beat the game. Press **K** in your world, switch the autopilot on, and watch.

- **Opus decides.** Opus 5.5 picks the goals ("get iron tools", "find a nether fortress") and,
  by default, every action. It runs through Claude Code's headless mode (`claude -p`) on your
  Claude plan, so you don't need an API key.
- **Code does the hands.** Walking, mining and pathfinding use [Baritone](https://github.com/cabaletta/baritone).
  Crafting goes through the recipe book, and fighting, eating and sleeping use the normal player controls.
- **Fair play.** No cheats, no commands, no x-ray. The AI only knows about blocks it has
  actually seen, and Baritone runs in `legitMine` mode.
- **You stay in control.** Any movement key (WASD, space) instantly gives control back.
- **$0.** Opus uses your existing plan. Free Groq or Gemini keys and a free rules brain can make
  the per-action choices instead.

## How it works

```
Strategist (Opus via claude -p)   picks the goal from a 13-step ladder, ~once a minute or on events
        |
Tactician (Opus / Groq / Gemini / rules)   picks the next action from a short list the planner builds
        |
Skills + reflexes (plain Java, Baritone)   collect, craft, smelt, attack, eat, shelter, build_portal, ...
```

The **planner** knows the tech tree (log → planks → table → pickaxe → ...). For the current goal
it builds a short list of sensible options, with the rules' pick first. The tactician chooses one
of them, so an LLM can't invent impossible actions. Each answer is checked against the list;
a bad answer gets one retry, and after that the rules decide.

**Reflexes** don't wait for any AI: fight back when hit, back away from creepers and when low on
health, eat when starving, and jump out of lava.

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
4. Start Minecraft with the **fabric-loader-26.3** profile.
5. Make a **new test world** (the AI will dig, die and lose things), press **K**, click **Autopilot: OFF**.

The first start writes `%APPDATA%\.minecraft\config\mc-autopilot.env`. Open it in Notepad to set
the brain, Opus call caps, and optional free API keys (Groq / Gemini). That file lives outside
this project, so keys can't end up on GitHub.

## Controls

| | |
|---|---|
| **K** | panel: on/off, action brain (opus / mock / groq / gemini), goals by Opus or rules, what it's thinking |
| **WASD / space** | take control back immediately |
| `!stop` `!start` `!status` | chat commands (never sent to the world) |
| `!brain opus\|mock\|groq\|gemini` | switch the action brain |
| `!goal iron_tools` | force a goal; `!goal` lists them |
| `!opus on\|off` | goals by Opus or by rules |

## Opus and your plan

Every Opus call counts toward your Claude plan's usage limits. One decision is ~4k tokens in
(mostly cached) and ~100 out, and takes ~7-13 s. Defaults: at most **30** goal calls and
**120** action calls per hour (`OPUS_MAX_CALLS_PER_HOUR`, `OPUS_TACTICIAN_MAX_CALLS_PER_HOUR`).
When a cap or plan limit is hit, the rules take over and the panel says so. If you want Opus
to spend less, set the action brain to `mock` and keep Opus for the goals.

## Logs

`%APPDATA%\.minecraft\mc-autopilot\logs\run-YYYY-MM-DD.jsonl` has one line per decision:
`{t, layer, brain, trigger, options, choice, valid, latency_ms, tokens_in, tokens_out}`, plus
skill results, deaths and milestones. `progress-<world>.json` keeps the furthest milestone.

## Honest limitations

- Early game (wood → stone → iron → armor) is where it's reliable. Diamonds take a long time
  with fair (non-x-ray) branch mining.
- Obsidian: the AI only mines obsidian it has seen (lava pools with water, ruined portals). It
  doesn't do bucket-casting yet.
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
  Add `-PtestOpus=true` to also run Opus for 2 minutes. Screenshots and logs end up in `mod/build/run/clientGameTest`.

## Credits

Built on [Fabric](https://fabricmc.net) and [Baritone](https://github.com/cabaletta/baritone) (LGPL-3.0).
Inspired by [Mineflayer](https://github.com/PrismarineJS/mineflayer), [Mindcraft](https://github.com/kolbytn/mindcraft),
[Voyager](https://voyager.minedojo.org), and Jev-driven bots like rudrasingh500/jev_minecraft and rmalde/minecraft-agent.
What's different here: the AI drives *your* character in an untouched vanilla world, with Opus as the
decision-maker through your own Claude plan.
