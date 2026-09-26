# Start here (for you, not for Claude Code)

MC Autopilot lets Claude Opus 5.5 play your own character in a normal single-player
survival world, and try to beat the game.

## What you need
- Minecraft Java Edition 26.3 in the official launcher.
- A Claude plan with Claude Code (Opus runs through it, so no API key is needed).
- Optional free API keys for faster, cheaper decisions:
  - **Groq:** make a free account at console.groq.com and create an API key.
  - **Google AI Studio:** sign in at aistudio.google.com and create an API key.

Keep keys secret. Don't paste them into chats, screenshots, or GitHub.
Keys go in `%APPDATA%\.minecraft\config\mc-autopilot.env` (copy `mc-autopilot.env.example`
there). That file is outside this project, so it can never be uploaded by accident.

## Working with Claude Code
Open PowerShell in this folder and run `claude`. One phase per session:
```
Do Phase 1. Tell me exactly how to test it when you're done.
```
Test each phase before starting the next.

## Good to know
- Use a **new test world** at first. The AI will dig, die, and lose items.
- The toggle key (default K) turns the autopilot on and off. Pressing any movement key
  also takes control back instantly.
- Opus calls count toward your Claude plan's usage limits. The panel shows how many
  calls were used this hour.
- If something breaks, paste the error into Claude Code and say "fix this".
