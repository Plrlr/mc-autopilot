# Research notes (gathered September 26, 2026; limits change often, so re-check)

## Free AI brain options
- Groq: no card needed, OpenAI-compatible API. Free tier about 30 requests/min and about 1,000
  requests/day per chat model, plus a tokens-per-minute cap that usually bites first.
  Limits are per model and change often. Check https://console.groq.com/docs/rate-limits
- Gemini (Google AI Studio): free tier covers only Flash and Flash-Lite models, roughly 5-15
  requests/min and up to about 1,000/day. Live quota shows in AI Studio. Free-tier inputs may
  be used for training. https://ai.google.dev/gemini-api/docs/rate-limits
- OpenRouter ":free" models: 20 requests/min but only 50/day unless you've bought credits.
  Not worth it for this project.

## Jev (TypeSafe AI)
- A "System One" model: state in, typed decisions out (choice, score, noul). Text/JSON only.
  $0.042 per 1M input tokens, output free. Choice supports up to 255 options.
- Direct signups opened Sept 20, 2026 with $5 credit, then paused Sept 22 under demand.
  Still paused as of Sept 24-25. Existing accounts keep working.
- Vercel AI Gateway serves Jev as typesafe-ai/jev. Its free Jev promo ended Sept 25, 2026.
  Vercel's gateway free tier gives $5 credit every 30 days (a subset of models, lower rate
  limits; buying credits ends the monthly freebie). Confirm Jev is covered before relying on it.
- Avoid unofficial "instant Jev key" resellers (one charges 10x the official price).
- API shape: POST https://api.typesafe.ai/v1/systemone, Bearer auth, body {model, state, questions}.

## Claude Code (for the foreman)
- Headless mode: `claude -p "<prompt>"` runs non-interactively; `--output-format json` for parsing.
  https://code.claude.com/docs/en/headless
- CLAUDE.md in the working directory is loaded every session. https://code.claude.com/docs/en/memory

## Prior work to credit in the README (and to stay different from)
- Mineflayer (PrismarineJS): the bot library everything here uses.
- mineflayer-builder (PrismarineJS): prints schematics in survival.
- Mindcraft: multi-agent LLM framework on Mineflayer with many model backends.
- Voyager (NVIDIA et al., 2023): LLM agent in Minecraft with a skill library.
- JesseRWeigel/minecraft-agent-swarm: five role-based bots on local LLMs or OpenAI-compatible APIs.
- rudrasingh500/jev_minecraft: Jev-driven Mineflayer bot aiming for the Ender Dragon.
- rmalde/minecraft-agent: a planner plus Jev controller in Minecraft.
- mansicer/jev-plays: Jev plus an LLM planner in Craftax, with a brain comparison.

What would be new here: a prompt-to-blueprint Opus foreman directing a small crew that builds in
survival, running for $0 on a weak laptop, with a published comparison of rule, free-LLM, and
Jev brains on the same builds.
