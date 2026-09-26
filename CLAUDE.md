# MC Build Crew: an AI crew that builds in Minecraft (Opus foreman + fast bot brains)

## Goal
The user types a build request ("build a small stone hut"). Opus, the foreman, turns it into a
blueprint and a job list. A small crew of Mineflayer bots gathers materials and builds it in
survival mode. Each bot's moment-to-moment choices come from a swappable "brain": free rules,
a free LLM API (Groq or Gemini), or Jev (a fast "System One" decision model) when credit exists.
This will be a public GitHub project. The demo is a timelapse plus a comparison of brains
(time, success rate, invalid answers, cost).

Background facts, limits, and sources are in docs/research-notes.md. Read it before Phase 3.

## Hard constraints
- The user's Windows laptop is weak (it barely runs Roblox on low graphics). Keep everything light:
  no Minecraft client needed, one server plus 1-3 headless bots, flat world, short view distance.
- Everything must work for $0. The mock brain must always work. Free API limits are tight, so
  respect them with a client-side rate limiter and fall back to mock when a limit is near.
- The Opus foreman runs through `claude -p` on the user's Claude plan. Call it rarely (see Foreman).
- The server is local only: bind to 127.0.0.1, offline mode, never port-forward or expose it.
- The user accepts Mojang's EULA themselves. Never set eula=true for them; ask them to do it.
- Never commit: .env, API keys, server/ (server jar and world; Mojang doesn't allow redistributing
  the server jar), logs/, node_modules/. Never print API keys in logs or console output.
- Ask before installing software. Prefer giving the user the exact `winget` command to run.
- Check current docs before using any API. Don't guess model names, limits, or response fields.
- Never send personal info to free AI APIs (free-tier inputs may be used for training).
  Game state only.

