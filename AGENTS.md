# AGENTS.md: working on MC Autopilot as Codex

This repo has two AI contributors: **Codex** (ChatGPT Pro) and **Claude Code** (Claude plan).
The project's full rules are in `CLAUDE.md`. Read it first: every rule there applies to you too.
The plan for the learning loop is in `docs/learning-loop.md`.

## What the project is
A Fabric mod (Java 25, Minecraft Java 26.3) that plays the user's own character in a vanilla
single-player survival world, using Baritone for pathing, and tries to kill the Ender Dragon.
A learning loop on GitHub Actions (`.github/workflows/loop.yml`) plays batches of trial games,
scores them, and keeps only changes that beat the current champion over paired seeds.

## Who does what
- **Codex: skills in the mod** (`mod/src/main/java/io/github/plrlr/autopilot/skills/`, `plan/`,
  reflexes). You fix how the bot plays: fighting, fleeing, sheltering at night, building the
  portal, and so on. Tasks come as GitHub issues labeled `codex`.
- **Claude: the loop and the harness** (`scripts/loop/`, the workflows, scoring, drills,
  analysis of results) and reviewing your pull requests.
- **The loop is the referee.** A change is not better because either of us thinks so. It is
  better when it wins the loop's paired-seed race.

## How to make a change
1. Work on a branch named `codex/<short-topic>` and open a pull request into `dev`.
   Never push to `main`, `dev` or `trial-results` directly. `dev` is merged into `main` in
   tested batches every few generations (each push to `main` changes what the loop plays).
2. **Put every behavior change behind a gene** in `mod/.../autopilot/Tune.java`:
   `gene("area.name", def, min, max, Kind.BOOL, "why")`, with the default giving the
   **old behavior**. Read it with `Tune.on("area.name")` (switch), `Tune.i(...)` (whole number) or `Tune.get(...)`.
   A pure bug fix (a crash, a wrong condition that can never be right) may skip the gene. Say so
   in the PR.
3. **Queue the race:** add an entry with your new gene(s) set to the new behavior to the
   `suggest` list in `scripts/loop/settings.json`, e.g. `{"combat.retreat_v2": 1}`. The loop
   races it against the champion. If it wins, the champion plays with it from then on.
4. The PR check (`.github/workflows/pr-check.yml`) compiles and runs the unit tests. It must
   pass. You probably can't build locally (Fabric's maven and Mojang's hosts may be blocked in
   your sandbox), so rely on the check and read its log when it fails.
5. PR description: what the bot did wrong (with the evidence: death causes, skill failures),
   what you changed, the gene name, and the suggestion you queued.

## Evidence to work from
- `trial-results` branch, `loop/history.jsonl`: one line per generation (scores, milestones
  reached, deaths). `loop/state.json` → `insights`: lessons the loop has already written down.
- `loop/data/gen-*/eval-*.jsonl.gz`: per-game rows. `{"k":"d","a":"<action>"}` is a decision,
  `{"k":"death","detail":"<cause>"}` a death, plus `milestone` and `checkpoint` rows.
- `docs/lessons.md`: game and API facts learned the hard way. Read it before touching combat,
  mining or the portal.

## Rules that are easy to break (all from CLAUDE.md)
- Play fair: only normal player controls and what a player could see. No x-ray, no reading
  hidden blocks, no seed cracking, no commands or cheats, no world edits.
- Never bundle Baritone in our jars (it stays compileOnly/runtimeOnly, never `include`).
- Never block the render thread. Every AI, network and process call runs off-thread, with a
  timeout.
- The user is always in control: the toggle key and any movement key stop the autopilot. On any
  error, stop and stand still.
- Don't edit these (the loop depends on them staying stable): `Tune.java` except adding new genes,
  `brains/Learned.java`, `log/`, `state/StateBuilder.java`.
- Minecraft 26.x ships unobfuscated (Mojang names). Check current Fabric and Baritone sources
  before using a method. Don't guess names.
- Never commit API keys, worlds, `logs/`, `build/`, `.gradle/`, or Minecraft jars.
- Small classes. Comments explain why. Match the style of the code around your change.
