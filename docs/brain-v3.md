# Brain v3: one brain that chooses, and a loop that can tell

2026-09-29 review (Claude), after gens 50-60 crowned nothing.

## What decides today

1. **Rules** (`plan/Planner.java`, ~1050 lines of if/else) turn the goal ladder into a list of
   options. In a sampled run: 1 option in 11% of decisions, 2 in 43%, 3 in 34%. The extra options
   are shallow ("pickup", "explore any"). The real choices (route, dig or explore, fight or flee)
   are in the order the rules write them.
2. **Reflexes** (`Autopilot.java`) override for danger: melee, creeper, fall, lava, stuck.
3. **Brain** (`brains/Brain.java`) takes the rules' first option unless the learned genes are on.
   `learned.weight` and `learned.explore` are 0 in the champion, so **the brain is the rules**.
4. **Learned re-ranker** (`brains/Learned.java`, trees on 40 features): advantage R2 0.008 on 82k
   rows, under its 0.1 gate, so it never plays. It can't learn which choice is better: the data
   holds only what the rules chose (data runs were 0), so there is nothing to compare.
5. **Brain v2 pieces, built but never adopted**: `brain.strategist` picks late-game routes by
   expected time from measured skill stats; `brain.skill_stats` moves options that keep failing
   behind the ones that work. Both lost to noise in the whole-game race (g40 +1.33 over 16 worlds,
   cut off by max_pairs; g39 flat).

So the bot improves only when a gene flips or someone writes code, and the loop could not tell a
good flip from noise (paired sd 3-4.7 per world).

## Target

**The brain picks every non-emergency choice by expected cost, learned from every game.**

- **Options**: for each step the planner lists every viable way (the route specs in
  `skills/SkillSpecs.java`: what each skill gives or makes), not one. E.g. stone: mine seen stone,
  stair down (gather.stair_for_stone), explore. Lava: explore, dig deep, lava_scout.
- **Cost** of an option = expected seconds to one success: (mean try time + death risk x
  `brain.death_s`) / success rate, per context (dimension, night, underground). SkillStats already
  estimates this from the loop's data plus this game's tries.
- **Choice**: the cheapest option, the rules' order as the prior while data is thin (a spec's
  prior success and seconds). Emergencies stay with the reflexes and the danger model.
- **Exploration**: sample each option's success rate from its uncertainty (Thompson sampling on
  SkillStats' bounds) instead of a fixed 15%. Well-known options are exploited, and uncertain ones
  get tried in proportion to how likely they are to be better. Every game then adds the data the
  next choice needs. This is the self-learning, and it needs no race: stats are data, not decisions.
- **The loop** keeps racing code and genes, judged by the metric each targets (`metrics.py`), with
  the whole-game score as a safety check.

## Order of work

1. Done: targeted-metric races (`_metric`), stage focus (`_stages`), longer races for broad
   changes (`_max_pairs`, `_priority`), the failure table (`scripts/loop/failures.py`).
2. Done: the brain (strategist + skill-stat demotion) races again with 40 worlds, and 1 data run per
   generation feeds the learned model counterfactuals.
3. Next: generalize the strategist from late-game routes to every goal step (the planner emits
   alternatives from SkillSpecs; `Brain.decide` picks by cost), behind `brain.utility`, judged
   by `deaths` and milestone times.
4. Then: Thompson exploration in `Brain` (replaces `learned.explore`), and retire the advantage
   trees if R2 stays under the gate once exploration data exists.

## 2026-09-29 later: the advantage re-ranker is retired

Its held-out choice R2 was 0.008 on 82k rows (and -0.018 when retrained on gens 50-58 alone), under
its 0.1 gate, so it never played. `train.py` now trains only the danger model and the skill stats
(the strategist's data): 17 s instead of 30 s on 9 generations. `--advantage` revives it. Data runs
are 0 again: Thompson sampling in the strategist (brain.thompson) is the exploration now.

## 2026-09-30: the neural brain

The advantage re-ranker is now a network (`scripts/loop/mlp.py`, `brains/MlpModel.java`): 40
features -> 256 -> 256 -> 128 -> one head per action kind (~117k parameters, 63 heads on the 1,432
games so far), retrained on every game each generation (~6 min with the danger model). First
fit: held-out R2 0.19 for progress, choice part 0.006, the trees' level. Size doesn't fix that:
the data held only the rules' habits. The 2 data runs per generation (15% deliberate tries) are back
on to give it choices to compare. It plays (learned.* genes race) only once its choice R2 passes
model_gate, measured against the same tree baseline as before.

### The gate bug (found 2026-09-30)

The learned brain was benched by its own grading. The gate asked for a held-out "choice R2" above
0.1, but R2 is capped by how random one decision's outcome is, not by how good the choices are. On
synthetic games where the net picked the better action 92% of the time, its choice R2 was 0.02:
no model could pass, the trees included. Now the gate is the 5% lower bound, over resampled whole
games, of how much the choice part improves on the state-only tree baseline (`gain_lower_bound`);
above zero, the learned.* genes race and the game decides. On the 1,432 loop games so far it is
-0.004 (not yet above zero): only 631 of ~146k non-urgent decisions differed from the rules' pick,
and training rows kept only the pick, not the options. Now rows keep the options ("o"), and the
data runs try other options 30% of the time.

### The gate, reviewed (2026-09-30, Codex's review of the merge to main)

Three holes, all fixed before the gate could open: (1) the four genomes of a generation play the
same seeds, and those games were split and resampled as separate files, so four copies of one
world counted as four worlds; they now split and resample by world (`game_group`). (2) The choice
part was the pick's head minus `_v`, so heads sharing an offset over `_v` (a state error) passed for
choice skill; it is now the pick's head minus the mean head of the options it was chosen from, on
rows that logged their options ("o"). (3) A neural model with no bound yet fell back to the old R2
bar; now no bound is a closed gate. A second check sits next to it: on held-out decisions where the
bot deviated from the rules, does the sign of the net's Q(chosen) - Q(rules' pick) match what
happened relative to V(x) (`sign_check`: agreement, 0.5 = chance, with a 5% bound over worlds)?
When the gate opens, loop.py queues the champion with `learned.weight` 1 as a priority race.
