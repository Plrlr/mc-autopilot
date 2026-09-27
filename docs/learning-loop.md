# The learning loop

The bot improves itself between trials, with nobody tuning it by hand. It runs on GitHub's free
machines around the clock, and the laptop joins in when it's free. Live dashboard:
https://plrlr.github.io/mc-autopilot/ (the `trial-results` branch, served by GitHub Pages).

## Why this design (2026-09-27)
- Hand-diagnosed fixes (19 batches, four coordinating Claude sessions) plateaued at iron tools
  (~6:00, ~1.2 deaths/run, no natural portal ever lit) and cost the most tokens.
- Pure RL is out of budget: DreamerV3 needed ~17 GPU-days for one diamond; VPT used 720 GPUs.
- 2026's reported dragon kills are single runs (Opus-5 over MCP on a fixed seed in ~4 h; a 1.16.5
  seed-and-route speedrun). None learns across runs, none meets Manifold's bar (random seeds,
  < 150 min, player-visible info only). A bot that gets better by itself over trials, fair-play and
  on random seeds, is the new part.

## The three levels

**1. Genes (free).** Every hand-tuned threshold is a gene in `Tune.java` (32 of them: fight/flee
health, creeper distance, eating, food stock, gathering amounts, night and route switches, loop
timings), with hard limits. Defaults are the old values, so no params file means unchanged play.

**2. Its own model (free).** Every decision logs 32 state features, the option taken and its
probability. `train.py` learns, per action kind ("collect:log", "explore:lava"...), how much
progress follows in the next 2 game minutes (tools, key items, milestones, checkpoints, minus
deaths): a baseline value of the state plus each action's advantage over it (ridge regression,
inverse-propensity weighted). `Learned.java` re-ranks the rules' options by it. Its say is itself
a gene (`learned.weight`, 0 = pure rules), so it only gets used if the race shows it helps.
Emergencies (fight, flee, eat when starving) always stay with the rules. Data runs (champion +
`learned.explore` 0.15) try other options on purpose, so the model sees more than the rules' habits.

**3. Code changes by Claude (plan, capped).** When the gene search stalls (no new champion for 6
generations), `evolve.py` makes one `claude -p` call with the generation's failures, the worst
traces, every earlier attempt with its result and lesson (ExpeL-style memory), and the files the
failures point to. Its edits must apply exactly, stay out of frozen files (genes, model features,
logs, the harness, the scorer; DGM showed self-editing agents learn to fool their own judge), use
nothing command-like, and compile. The change races like a gene change; a winner is merged into
main. At most 3 calls a day. Needs the `CLAUDE_CODE_OAUTH_TOKEN` secret (below); without it the
loop runs levels 1 and 2 only.

## One generation (`.github/workflows/loop.yml`)
1. **propose** (`loop.py propose`): the champion, the challengers still racing, and new mutants
   (1-3 genes each; mostly children of the champion, sometimes of another strong genome from the
   archive), each on the same 4 fresh random seeds, plus 2 data runs. 18 machines.
2. **trial**: each run plays 20 game minutes of plain vanilla drawing, which already keeps up
   with real time on the cloud (0.98x). A 10 fps cap halved the game speed, so it isn't used.
3. **update**: scores every run (`common.score_run`: points per milestone and portal step, up to
   50% more the earlier, minus 0.75 per death), races, retrains the model, merges a winning code
   change, maybe asks Claude for one, writes `loop/history.jsonl` and the dashboard, and starts
   the next generation.

**Racing rule.** A challenger's score minus the champion's on the same seed is one pair. It
becomes champion with 8+ pairs, a mean gain of +0.3 or more, and a one-sided t of 2 or more;
it's dropped at a mean of 0 or less after 4 pairs, or undecided after 24. One promotion per
generation. The dethroned champion races the new one from scratch, so a lucky promotion gets
reversed by the same rule. Genes whose changes won get picked more often and mutated in bigger
steps (and vice versa). Noise is large (same code: iron 8/8 then 4/8), which is why nothing
is decided on one batch.

## Running it
- Start (runs until the daily cap of 36 generations, restarts itself hourly after that):
  `gh workflow run loop.yml`. A few only: `gh workflow run loop.yml -f generations=3`.
- Stop: add a file `loop/STOP` on the trial-results branch, or disable the workflow in the
  Actions tab. Status: `python scripts/loop/loop.py status --state <trial-results>/loop`.
- Settings live in `loop/state.json` → `settings` (seeds per generation, run length, caps,
  acceptance rule, perf mods). Edit them there; the next generation uses them.
- **Laptop:** `.\scripts\laptop-loop.ps1` plays the champion with its model in a visible window,
  run after run, with Sodium too. It waits while the Minecraft Launcher is open or memory is
  short, and sends each run to `loop/inbox/`. The next generation adds its decisions to the
  training data and shows its score (never used to promote: unpaired, different machine).
- **Level 3 on the cloud:** run `claude setup-token` on the laptop (it prints a token that is
  valid for a year and uses your plan), then add it as a repository secret named
  `CLAUDE_CODE_OAUTH_TOKEN` (Settings → Secrets and variables → Actions). Never paste it anywhere else.
- **Your own game:** `python scripts/loop/loop.py export --state <dir> --out params.json`, then put
  params.json (and loop/learned.json) in `%APPDATA%\.minecraft\mc-autopilot\`.

## Files
- `mod/.../Tune.java` genes; `brains/Learned.java` the model at play time.
- `scripts/loop/loop.py` propose / update / merge / status / export; `common.py` genes, scores,
  statistics; `train.py` the model; `evolve.py` code changes; `results.sh` the results branch;
  `dashboard.html` the page.
- `trial-results:loop/` state.json (genomes, champion, settings), history.jsonl (one line per
  generation), learned.json, data/ (compact decision logs per run), inbox/ (laptop runs).

## Sources
- Voyager (skill library, self-verification): https://arxiv.org/abs/2305.16291
- Darwin Goedel Machine (archive of self-modifying agents): https://arxiv.org/abs/2505.22954
- AlphaEvolve / OpenEvolve (LLM-guided code evolution with an evaluator): https://huggingface.co/blog/codelion/openevolve
- ExpeL (insights from past trials): https://arxiv.org/abs/2308.10144
- GITM (text knowledge + memory, diamonds on CPU): https://arxiv.org/abs/2305.17144
- DreamerV3 (cost of pure RL): https://arxiv.org/pdf/2301.04104
- irace / F-Race (racing configurations under noise); common random numbers (paired seeds).
- Opus-5 dragon run: https://huggingface.co/datasets/aibengineering/beat-the-game-minecraft
- Manifold criteria: https://manifold.markets/AdamK/will-an-ai-minecraft-agent-defeat-t-609shENQnu
