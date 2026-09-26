# MC Autopilot: Opus plays vanilla Minecraft survival for you

## Goal
The user loads a normal single-player survival world, presses a key to open a small in-game panel,
and turns on the autopilot. From then on the AI controls the user's own character and tries to
beat the game (kill the Ender Dragon). Claude Opus 5.5 is the brain that decides what to do,
reached through `claude -p` on the user's Claude plan (no API key). Free-tier LLMs (Groq, Gemini)
and a free rule-based brain can stand in for fast, frequent decisions.
Public GitHub project. The demo: a video of the run, and how far each brain setup gets.

Background facts, limits, and sources are in docs/research-notes.md. Game strategy per stage
(overworld to dragon) is in docs/roadmap.txt: read it before working on a stage. It was written for
an older Mineflayer version of this project; its game facts and loop rules apply, its Mineflayer
and server parts don't.

## How it works (three layers)
1. **Strategist: Opus via `claude -p`.** Picks the current objective from the milestone ladder
   (below), with a short reason and a plan of steps. Called on events only: objective done or
   failed, death, dimension change, stuck for 30 s, or at most every 60 s. A measured call took
   about 7 s (3.4 s API time) on 2026-09-26, which is fine at this rate.
2. **Tactician: picks the next skill** and its argument from a short candidate list, whenever a
   skill ends. Default `auto`: the first free LLM with a key (groq, cerebras, gemini), else
   `mock` (rules, free, always works, the fallback for everything). The user decided on
   2026-09-26 that Opus should only set goals, since the steps toward a goal are repetitive.
   Still swappable to `opus` (same `claude -p` route) or any single provider.
3. **Skills and reflexes: plain Java code, no AI.** Skills do the work (walk, mine, craft, fight),
   using Baritone for pathfinding and mining. Reflexes react instantly in code: eat when hungry,
   fight or back off from mobs in range, step away from lava and fire, stop falling into holes.

## Hard constraints
- The world is plain vanilla single-player survival: no server, no world edits, no cheats or
  commands. Only the client gets mods (Fabric + our mod + Baritone). The world save stays vanilla.
- The AI plays fair: it acts only through the normal player controls and the information a
  player could get (no x-ray, no reading hidden blocks, no seed cracking). Baritone's own
  x-ray-like helpers must be off: legitMine is on for ores (Baritone only targets ores it can
  see, else branch-mines at a sensible Y). Surface blocks (logs, sand, gravel, stone) should only
  be searched among blocks in WorldMemory (seen by the player). Until collect does that, they
  use Baritone's normal search of loaded chunks, and the README must document this exception.
- Baritone is never bundled in our release jars (it's compileOnly/runtimeOnly in build.gradle,
  never `include`): users install it separately from its own release page, as the README says.
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
  build (Temurin 25 JDK installed 2026-09-26 in C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot;
  set JAVA_HOME to it when running gradlew).
- `claude` CLI 2.1.283 (%USERPROFILE%\.local\bin\claude.exe). ClaudeCli finds claude.exe on PATH
  and starts it directly (cmd /c only for an npm .cmd shim). Java doesn't escape quotes in Windows
  arguments, so every argument goes through ClaudeCli.winQuote (MSVC rules).
- Node.js was installed for the old plan and isn't needed now.

