# MC Autopilot: Opus plays vanilla Minecraft survival for you

## Goal
The user loads a normal single-player survival world, presses a key to open a small in-game panel,
and turns on the autopilot. From then on the AI controls the user's own character and tries to
beat the game (kill the Ender Dragon). Claude Opus 5.5 is the brain that decides what to do,
reached through `claude -p` on the user's Claude plan (no API key). Free-tier LLMs (Groq, Gemini)
and a free rule-based brain can stand in for fast, frequent decisions.
Public GitHub project. The demo: a video of the run, and how far each brain setup gets.

Background facts, limits, and sources are in docs/research-notes.md.

## How it works (three layers)
1. **Strategist: Opus via `claude -p`.** Picks the current objective from the milestone ladder
   (below), with a short reason and a plan of steps. Called on events only: objective done or
   failed, death, dimension change, stuck for 30 s, or at most every 60 s. A measured call took
   about 7 s (3.4 s API time) on 2026-09-26, which is fine at this rate.
2. **Tactician: picks the next skill** and its argument from a short candidate list, whenever a
   skill ends. Swappable: `opus` (same `claude -p` route; slower but smartest), `groq`, `gemini`,
   or `mock` (rules, free, always works, the fallback for everything).
3. **Skills and reflexes: plain Java code, no AI.** Skills do the work (walk, mine, craft, fight),
   using Baritone for pathfinding and mining. Reflexes react instantly in code: eat when hungry,
   fight or back off from mobs in range, step away from lava and fire, stop falling into holes.

## Hard constraints
- The world is plain vanilla single-player survival: no server, no world edits, no cheats or
  commands. Only the client gets mods (Fabric + our mod + Baritone). The world save stays vanilla.
