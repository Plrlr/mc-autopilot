# Tasks for the laptop session (local Minecraft, Opus on the user's plan)

Written 2026-09-26 by the cloud session that runs the improvement loop on GitHub Actions. The cloud
runs 8+ trials in parallel but only with the free rules brain, at ~0.75x speed, and nobody watches
them. The laptop can do what the cloud can't: Opus as the goal-picker (claude -p on the user's
plan), real speed with a GPU, and watching the game. One Minecraft at a time, so run these one
after another, in this order. Budget: about 4 hours.

## Ground rules

- Work on branch `claude/autopilot-trial-runs-gdcq8y`. Before each run: `git pull` (the cloud session
  pushes fixes all the time) and note the commit in your results.
- **Don't edit the Java code, CLAUDE.md or the workflow** on this branch: the cloud session is
  changing them right now and parallel edits would conflict. If you find a bug, describe it in
  your results with the log lines and your proposed fix; the cloud session will make the change.
- Report results in `docs/laptop-results.md` on a separate branch `claude/laptop-results`
  (create it from this branch; commit only that file, never `.trials/`, worlds or logs), push it,
  and tell the user. The cloud session reads it with `git fetch`.
- Never press WASD/space in the game window during a run: any movement key turns the autopilot off.
- Build needs JDK 25: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"`.
- One run: `.\scripts\local-trial.ps1 ...` (see its header). It saves logs like a cloud batch and
  prints the one-page summary (`scripts/summarize_batch`, needs Python). Read CLAUDE.md's "Trial
  loop" and docs/lessons.md first; docs/batches.md has the cloud results so far.

## Task 1: Opus vs rules on the same seeds (about 90 minutes)

The question for the project: does Opus choosing the goals beat the free rules? Same commit,
same seeds, 20 game minutes each (night starts at ~10.75 min):

    .\scripts\local-trial.ps1 -Batch local-brains -Seed a -Minutes 20
    .\scripts\local-trial.ps1 -Batch local-brains -Seed a -Minutes 20 -Opus
    .\scripts\local-trial.ps1 -Batch local-brains -Seed b -Minutes 20
    .\scripts\local-trial.ps1 -Batch local-brains -Seed b -Minutes 20 -Opus

Report per run: milestone times (m1, m2, m4, m7), portal-path checkpoints, deaths and causes,
the top failures, Opus calls used and their latency (the jsonl `layer: strategist` lines), and
the goals Opus picked with its reasons (quote a few). Say where Opus chose differently from the
rules and whether it helped.

## Task 2: watch the portal cast (about 20 minutes)

The staged `cast` scenario gives iron gear, two buckets, flint and steel, 64 cobblestone, and a
lava pool 7 blocks east and water 6 blocks west. The bot should wall off a 4x6 area, pour lava
into each frame spot, harden it with water beside it, scoop the water back, and light the portal.
In the cloud it got as far as two obsidian blocks, then failed to scoop the water.

    .\scripts\local-trial.ps1 -Batch local-cast -Seed a -Scenario cast -Minutes 10

Watch the game window. Report what you see step by step: does the wall go up behind the frame,
where does each lava and water pour land, does the scoop work, does the frame finish, does it
light. Grep trial.log for `[cast]`, `[bucket]` and `[station]` lines and include them. Take
screenshots of anything odd (F2 saves one to mod\build\run\clientGameTest\screenshots). If it
fails, say exactly at which step and what the world looked like there.

## Task 3: one full 30-minute run, watched (about 35 minutes)

    .\scripts\local-trial.ps1 -Batch local-long -Seed c -Minutes 30

The target is beating the game in 30 minutes; the cloud runs haven't reached the Nether yet.
Watch especially: night (from ~10.75 min: does it shelter, hide, sleep, die?), fights (does it
flee into danger?), anything that looks stupid to a human player (walking in circles, standing
still, digging into lava). Note the game time of each problem.

## Task 4 (if time is left): repeat Task 2 after pulling the latest fixes

The cloud session keeps fixing the cast from your Task 2 report and its own batches. Pull,
rerun the cast scenario, report again.