## Stack
- Windows 10/11, PowerShell. On Windows, spawn `claude` through the shell (it is often a .cmd shim).
- Java 25 (Minecraft 26.1's version manifest requires majorVersion 25; Java 21 is too old).
  Node.js 22 LTS or newer.
- Minecraft version: 26.1 exactly (Mineflayer 4.39.0's newest tested version, checked 2026-09-26).
- Minecraft Java server: the official vanilla server jar. Use the newest Minecraft version that
  Mineflayer officially supports. Check Mineflayer's README before downloading.
- Node packages: mineflayer, mineflayer-pathfinder. Later: mineflayer-collectblock and, optionally,
  prismarine-viewer for a browser view (skip it if it won't install on Windows).
- Plain JavaScript (ES modules). Load .env with a library or Node's --env-file.

## Project layout
```
mc-build-crew/
  CLAUDE.md  START_HERE.md  docs/research-notes.md
  .env.example   .env (never committed)
  server/             never committed: server.jar, world, eula.txt, server.properties
  scripts/start-server.ps1
  src/
    bot.js            spawns one bot, runs the decision loop
    skills/           one file per skill
    state.js          compact JSON state for the brain
    triggers.js       decides WHEN to ask the brain
    brains/mock.js    rule-based, free, always works (default)
    brains/llm.js     shared LLM brain: prompt, JSON parsing, validation, retry
    brains/groq.js    Groq backend (free tier)
    brains/gemini.js  Gemini backend (free tier)
    brains/jev.js     Jev backend (Vercel AI Gateway or TypeSafe direct)
    limiter.js        per-provider requests/min, tokens/min, daily counts (persisted)
    budget.js         dollar tracking for paid backends
    foreman.js        calls `claude -p`, or loads a saved plan in fixture mode
    crew.js           launches N bots, shared job board
    bench.js          runs the same task with each brain and writes a results table
  plans/              blueprints and job lists (commit a few as examples)
  logs/               JSONL per run plus daily usage counters (never committed)
```

## server.properties (starting values)
online-mode=false, server-ip=127.0.0.1, level-type=minecraft:flat, view-distance=4,
simulation-distance=4, max-players=5, difficulty=peaceful (raise later), gamemode=survival,
spawn-protection=0, generate-structures=false.
Start the JVM with -Xms512M -Xmx1G. Raise to 1536M only if the server lags.

## Bot skills (the action menu, 15 max)
idle, explore_nearby, goto, come_to_player, collect(block, n), craft(item, n), equip_best_tool,
place_block, build_next_step, deposit_to_stash, withdraw_from_stash(item, n), eat, flee_danger,
report_status.
Skills are plain code, not AI. Each is async, has a timeout, and returns {ok, detail}.
The brain only picks which skill runs next, plus an argument from a short candidate list.

## State sent to the brain (target under 600 tokens)
Position, health, food, time of day, inventory summary (item: count), current job from the job
board, nearest useful blocks (type, distance), nearby entities (type, distance, hostile?),
last skill and its result, and a stuck flag (no position change for N seconds).
Use short keys and round numbers. Small state is what keeps free tiers usable.

## When to ask the brain (event-driven, not every tick)
Run a cheap 1 s loop in code, but only call the brain when:
- the current skill finished or failed,
- a hostile mob comes within 8 blocks, or health drops,
- the foreman changes this bot's job,
- the bot is stuck for 10 s,
- or 20 s pass with no decision (heartbeat).
Stagger bots so their calls don't line up. Log one JSONL line per decision:
{t, bot, brain, trigger, options, choice, valid, latency_ms, tokens_in, tokens_out, result}.
Chat commands from the user: !stop (all bots idle), !status, !brain mock|groq|gemini|jev.

## Brain backends (env BRAIN)
- mock (default): rules only. Free and must always work.
- groq: Groq's OpenAI-compatible API, key GROQ_API_KEY, model GROQ_MODEL (a small, fast model on
  Groq's current free list). Main free AI brain.
- gemini: Google AI Studio key GEMINI_API_KEY, model GEMINI_MODEL (a Flash or Flash-Lite model;
  only those are free). Backup brain.
- jev: TypeSafe's Jev. JEV_ROUTE=vercel (Vercel AI Gateway, model id typesafe-ai/jev, key
  AI_GATEWAY_API_KEY, called via the AI SDK's experimental evaluate API) or JEV_ROUTE=typesafe
  (direct API, TYPESAFE_API_KEY). Optional; only used if the user has credit.

## LLM brain rules (groq and gemini)
- Temperature 0. Short system prompt. Max output about 60 tokens.
- Ask for JSON only: {"skill": "<one menu name>", "arg": "<one listed candidate or null>"}.
  Use the provider's JSON mode if it has one.
- Validate against the menu and candidates. On invalid output, retry once with a one-line
  correction. If still invalid, use mock for this decision and count it as invalid.
- On 429 or a limit near its cap: back off with jitter, then switch to the other free provider,
  then to mock. Tell the user in the console when this happens.

## Jev brain rules
- Text or JSON state in, typed answers out. No text generation, no images.
- In ONE request ask: next_skill (choice over the menu, each option with a one-line criterion),
  danger (noul), job_done (noul).
- Direct API: POST https://api.typesafe.ai/v1/systemone with `Authorization: Bearer <key>` and body
  {model: "jev-latest", state, questions: {name: {type, instructions, criteria}}}.
  Official JS SDK: @typesafe-ai/sdk. Read the current API reference for response fields.
- 402 billing_error means out of credit: switch to mock and tell the user. 429 or 529: back off.
- Budget guard: count input tokens at $0.042 per 1M. Stop Jev and switch to mock when this run's
  spend passes JEV_RUN_BUDGET_USD.

## Rate limiter (limiter.js)
Per provider and model: requests per minute, tokens per minute, requests per day. Read the limits
from .env (defaults set conservatively below the providers' published free limits). Persist daily
counts in logs/usage-YYYY-MM-DD.json so restarts don't reset them. Print usage every minute.

## Foreman (Opus through Claude Code headless mode)
- Invoke by piping a compact JSON summary on stdin into
  `claude -p "<instructions>" --output-format json`, then parse the result.
- FOREMAN_MODE=fixture loads a saved plan from plans/ instead of calling Opus. Use fixture mode
  for all development and tests so Opus is only called in real runs.
- When (live mode): once at start (user request to blueprint and job list), then at most every
  60 s, or right away on events (a bot stuck over 30 s, a job done, missing materials).
  Cap it with FOREMAN_MAX_CALLS_PER_HOUR.
- Blueprint (plans/<name>.json): {name, origin: {x,y,z}, blocks: [{dx,dy,dz,block}],
  materials: {block: count}}. Start tiny: a 5x5 dirt or cobblestone hut.
- Job list: [{id, type: gather|build|haul, target, qty, assigned_bot, status}].
  The foreman may also rewrite the one-line description the brain sees for each skill.
- Validate foreman output against a schema. Reject anything that doesn't match.

## Phases (one at a time; stop and report after each, with what the user should test)
0. Environment: check Java 21, Node 22+, and git. Create the folder layout, .gitignore, and .env
   from .env.example. `git init`. Give the user the commands for anything they need to install.
1. Server: download the right server jar, write server.properties and scripts/start-server.ps1,
   ask the user to accept the EULA, start the server, and confirm it listens on 127.0.0.1.
2. One bot with the mock brain: connect, spawn, event-driven loop, 5 skills (idle,
   explore_nearby, collect, come_to_player, report_status), chat commands, and logs.
3. Free AI brains: llm.js, groq.js, gemini.js, limiter.js, triggers.js, validation, and fallback.
   Test on "collect 16 dirt" with mock, then groq, then gemini.
4. Foreman: write plans/hut-5x5.json by hand first and build it in fixture mode. Then add live
   mode: user request to blueprint, and one bot gathers and builds it.
5. Crew: 2-3 bots, a shared stash chest, and job claims so bots don't fight over the same block.
6. Jev (optional, only if the user has a key) and bench.js: run the same task with every
   available brain and write docs/results.md (time, success, decisions, invalid answers, cost).
7. Show-off: timelapse instructions and a README with setup, results, honest limitations, and
   credits to prior work (listed in docs/research-notes.md).

## Style
- Small files, comments that explain why.
- Every network call has a timeout.
- Fail safe: on any error, bots idle instead of wandering.
- Commit after each phase with a clear message.