- The AI plays fair: it acts only through the normal player controls and the information a
  player could get (no x-ray, no reading hidden blocks, no seed cracking). Baritone's own
  x-ray-like helpers (e.g. mining ores it can't see) must be turned off where the setting exists.
- The user is always in control: a toggle key turns the autopilot off instantly, and any
  movement key the user presses also turns it off. Fail safe: on any error, stop and stand still.
- Everything must work for $0. Opus runs on the user's Claude plan through `claude -p`
  (counts toward plan usage limits, so cap calls with OPUS_MAX_CALLS_PER_HOUR). Free API limits
  are tight: use a client-side rate limiter and fall back to mock when a limit is near.
- Never block the game thread: every AI call and network call runs off the render thread, with a
  timeout. Minecraft must stay at normal fps while the AI thinks.
- Never commit: API keys, the user's worlds, logs/, build/, .gradle/, or Minecraft/Mojang jars.
  Never print API keys in logs or chat.
- Ask before installing software. Prefer giving the user the exact `winget` command to run.
- Check current docs before using any API (Fabric, Baritone, Groq, Gemini, claude CLI).
  Don't guess method names, mappings, model names, limits, or response fields.
- Never send personal info to free AI APIs (free-tier inputs may be used for training).
  Game state only.

## Stack
- Windows 11, PowerShell. Minecraft Java **26.3** (installed in the official launcher).
- Fabric Loader + Fabric API for 26.3 (Fabric API 0.161.0+26.3 was newest on 2026-09-26).
- Baritone **v1.20.0** (Fabric build, "For Minecraft 26.3", LGPL-3.0) for pathfinding and mining,
  used through its API from our mod.
- Our mod: Java 25, built with Gradle (Fabric Loom, via the Gradle wrapper). Needs a JDK 25 to
  build (the Temurin 25 JRE is installed; the JDK is not yet).
- `claude` CLI 2.1.283 on PATH (C:\Users\alexe\.local\bin\claude.exe). Spawn it from Java with
  ProcessBuilder through `cmd /c` in case it's a .cmd shim.
- Node.js was installed for the old plan and isn't needed now.

## Project layout
```
mc-build-crew/            (the folder name is historical; the project is MC Autopilot)
  CLAUDE.md  START_HERE.md  docs/research-notes.md
  mod/                    Fabric mod source (Gradle project)
    src/main/java/.../
      AutopilotMod.java     entry point, keybinds, tick hook
      ui/                   in-game panel (toggle, brain choice, goal) and HUD status line
      state/                compact JSON game state for the brains
      skills/               one class per skill; async, with timeout; returns {ok, detail}
      reflexes/             instant safety reactions in code
      brains/               Strategist (claude -p), tactician backends: mock, opus, groq, gemini
      limiter/              per-provider requests/min, tokens/min, daily counts (persisted)
      log/                  JSONL decision log
  prompts/                 strategist and tactician prompts (plain text, easy to tweak)
```
Runtime files live in the Minecraft folder, not the repo:
`%APPDATA%\.minecraft\config\mc-autopilot.env` (API keys and settings, never in the repo) and
`%APPDATA%\.minecraft\mc-autopilot\logs\` (JSONL logs, daily usage counters).

## In-game UI
- A keybind (default K, rebindable in Controls) opens a small panel that doesn't pause the game:
  Autopilot on/off, tactician brain (mock / opus / groq / gemini), the current objective and
  Opus's reason, recent decisions, and Opus calls used this hour.
- A one-line HUD in the corner while autopilot is on: brain, objective, current skill.
- Chat commands typed by the user (not sent to the world): !stop, !status, !brain <name>.
- When autopilot turns on, set pauseOnLostFocus off so alt-tab doesn't pause the world;
  restore the user's setting when it turns off.

## Milestone ladder (the strategist chooses among these; track the furthest one reached)
1 wood and crafting table, 2 wooden then stone tools, 3 food source and survive the first night,
4 iron tools, bucket, shield, 5 iron armor, 6 diamonds and diamond pickaxe, 7 obsidian and a
nether portal, 8 nether fortress and blaze rods, 9 ender pearls, 10 eyes of ender,
11 find the stronghold, 12 activate the end portal, 13 kill the Ender Dragon.
Be honest in docs: later milestones are very hard for any AI. Report the furthest one reached.

## Skills (the tactician's menu, 20 max)
idle, explore(direction), goto(target), collect(block, n), craft(item, n), smelt(item, n),
equip_best(tool|weapon|armor), eat, place(block), build_shelter, sleep, attack(entity),
retreat, pick_up_items, deposit(chest), withdraw(chest, item, n), report_status.
Skills are code, not AI. Crafting uses the normal crafting screens (recipe book clicks),
not commands. Each skill has a timeout and returns {ok, detail}.

## State sent to the brains (target under 600 tokens)
Dimension, position, health, food, armor, time of day, weather, inventory summary (item: count),
held item, current objective, nearest useful blocks the player can see (type, distance),
nearby entities (type, distance, hostile?), last skill and result, stuck flag, deaths so far.
Short keys, rounded numbers.

## Strategist rules (Opus via claude -p)
- Command shape: pipe the state JSON on stdin into
  `claude -p "<task>" --model opus --tools "" --no-session-persistence --output-format json
  --json-schema '<schema>'` and read `structured_output`. Run it in an empty working directory
  and replace the default system prompt (--system-prompt) so no CLAUDE.md or tool definitions
  are loaded: less plan usage and lower latency.
- Output: {objective: one ladder milestone or sub-goal, reason: one sentence, steps: [<=5 short]}.
- Validate against the schema. On failure, keep the last objective and retry once later.
- Cap with OPUS_MAX_CALLS_PER_HOUR (default 30 for the strategist, separate cap for Opus as
  tactician). When the cap or plan limit is hit, keep the current objective and tell the user.

## Tactician rules (LLM backends)
- Temperature 0 where the provider allows. Short system prompt. Output about 60 tokens max.
- JSON only: {"skill": "<one menu name>", "arg": "<one listed candidate or null>"}.
  Use the provider's JSON mode or schema option.
- Validate against the menu and candidates. On invalid output, retry once with a one-line
  correction; if still invalid, use mock for this decision and count it as invalid.
- On 429 or a limit near its cap: back off with jitter, then switch to the other free provider,
  then to mock. Tell the user in the HUD when this happens.
- Log one JSONL line per decision: {t, layer, brain, trigger, options, choice, valid,
  latency_ms, tokens_in, tokens_out, result}.

## When to ask the tactician (event-driven, not every tick)
A skill finished or failed, a hostile mob came within 8 blocks, health dropped, the objective
changed, stuck for 10 s, or 20 s with no decision (heartbeat). Reflexes never wait for an AI.

## Phases (one at a time; stop and report after each, with what the user should test)
0. Reset: new plan (this file), JDK 25, Fabric for 26.3, Fabric API, Baritone. Mod folder ready.
1. Mod skeleton: Gradle project builds a jar; keybind opens the panel; on/off toggle; HUD line;
   user-input and toggle kill switch; state snapshot printed to the log. Test in a new world.
2. Skills and mock brain: Baritone integration (with fair-play settings), reflexes, the core
   skills (explore, goto, collect, craft, equip_best, eat, pick_up_items), mock tactician,
   triggers, JSONL log. Test: from spawn to a stone pickaxe on mock alone.
3. Opus: strategist through claude -p, the ladder, Opus as tactician. Test: reach iron tools.
4. Free brains: groq and gemini tacticians, limiter, fallback chain, keys in the config file.
5. Survival depth: smelting, shelter and sleep, combat, armor, diamonds, portal. One milestone
   at a time, test each.
6. Late game: nether, blaze rods, pearls, eyes, stronghold, the dragon fight.
7. Show-off: recording tips, README with setup, results per brain, honest limitations,
   and credits (docs/research-notes.md).

## Style
- Small classes, comments that explain why.
- Every network call and process call has a timeout.
- Commit after each phase with a clear message.
