# MC Autopilot: a Minecraft bot that learns from its own trials

## Goal
The AI plays the user's own character in a plain vanilla single-player survival world (Fabric
mod + Baritone) and tries to beat the game (kill the Ender Dragon). The bot improves itself
between trial runs: an automatic loop plays batches on GitHub Actions, scores them, and keeps
only changes that beat the current best (docs/learning-loop.md: the plan and build order).
Public GitHub project (github.com/Plrlr/mc-autopilot). Report the furthest milestone honestly.

Opus 5.5 (through `claude -p` on the user's plan, no API key) sets goals in play and writes at
most a few patches a day in the loop; free LLMs or the rules brain pick the actions.

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
Minecraft Java 26.3, Fabric Loader + Fabric API, Baritone 1.20.0 (installed separately), our mod
in Java 25 (Gradle, Loom), the `claude` CLI. Versions, install paths and Windows details: docs/setup.md.


## Layout
- `mod/`: the Fabric mod (Java). `src/main/.../autopilot/`: Autopilot (main loop), plan/
  (Goal ladder, TechTree, Planner), skills/ (code skills over Baritone), brains/ (Opus, free
  LLMs, rules), state/ (WorldMemory, no x-ray), log/ (JSONL decision log). `src/gametest/`: the
  in-game trial (fresh world or a staged scenario), run by CI.
- `.github/workflows/trials.yml`: one batch = one machine per run; results committed to the
  `trial-results` branch (runs/<id>/summary.md and decision logs).
- `scripts/cycle` (run a batch and summarize), `scripts/summarize_batch`, `scripts/plot_progress`,
  `scripts/local-trial.ps1` (one trial on this PC; never while the user is playing).
- `docs/`: learning-loop.md (the plan), design.md (how the mod works, per section),
  batches.md (one line per batch), lessons.md (game and API facts learned), roadmap.txt (game
  strategy per stage), setup.md, research-notes.md, history.md. docs/archive/: the retired
  multi-session process; read only if asked.

## Working rules
- One branch: `main`, plus the data branch `trial-results`. No coordination between sessions.
- Save tokens: read a batch's summary.md, not its logs, unless the summary points at a failure.
  Don't read whole large files (Autopilot.java, Planner.java) when a grep will do.
- Judge changes by the loop's paired-seed score, never one batch against another (noise is
  large: the same code gave iron 8/8 and 4/8). Full natural runs are the only README numbers;
  staged scenario runs are labeled as such.
- Test worlds: Easy difficulty, never Peaceful.
- Small classes, comments that explain why. Every network and process call has a timeout.
- Commit after each change with a clear message.