## Project layout
```
mc-build-crew/            (the folder name is historical; the project is MC Autopilot)
  CLAUDE.md  START_HERE.md  README.md  docs/research-notes.md  mc-autopilot.env.example
  mod/                    Fabric mod (Gradle, Loom 1.17, no mappings: 26.x ships unobfuscated)
    build.gradle          Baritone is read straight from its GitHub release (ivy repo)
    src/main/java/io/github/plrlr/autopilot/
      AutopilotMod.java     entry point: K key, tick loop, HUD, "!" chat commands
      Autopilot.java        main loop: triggers, reflexes, skill lifecycle, death, takeover
      Config.java Mc.java Items2.java Progress.java
      state/                WorldMemory (seen blocks, no x-ray), Perception (mobs, items), StateBuilder (JSON)
      plan/                 Goal (13-rung ladder), TechTree (recipes/sources), Planner (options), Option
      skills/               Skill base, Skills menu, Bari (Baritone), one class/group per skill
      brains/               ClaudeCli (claude -p), Backends (opus, groq, gemini), Tactician,
                            Strategist, RateLimiter, Decision, Prompts
      log/RunLog.java       JSONL decision log
      ui/                   PanelScreen (K panel), Hud (status line)
    src/main/resources/autopilot-prompts/   strategist.txt, tactician.txt (plain text, easy to tweak)
    src/test/               unit tests (gradlew test)
    src/gametest/           in-game test: fresh survival world, autopilot plays (gradlew runClientGameTest);
                            -PtestScenario=portal|cast|stronghold|end stages a late-game step with test-world commands
.github/workflows/trials.yml  cloud trial batches (one machine per seed, plus staged extras); logs as artifacts
```
Runtime files live in the Minecraft folder, not the repo:
`%APPDATA%\.minecraft\config\mc-autopilot.env` (settings and API keys, created on first start),
`%APPDATA%\.minecraft\mc-autopilot\logs\` (run-DATE.jsonl, usage-DATE.json),
`%APPDATA%\.minecraft\mc-autopilot\progress-<world>.json`, and `claude-cwd/` (empty dir for claude -p).

## In-game UI
- A keybind (default K, rebindable in Controls) opens a small panel that doesn't pause the game:
  Autopilot on/off, action brain (auto / mock / groq / cerebras / gemini / opus), goals by Opus or rules, "ask Opus
  for a new goal", the current goal and Opus's reason, recent decisions, Opus calls this hour.
- A one-line HUD in the corner while autopilot is on: brain, objective, current skill.
- Chat commands typed by the user (not sent to the world): !start, !stop, !status,
  !brain <name>, !goal <name>, !opus on|off.
- When autopilot turns on, set pauseOnLostFocus off so alt-tab doesn't pause the world;
  restore the user's setting when it turns off.

## Planner behavior (free, in code)
- Goals 1-6 (tools, food, armor, diamonds) stay done once Progress has reached them; lost tools
  are rebuilt through the tech tree on the way to the next goal. Rules never pick survive_night.
- Speedrun route (target: beat the game in 30 minutes): the rules skip the optional rungs 3 (food),
  5 (iron armor) and 6 (diamonds). Food is upkeep (below). Right after iron tools and a bucket,
  the portal step casts the frame from a lava pool (build_portal in cast mode, CastPortal: a lava
  bucket + a water bucket against a wall of throwaway blocks), so no diamond pickaxe is needed; it
  adds a second bucket and flint and steel first. make_obsidian (harden a pool, then mine it)
  stays for runs that already have a diamond pickaxe. Iron is mined and smelted in one batch
  (Planner.ironStillNeeded).
- Food: keep 8+ cooked food as an upkeep target. Hunt 3-5 animals in one trip when they're in
  view, cook all raw meat in one furnace load. Eat at food <= 14, or <= 17 when health isn't full
  (regeneration needs 18+); never try to eat at full hunger (it just times out).
- Our own crafting table and furnace are picked back up before walking off (pickup stations).
- Option order: urgent (fight, eat, heal, sleep, shelter), recover items at the death spot,
  upkeep (cook, hunt toward 8+ cooked food, wool for a bed, coal in view), the goal step, extras.
- Explore takes what to look for ("cow,pig", "log", "nether_bricks,blaze", "any"), keeps a
  straight heading into unvisited 64-block regions (WorldMemory), stops when the target is seen.
- Baritone may only use cobblestone as scaffolding once 24+ are carried (Bari.updateThrowaway).

## Milestone ladder (the strategist chooses among these; track the furthest one reached)
1 wood and crafting table, 2 wooden then stone tools, 3 food source and survive the first night (optional),
4 iron tools, bucket, shield, 5 iron armor (optional), 6 diamonds and diamond pickaxe (optional),
7 obsidian and a nether portal (cast from lava right after rung 4), 8 nether fortress and blaze rods, 9 ender pearls, 10 eyes of ender,
11 find the stronghold, 12 activate the end portal, 13 kill the Ender Dragon.
Be honest in docs: later milestones are very hard for any AI. Report the furthest one reached.

## Skills (the tactician's menu, 20 max; 20 implemented)
collect item:n, craft item:n, smelt output:n, attack <mob>, shoot <mob>, eat, equip
armor|shield|weapon|pickaxe, place <block>, pickup, explore <what to look for>, goto <known
block>|death, retreat, shelter, sleep, fill_bucket water|lava, make_obsidian obsidian:n,
build_portal (placed block by block, not Baritone's builder; cast from lava without 10 obsidian), enter_portal
nether|overworld|end, locate_stronghold, fill_end_portal. Shelter, sleep, eat, craft, smelt and the portal skills are not
interruptible by routine re-checks (heartbeat, new goal); danger reflexes still interrupt them.
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
- Cap with OPUS_MAX_CALLS_PER_HOUR (default 10 for the strategist, separate cap for Opus as
  tactician). When the cap or plan limit is hit, keep the current objective and tell the user.

## Tactician rules (LLM backends)
- Temperature 0 where the provider allows. Short system prompt. Output about 60 tokens max.
- JSON only: {"choice": "<one option label, e.g. collect log:3>", "why": "..."}. The planner
  builds the option list (rules' pick first); the schema's enum is exactly those labels.
  Use the provider's JSON mode or schema option.
- Validate against the menu and candidates. On invalid output, retry once with a one-line
  correction; if still invalid, use mock for this decision and count it as invalid.
- On 429 or a limit near its cap: back off with jitter, then switch to the other free provider,
  then to mock. Tell the user in the HUD when this happens.
- Log one JSONL line per decision: {t, layer, brain, trigger, options, choice, valid,
  latency_ms, tokens_in, tokens_out, result}.

## When to ask the tactician (event-driven, not every tick)
A skill finished or failed, a hostile mob came within 8 blocks, health dropped, the objective
changed, stuck for 12 s (the skill is restarted), or no decision for 20 s (30 s for free LLMs,
60 s for Opus) while an interruptible skill runs. Heartbeats are skipped without a call when the
rules still want the running action. Reflexes never wait for an AI.

## Loop rules (improvement cycles on trial batches; from docs/roadmap.txt Part 1)
- Verify milestones from world state (inventory, blocks, dimension, entity deaths), never from the
  bot's own report. Progress.update does this.
- Scoreboard per seed and batch: milestones and the time each was reached, deaths and causes,
  time spent on food / crafting / mining / travel / combat / idle, top failure reasons (lessons).
- Accept a change only if the target stage improves across the seed set (median time down or
  success rate up) and no earlier stage gets worse; otherwise revert it.
- Keep 4 fixed benchmark seeds (a, b, c, d) for comparisons; every ~5 batches add 1-2 fresh seeds
  to catch overfitting.
- Two kinds of runs, always labeled: full runs (natural, no commands; only these numbers go in
  the README) and scenario runs (portal, cast, stronghold, end: the harness stages a later stage
  with test-world commands to iterate fast on late-game skills).
- Code does mechanics, the brain chooses: anything precise (bucket casting, bridging, crystal
  shooting, triangulation) is a deterministic skill; the AI only picks which skill runs next.
- Every skill has preconditions, a postcondition checked from world state, a timeout, a max retry
  count and a specific failure reason ("timed out" alone isn't one).
- Test worlds use Easy or Normal difficulty, never Peaceful (blazes and endermen don't spawn
  there). The benchmark is fixed at Easy.
- One focused change per cycle where possible, so each batch shows what helped.
- GitHub Actions jobs stop after 6 hours: for longer runs, checkpoint the world and run state at
  each milestone (artifact or cache) and resume in the next job. Artifacts on a public repo are
  public: never include the .env file or keys.
- Cap the cycles per day and log what each cycle cost.

## Phases (one at a time; stop and report after each, with what the user should test)
Status 2026-09-26: phases 0-6 have code; the in-game test covers the early game. Late-game
skills (portal, stronghold, dragon) compile and are wired up but are untested in real play.
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
