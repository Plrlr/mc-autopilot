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

**2. Its own model (free).** Every decision logs 40 state features, the option taken and its
probability. `train.py` learns how much progress follows in the next 2 game minutes (tools, key
items, milestones, checkpoints, minus deaths): a baseline of boosted trees for the state
(`gbt.py`, plain numpy), then small boosted trees per action kind ("collect:log", "explore:lava"...)
for its advantage over that baseline (inverse-propensity weighted, early-stopped on held-out runs).
Only the advantage trees ship; `Learned.java` (with `TreeModel.java`) re-ranks the rules' options
by them in microseconds. Its say is itself a gene (`learned.weight`, 0 = pure rules), and those
genes only race once the model's held-out advantage R2 passes `model_gate`. Emergencies (fight,
flee, eat when starving) always stay with the rules. Data runs (champion + `learned.explore` 0.15)
try other options on purpose, so the model sees more than the rules' habits. Honest state
(2026-09-28): on a shared tree baseline every model kind explains only ~2% of the choice part,
because the rules pick nearly the same option in the same state; the data runs are what can fix it.

**2b. Its danger model (free, learns fastest).** `train.py` also learns, from every decision and
every reflex (fights and escapes are logged since 2026-09-28), the chance of dying in the next 45
game seconds after taking an action in a state. Deaths are frequent (~3 per game) and their label
is exact, so this model is strong where the progress model is weak: held-out AUC 0.87 on the first
598 games. With gene `safety.hazard` the brain swaps any pick, emergencies included, for one whose
death risk is lower by at least `safety.hazard_margin` (at 0.1 that swaps ~2% of fight/flee/shelter
choices, at 0.05 ~21%). It is retrained every generation, so each game's deaths teach the next.

**3. Code changes by Claude (plan, capped).** When the gene search stalls (no new champion for 2
generations), `evolve.py` makes one `claude -p` call with the generation's failures, the worst
traces, every earlier attempt with its result and lesson (ExpeL-style memory), and the files the
failures point to. Its edits must apply exactly, stay out of frozen files (genes, model features,
logs, the harness, the scorer; DGM showed self-editing agents learn to fool their own judge), use
nothing command-like, and compile. The change races like a gene change; a winner is merged into
main. A code change races first (ahead of queued ideas), replayed on each generation's main so it
differs from the champion only by the change; one that never got a slot retires after 6 generations.
(Level 3 made no call from gen 41 to 85 because code genome g29 waited behind the queue and never
played, and evolve.py writes nothing while a code change "races"; fixed 2026-09-30.) At most 12 calls a day, after 2 generations without a new champion (scripts/loop/settings.json "evolve"). Needs the `CLAUDE_CODE_OAUTH_TOKEN` secret (below); without it the
loop runs levels 1 and 2 only.

**3b. New skills (level 3 v2, 2026-09-30; `evolve_skill.py`, setting `evolve.skills`).** "The bot
learns a new skill" means three steps, each by a different part:
1. *Claude writes it.* When one failure stays among the costliest for generations (failures.py's
   table, game minutes lost, among each generation's top 5 in at least half of the last 8) and the
   last level-3 attempt was an edit, evolve.py asks Claude for a NEW skill aimed at it, with the
   failure table, the skill stats of the failing action, examples with what led to them, earlier
   attempts, and the framework's source. The skill is new files in `skills/evolved/` only, written
   against `Player` (the fair-play facade: normal player keys, looking, mining and placing within
   reach of visible blocks, Baritone paths to a known position, blocks only if visible, a FairProbe,
   mobs in sight). evolve_skill.py itself adds its one registry line and its gene (`evolved.<skill>`,
   BOOL, default 0, appended to `evolved-genes.json`); the model never edits Tune.java, the framework
   or anything the scorer reads. Gates, cheapest first: `fairplay.py` (an allowlist of what the code
   may use; Mc, level, Baritone, Perception, threads, reflection, files and fully qualified names are
   rejected), the path check, pr-check's build (`gradlew test compileGametestJava`) and
   `test_evolved_genes.py`; one retry with the errors. It counts against `evolve.max_per_day`.
2. *The race keeps it.* Its branch `evolve/<id>` is drilled by trials.yml with drill.py's plan (3
   starts with and without the gene; trials.yml's unit-tests job is pr-check's Java check again). A
   PASS makes it a contender: it races first, as the champion plus its gene, replayed on each
   generation's main, judged by whether the failure it targets gets fixed (e.g.
   `resolve:shore:TIMEOUT+swim_out`: the share of shore timeouts followed by an ok shore or swim_out
   within 3 minutes), world by world, with the whole-game score as a safety check. A winner is
   merged into main like any code change. It must run to be judged (since 2026-10-04): a skill
   never offered and started in its drill is INCONCLUSIVE and doesn't race, and in the race one that
   acted in no game leaves as inconclusive (g164's swim_out "passed" its drill on `ok:shore`, which
   measured shore itself: the shore phase returned before evolved offers were asked).
