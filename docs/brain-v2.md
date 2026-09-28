# Brain v2: fast reflexes, a strategist that reasons about routes, and learning from every skill run

2026-09-28. Built by Claude while Codex builds the 40 skills (docs/skills-40.md).

## Why a new brain

The loop's data (generations 41-46, 120 games) shows the old one-brain setup's limits:

- **The learned models never play.** The champion runs `learned.weight 0` and `safety.hazard 0`:
  every choice is the hand-written rules'. The progress model is gated off (advantage R² 0.014
  against a gate of 0.1) because the rules choose the same thing in the same state, so there is
  nothing to compare. The danger model is good (held-out AUC 0.86) but its gene was never raced.
- **What the bot learns from is thin.** Skill results (`skill_end`: ok, fail code, seconds) are in
  every game log but are dropped from the training data. The loop knows "the bot died 45 s after
  choosing retreat", not "build_portal failed PLACE_FAILED 25 times out of 26".
- **Routes are fixed switches.** Cast vs. diamond portal, barter vs. endermen: each is a gene the
  race flips for the whole game, whatever the world offers. A player picks by what's in front of
  them: a ruined portal in sight, a diamond pickaxe already made, piglins nearby.
- **Reflex and plan fight.** `smelt ↔ retreat` flips every 4-6 s for 100+ s: the planner walks back
  to the furnace, the reflex runs, and nothing escalates.

## The design: three speeds, one memory

```
             every tick (µs)                each decision (µs)              on change (≤ 2 ms)
  ┌───────────────────────────┐  ┌──────────────────────────────┐  ┌───────────────────────────────┐
  │ REFLEXES                  │  │ TACTICIAN                    │  │ STRATEGIST                    │
  │ lava, air, fall, fire,    │  │ planner's options, ranked:   │  │ routes to the goal over       │
  │ creeper, melee            │  │ rules order                  │  │ SkillSpecs + TechTree,        │
  │ + escalation (run twice   │  │ − skill stats (fails here)   │  │ costed by skill stats:        │
  │   from the same mob →     │  │ + learned advantage (gated)  │  │ E[time] / P(success) + risk   │
  │   fight/box/pillar)       │  │ → safety critic swap         │  │ + readiness gates before      │
  │ → safety critic picks     │  │                              │  │   one-way doors (Nether, End) │
  └───────────────────────────┘  └──────────────────────────────┘  └───────────────────────────────┘
            ▲                                   ▲                                 ▲
            └──────────── SKILL STATS: success, seconds, deaths per key × context ┘
                          prior (SkillSpecs) ← loop (every game) ← this game (every skill end)
```

**Reflexes** (Autopilot.reflexes, every tick) stay the rules' code: they must be instant and they
are where the bot dies, so they're the one place a learned model only *chooses among safe
alternatives* (the safety critic), never invents. New: **escalation** (gene `reflex.escalate`):
fleeing the same threat twice within 30 s means fleeing doesn't work here, so the third time
the reflex fights, boxes in or pillars instead (whichever the safety critic rates safest).
That ends the smelt↔retreat thrash, and it goes after the #1 death pattern (101 of 281 deaths
came right after `retreat`).

**Tactician** (Brain.decide, each decision): the planner's options in the rules' order. New:
**skill stats** (gene `brain.skill_stats`): an option whose key has clearly failed in this
context (≥ 8 tries, success < 15% at 90% confidence, from the loop's stats plus this game's) drops
behind the options that work, instead of being tried three times before a one-minute pause. The
learned advantage re-rank and the safety critic stay as they are.

**Strategist** (plan/Strategist, gene `brain.strategist`): when the goal changes, a skill fails,
or every 20 s, it plans the rest of the route. For each thing the goal needs (items from
Goal.needs, facts like `in_nether`, `frame_known`, `dragon_dead`), it looks up every way to get
it: the route specs (SkillSpecs: casting, mining obsidian, a ruined portal; bartering, the boat
trap, warped-forest endermen; bed bombs or melee), and plain crafting, smelting and mining
(TechTree). It costs each route by
`E[seconds] / P(success) + P(death) × death cost`, from the skill stats, recursively over what
the route itself needs, and takes the cheapest. That's classical AND-OR planning (like GOAP/HTN
in game AI, and Plan4MC's skill graph for Minecraft) with costs that are *learned*, so routes
that fail stop getting picked, and a route that starts working (a better cast_portal) starts
getting picked, with no gene flip. The chosen route is logged as one readable line ("portal via
ruined_portal 4.2 min p 0.31 > enter_portal > fortress_scout > blaze_farm > ..."), shown on
the HUD, and is what the loop's code step reads when it goes looking for what to fix.

**Readiness gates** (part of the strategist, gene `plan.readiness`): before a one-way door
(entering the Nether, the End), check the kit a player would bring: health, food, blocks, the
tools for the next stage (flint and steel back, a bow or beds for the End). Missing items become
steps. Gate thresholds are genes, so the race tunes them.

## Learning, from fast to slow

| What | Learns from | How fast | Where |
|---|---|---|---|
| Skill stats, this game | each skill end | instantly | brains/SkillStats (in memory) |
| Skill stats, all games | skill_end rows of every run | every generation | train.py → learned.json "skills" |
| Danger model | deaths within 45 s of each choice | every generation | train.py → learned.json "hazard" |
| Progress advantage | progress over 2 min after each choice | every generation, gated | train.py → "keys" |
| Genes | paired-seed races | 2+ generations | loop.py |
| Code | failures, traces, earlier attempts | on plateaus | evolve.py (Claude) |
| New skills | this doc and docs/skills-40.md | by hand | Codex PRs |

Skill stats are Beta-smoothed success counts, median seconds and deaths per key, per context
(dimension; night; underground), with the SkillSpecs prior as the pseudo-count so a new skill
starts where its author guessed and moves as data comes in. They're small tables: loading and
lookups cost nothing, and they work from a handful of tries, which is why they learn faster than
any model of the whole state.

## What stays

No language model plays (the in-game LLM brains were removed on purpose, 2026-09-28): the brain
decides in microseconds on the game thread, costs $0, and plays fair. The "reasoning" is the
strategist's search over routes with learned costs, which is explicit, testable, and fast. The
LLM's part is the offline loop: Claude's code step (evolve.py) and Codex's skills.

## Build order (each behind its gene, each raced)

1. Contract: `SkillSpec`, `SkillSpecs`, `Facts` (done: the base for Codex's skills).
2. Data: `skill_end` rows kept in the loop's compact data (`{"k":"s",...}`); train.py writes
   skill stats into learned.json.
3. `brains/SkillStats`: loads the stats, adds this game's results as they happen.
4. `plan/Strategist`: route costing and choice; the planner's main step asks it (gene
   `brain.strategist`); readiness gates (gene `plan.readiness`).
5. Escalation reflex (gene `reflex.escalate`) and skill-stat demotion (gene `brain.skill_stats`).
6. The loop: race `safety.hazard` first (the strongest model, never raced); the dashboard shows
   skill stats per key so a broken skill is visible at a glance.
