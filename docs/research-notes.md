# Research notes (limits and versions change often, so re-check)

## Versions (checked 2026-09-26)
- Minecraft Java: newest release 26.3 (already installed in the user's launcher). Needs Java 25
  (Mojang version manifest: javaVersion.majorVersion = 25). The launcher bundles its own Java.
- Fabric: 26.3 is a stable Fabric game version. Fabric API 0.161.0+26.3 on Modrinth.
- Baritone v1.20.0 (2026-09-23): "For Minecraft 26.3. Forge/Fabric/NeoForge supported."
  Assets include baritone-api-fabric-1.20.0.jar (API for mods) and
  baritone-standalone-fabric-1.20.0.jar. License LGPL-3.0.
  https://github.com/cabaletta/baritone/releases
  Other builds: v1.19.0 for 26.2, v1.18.0 for 26.1.

## Opus through Claude Code headless mode (no API key)
- `claude -p` runs one non-interactive request on the user's Claude plan.
  https://code.claude.com/docs/en/headless
- Useful flags (claude 2.1.283): --model opus, --tools "" (no tools), --json-schema '<schema>'
  (answer lands in `structured_output`), --output-format json, --no-session-persistence,
  --system-prompt (replaces the default prompt).
- Measured 2026-09-26: one tiny decision with tools off and a JSON schema took 6.8 s wall
  time (duration_api_ms 3392), model claude-opus-5-5, returned {"skill":"craft","arg":"oak_planks"}.
  Fine for a strategist every ~60 s or on events; too slow for split-second reflexes.

## Free AI brain options
- Groq: no card needed, OpenAI-compatible API. Free tier about 30 requests/min and about 1,000
  requests/day per chat model, plus a tokens-per-minute cap that usually bites first.
  Limits are per model and change often. Check https://console.groq.com/docs/rate-limits
- Gemini (Google AI Studio): free tier covers only Flash and Flash-Lite models, roughly 5-15
  requests/min and up to about 1,000/day. Live quota shows in AI Studio. Free-tier inputs may
  be used for training. https://ai.google.dev/gemini-api/docs/rate-limits
- OpenRouter ":free" models: 20 requests/min but only 50/day unless you've bought credits.
  Not worth it here.
- Jev (TypeSafe AI): a fast "System One" decision model, $0.042 per 1M input tokens. Signups
  were paused in late September 2026 and Vercel's free promo ended Sept 25, so it's not free.
  Could be added later as another tactician if the user gets access.

## Prior work to credit in the README (and to stay different from)
- Baritone (cabaletta): the pathfinding and mining engine the skills are built on.
- Fabric (FabricMC): the mod loader.
- Mineflayer (PrismarineJS): the usual way to write Minecraft bots (separate bot players).
- Mindcraft: multi-agent LLM framework on Mineflayer with many model backends.
- Voyager (NVIDIA et al., 2023): LLM agent in Minecraft with a skill library.
- rudrasingh500/jev_minecraft: Jev-driven Mineflayer bot aiming for the Ender Dragon.
- rmalde/minecraft-agent: a planner plus Jev controller in Minecraft.

What would be new here: the AI drives the user's own character in an unmodified vanilla
single-player world, toggled from an in-game panel, with Opus as strategist through the user's
Claude plan (no API key), running for $0, and a published comparison of brain setups by
furthest milestone reached.