3. *The brain learns when to use it.* Its option carries its own action key, so the neural brain
   grows a head for it once it has rows (data runs try it on purpose) and can re-rank it against
   the rules' order when `learned.weight` wins its race. EvolvedSkills also benches a skill for a
   minute after three failures in a row.

What it can't do: it can't learn motor skills from pixels or invent abilities the facade doesn't
expose (a new skill is Java composed of the player's controls, chosen by Claude from evidence, and
kept only if it wins); it doesn't learn inside one game (skills and genes change between
generations; within a game only the skill stats and the danger model adapt); and a skill judged by
a rare metric may need many generations to be decided. Test it by hand, sandboxed:
push a branch `evolve-test-run/<x>` (workflow `evolve-test.yml`).

## The road to the dragon (long-term design, 2026-09-27)

Tuning numbers can't give the bot abilities it lacks, and 20-minute runs from spawn only ever
practice the first 20 minutes. Five pieces make the whole game learnable:

1. **Checkpoint bank (Go-Explore).** A run that first reaches a stage saves the world: portal kit,
   Nether, 6 blaze rods, 12 eyes, stronghold found, the End (gametest `Bank.java`). The zips live in
   the `checkpoints` release (40 per stage, real ones before staged). Go-Explore solved hard
   exploration games by returning to states it had reached and exploring from there; this is
   the same idea. https://arxiv.org/abs/1901.10995
2. **Stage curriculum.** Every genome also plays stage starts (`stage_seeds`, 1 since 2026-09-28:
   the frontier, the first stage runs don't get past half the time; with 2 or more, one later
   stage too), from real saves or, until those exist, the staged scenario (cast, nether,
   stronghold, end; labeled synthetic). Stage runs score only what they gain after the start. The race, the model and the code step all see
   late-game play from the first day.
3. **Code where the game is lost.** Claude's code step (level 3) gets the frontier stage's
   failures first, and the files for the skills that failed. Stuck handling shows the kind of
   change genes can't make: a moving skill that stays inside a 2-block square for 12 s is aborted
   and the unstuck reflex swims, walks, tunnels or climbs out (user's idea, `Unstuck.java`).
4. **Marathons** (`marathon.yml`, every 8 hours): the champion plays 2 game hours from a fresh
   world with no staged starts. The honest number, and the source of real late-game checkpoints.
5. **A model that covers the whole game.** The learned brain's features include blaze rods,
   pearls, eyes, gold, known fortress/portal/frame and the dragon, and its progress measure
   rewards them, so data from stage runs trains it for the late game too.

**The diamond route (2026-09-29, the user's call).** The portal goes iron pickaxe → diamond depth
→ diamond pickaxe → obsidian from a pool at depth → a frame in a dug room (docs/design.md,
Planner behavior). The bank has a "diamond" stage between lava and the Nether (diamond pickaxe,
a bucket, flint and steel; the `mold` scenario stands in until a run banks one), the score (v3)
counts at_depth 0.5, diamond_pickaxe 1.5 and obsidian_10 2, and games are 40 minutes. The first
staged trials entered the Nether at 2:39 (a shallow pool) and 4:20 (a pool three deep).

Next, when the frontier reaches them: gene groups per stage (Nether and End thresholds), a quick
screening test for code changes before they race (cheap first, like AlphaEvolve's evaluation
cascade), and more parallel seeds once the cloud's speed is fixed (the renderer sets it:
~0.63x in 20-minute runs).

## One generation (`.github/workflows/loop.yml`)
1. **propose** (`loop.py propose`): the champion, the challengers still racing, and new mutants
   (1-3 genes each; mostly children of the champion, sometimes of another strong genome from the
   archive), each on the same tasks: 4 fresh random seeds and 2 stage starts; plus 2 data runs.
   20 machines (GitHub's limit at once).
2. **trial**: each run plays 20 game minutes of plain vanilla drawing, which already keeps up
   with real time on the cloud (0.98x). A 10 fps cap halved the game speed, so it isn't used.
3. **update**: scores every run (`common.score_run`: points per milestone and portal step, up to
   50% more the earlier; one life: only what came before the first death counts, plus up to 2
   points for the share of the run lived, minus 1 for dying; a death's game second comes from
   `clock.py`, score v4 since 2026-10-04), races, retrains the model, merges a winning code
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
- Settings live in `scripts/loop/settings.json` on main (defaults and meanings in `loop.py`
  DEFAULT_SETTINGS); the next generation uses them. Since 2026-09-28 each generation races the
  champion and 4 queued ideas (`max_genomes` 5, `suggest_slots` 4) on 4 paired tasks each: 2 fresh
  worlds, 1 checkpoint start, 1 combat drill, 20 jobs. An idea is crowned after 8 pairs (two
  generations) at t >= 2 and a gain of 0.3, dropped after 4 (mutations) or 8 (queued ideas) pairs
  at or below zero. It was one idea at a time on 16 pairs: 47 queued ideas would have taken days.
  More looks at smaller samples crown more flukes; the dethroned champion's defense race (it
  re-races the new one from scratch) is what catches them.
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
