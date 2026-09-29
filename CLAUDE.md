# MC Autopilot: a Minecraft bot that learns from its own trials

## Goal
The AI plays the user's own character in a plain vanilla single-player survival world (Fabric
mod + Baritone) and tries to beat the game (kill the Ender Dragon). The bot improves itself
between trial runs: an automatic loop plays batches on GitHub Actions, scores them, and keeps
only changes that beat the current best (docs/learning-loop.md: the plan and build order).
Public GitHub project (github.com/Plrlr/mc-autopilot). Report the furthest milestone honestly.

Since 2026-09-28 it is a self-learning bot with one brain: the rules planner, re-ranked by the
learned model the loop trains (brains/Brain.java). No language model plays the game; the in-game
Opus/Groq/Gemini/Cerebras brains and their settings file were removed. The user reviews; Claude
builds in parts, each a PR into dev.

## Hard constraints
- The world is plain vanilla single-player survival: no server, no world edits, no cheats or
  commands. Only the client gets mods (Fabric + our mod + Baritone). The world save stays vanilla.
- The AI plays fair: it acts only through the normal player controls and the information a
  player could get (no x-ray, no reading hidden blocks, no seed cracking). Baritone's own
  x-ray-like helpers must be off: legitMine is always on (Baritone only targets blocks it can
  see, else branch-mines at a sensible Y). Logs, sand, gravel, stone and spotted ores are mined
  only from blocks in WorldMemory (seen by the player; skills/SeenMiner, FlintSteps). Baritone is
  never asked to search loaded chunks, and draws no path or goal markers.
- Baritone is never bundled in our release jars (it's compileOnly/runtimeOnly in build.gradle,
  never `include`): users install it separately from its own release page, as the README says.
- The user is always in control: a toggle key turns the autopilot off instantly, and any
  movement key the user presses also turns it off. Fail safe: on any error, stop and stand still.
- Everything must work for $0 (the loop runs on GitHub's free machines).
- Never block the game thread: the brain decides in microseconds, and any file or network work
  runs off the render thread with a timeout. Minecraft must stay at normal fps.
- Never commit: API keys, the user's worlds, logs/, build/, .gradle/, or Minecraft/Mojang jars.
  Never print API keys in logs or chat.
- Ask before installing software. Prefer giving the user the exact `winget` command to run.
- Check current docs before using any API (Fabric, Baritone, claude CLI).
  Don't guess method names, mappings, model names, limits, or response fields.

## Stack
Minecraft Java 26.3, Fabric Loader + Fabric API, Baritone 1.20.0 (installed separately), our mod
in Java 25 (Gradle, Loom). Versions, install paths and Windows details: docs/setup.md.


## Layout
- `mod/`: the Fabric mod (Java). `src/main/.../autopilot/`: Autopilot (main loop), plan/
  (Goal ladder, TechTree, Planner), skills/ (code skills over Baritone), brains/ (Brain: rules +
  Learned), state/ (WorldMemory, no x-ray), log/ (JSONL decision log). `src/gametest/`: the
  in-game trial (fresh world or a staged scenario), run by CI.
- The Nether route (since 2026-09-29, the user's call): iron kit, diamonds at depth, obsidian
  from a lava pool there (skills/ObsidianMold: one block at a time in a pit), a frame in a dug
  room (dig_portal); plan/RouteSteps.deepStep. Digs are planned from visible blocks only
  (skills/FairProbe). The old bucket-cast route races only as a control.
- The learning loop (docs/learning-loop.md): genes in `Tune.java`, the learned brain in
  `brains/Learned.java`, `scripts/loop/` (loop.py, train.py, evolve.py, dashboard.html),
  `.github/workflows/loop.yml` (one generation per run, self-dispatching), state and data in
  `loop/` on the `trial-results` branch. `scripts/laptop-loop.ps1` is the laptop's part.
- `.github/workflows/trials.yml`: hand-started batches (one machine per run) and quick task tests;
  results committed to `trial-results` (runs/<id>/). Both workflows share `.github/actions/play`.
- `scripts/cycle` (run a batch and summarize), `scripts/summarize_batch`, `scripts/plot_progress`,
  `scripts/local-trial.ps1` (one trial on this PC; never while the user is playing).
- `docs/`: learning-loop.md (the plan), design.md (how the mod works, per section),
  batches.md (one line per batch), lessons.md (game and API facts learned), roadmap.txt (game
  strategy per stage), setup.md, research-notes.md, history.md. docs/archive/: the retired
  multi-session process; read only if asked.

## Working rules
- Branches: `main` (what the loop plays), `dev` (work in progress), and the data branch
  `trial-results`. Build and test on `dev` (compile check: `gh workflow run trials.yml --ref dev -f
  seeds='[]' -f extra='[]'`; scenario tests with `-f extra=...`), then merge into `main` in batches
  every few generations with one summary: each push to main changes the next generation's code.
- Codex (ChatGPT Pro) also works here, by the rules in AGENTS.md: skill fixes as pull requests from
  `codex/*` branches into `dev`, each behind a gene with a queued suggestion. Claude owns the loop and
  the harness and reviews Codex's PRs (they must pass pr-check.yml; merge only what keeps these rules).
- Save tokens: read a batch's summary.md, not its logs, unless the summary points at a failure.
  Don't read whole large files (Autopilot.java, Planner.java) when a grep will do.
- Improve the bot through the loop: a new gene, a new skill, a better score or feature, a fix to
  the harness. A behavior change by hand should still race (put it behind a gene or let it run
  as a code genome) rather than be judged by eye.
- Judge changes by the loop's paired-seed score, never one batch against another (noise is
  large: the same code gave iron 8/8 and 4/8). Full natural runs are the only README numbers;
  staged scenario runs are labeled as such.
- Test worlds: Easy difficulty, never Peaceful.
- Small classes, comments that explain why. Every network and process call has a timeout.
- Commit after each change with a clear message.
