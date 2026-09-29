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
