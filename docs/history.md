# History and decisions

Moved out of CLAUDE.md; CLAUDE.md links here. Batch-by-batch results are in docs/batches.md.

## Decisions
- 2026-09-27: restart around a self-improving loop (docs/learning-loop.md). 19 cloud batches of
  hand-diagnosed fixes by four coordinating sessions plateaued at iron tools (~6:00, ~1.2
  deaths/run, no natural portal) and cost the most tokens. Branches consolidated into main;
  the multi-session docs moved to docs/archive/.
- 2026-09-26: Opus only sets goals (strategist); the per-action choices go to free LLMs or rules,
  since the steps toward a goal are repetitive. A measured `claude -p` call took about 7 s
  (3.4 s API time), fine at event-driven rates.
- 2026-09-26: target is beating the game in 30 minutes. Speedrun route: food, iron armor and
  diamonds are optional rungs; the nether portal is cast from lava with two buckets.
- 2026-09-26: OPUS_MAX_CALLS_PER_HOUR default lowered from 30 to 10; periodic goal checks every
  10 minutes.
- 2026-09-26: improvement loop runs on GitHub Actions (cloud trials), see docs/lessons.md.

## Phases
Status 2026-09-26: phases 0-6 have code; cloud trials cover the early game (wood to iron tools
reached in 4-5 minutes on 2 of 4 seeds in batch 4). Late-game skills (portal, stronghold, dragon)
compile and are wired up but are untested in real play; the cast scenario is being debugged.
0. Reset: new plan, JDK 25, Fabric for 26.3, Fabric API, Baritone. Mod folder ready.
1. Mod skeleton: Gradle project builds a jar; keybind opens the panel; on/off toggle; HUD line;
   user-input and toggle kill switch; state snapshot printed to the log.
2. Skills and mock brain: Baritone integration (with fair-play settings), reflexes, the core
   skills, mock tactician, triggers, JSONL log. Test: from spawn to a stone pickaxe on mock alone.
3. Opus: strategist through claude -p, the ladder, Opus as tactician. Test: reach iron tools.
4. Free brains: groq and gemini tacticians, limiter, fallback chain, keys in the config file.
5. Survival depth: smelting, shelter and sleep, combat, armor, diamonds, portal.
6. Late game: nether, blaze rods, pearls, eyes, stronghold, the dragon fight.
7. Show-off: recording tips, README with setup, results per brain, honest limitations,
   and credits (docs/research-notes.md).
