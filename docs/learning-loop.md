# The learning loop (plan, 2026-09-27)

The bot improves itself between trials. No person or Claude session diagnoses batches by hand
any more: that cost the most tokens and stalled at iron tools (batches 13-19: iron ~6:00 on 7/8
seeds, ~1.2 deaths/run, no natural run ever lit a portal).

## Why this and not pure RL
- Pure RL is out of budget: DreamerV3 needed ~17 GPU-days for one diamond; VPT used 720 GPUs.
- Cloning our own logs only copies the rules brain (about 150 runs, all rule-made decisions).
- What works cheaply is the Voyager / SkillOpt pattern: the system changes its own behavior and
  keeps a change only if a held-out benchmark improves. We add a free parameter search under it.
- Novelty: 2026's reported dragon kills (Opus-5 over MCP on a fixed seed in ~4 h; a 1.16.5
  seed-and-route speedrun) are single runs. None learns across runs, none meets the Manifold
  bar (random seeds, < 150 min, player-visible info only). A bot that gets better by itself over
  trials, fair-play and on random seeds, is new.

## Level 1: parameter search (free, runs by itself)
1. **Knobs.** Every hand-tuned number and ordering moves into one `params.json` with defaults
   equal to today's code: fight/flee health, creeper distance, eat thresholds, food upkeep
   count, explore distances and headings, iron batch size, when to go for the portal, option
   order weights, skill timeouts. The game test reads it at start (no rebuild per candidate).
2. **Score per run** (dense, so the frontier gives signal): points per milestone reached, plus
   partial credit along the portal path (2 buckets, flint and steel, lava seen, obsidian, frame,
   lit), a bonus for reaching each earlier, minus a penalty per death.
3. **Search.** Each generation plays the incumbent and one or two candidates on the same seeds
   (paired, ~20 jobs, GitHub's concurrency cap). A candidate replaces the incumbent only if its
   paired score gain is clearly above noise (batches 8a/8b: same code, iron 8/8 vs 4/8). Candidates
   mutate a few knobs at a time; step sizes adapt to what has been accepted.
4. **Seeds.** A fixed set for pairing, plus fresh seeds rotated in every few generations to catch
   overfitting. Easy difficulty.
5. **Runs in the cloud, unattended.** A driver job reads `loop/state.json` on the trial-results
   branch (incumbent, history, seeds), dispatches the next generation, scores it, writes the
   state back, and dispatches again. To verify first: workflow_dispatch from GITHUB_TOKEN is
   allowed to start a new run.

## Level 2: code evolution (one Claude call per cycle)
Once Level 1 plateaus at a stage, one `claude -p` call gets: the scoreboard, the top failure codes,
2-3 short traces of the worst failures, and the files those failures point to. It returns one
patch. The patch runs against the incumbent on the paired seeds with the same accept rule;
rejected patches and their scores go into `loop/rejected.jsonl`, so the next call sees what
didn't work. Cap: a few calls per day (plan usage). To verify: running claude on the plan from
GitHub Actions (setup-token), or else run this step from the laptop on a schedule.

## Stage ladder
Natural runs never reach the portal, so the loop trains stage by stage from staged starts (the
existing scenarios: cast, portal, nether, blaze, stronghold, end) and promotes a stage once it
passes on most seeds. Full natural runs stay the only numbers for the README.

## Data we already have
`trial-results` branch: batch summaries and decision logs (JSONL: options, choice, skill result
with failure code, deaths, milestones) for batches 13-19. First step builds one table from them
(state, option chosen, outcome) as the loop's memory and the Level 2 failure bank.

## Build order
1. `params.json` + the code reading it (defaults = today's behavior; one batch proves no change).
2. Score function in `scripts/summarize_batch` (one number per run and per batch).
3. The driver (generation state on trial-results, paired accept rule, self-dispatch).
4. Level 2 patch step.

## Sources
- Voyager: https://arxiv.org/abs/2305.16291
- DreamerV3: https://arxiv.org/pdf/2301.04104
- Agent skills survey (SkillOpt, AutoSkill): https://arxiv.org/pdf/2605.07358
- Opus-5 dragon run dataset: https://huggingface.co/datasets/aibengineering/beat-the-game-minecraft
- Astra + Jev speedrun reproduction: https://github.com/teknium1/hermes-and-jev-play-minecraft
- Manifold criteria: https://manifold.markets/AdamK/will-an-ai-minecraft-agent-defeat-t-609shENQnu
