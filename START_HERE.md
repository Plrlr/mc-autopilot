# Start here (for you, not for Claude Code)

## 1. Put the folder somewhere simple
Unzip so you have `C:\Users\<you>\mc-build-crew` with CLAUDE.md inside it.

## 2. Get free API keys (you do this yourself)
- **Groq (main brain):** make a free account at console.groq.com and create an API key.
- **Google AI Studio (backup brain):** sign in at aistudio.google.com and create an API key.
- **Jev (optional):** only if you get access later, through Vercel AI Gateway or TypeSafe.

Keep keys secret. Don't paste them into chats, screenshots, or GitHub.
After Phase 0 creates your `.env` file, open it in Notepad and paste each key after its `=`.

## 3. Start Claude Code
Open PowerShell and run:
```
cd $HOME\mc-build-crew
claude
```

## 4. Paste this first prompt
```
Read CLAUDE.md. Do Phase 0 and Phase 1 only.
Check what's already installed and give me the exact commands for anything I need to install myself.
Before downloading the server, confirm the newest Minecraft version Mineflayer officially supports.
Stop when the server is running on 127.0.0.1 and tell me how to start it next time.
```

## 5. After that, one phase per session
Claude Code reads CLAUDE.md every time, so you only need short prompts:
```
Do Phase 2. Tell me exactly how to test it when you're done.
```
Then Phase 3, Phase 4, and so on. Test each phase before starting the next.

## Good to know
- Claude Code will ask you to accept Minecraft's EULA yourself. Read it, then say yes.
- In the game chat or bot console, `!stop` makes every bot stand still.
- Before your first `git push`, run `git status` and make sure `.env` and `server/` are NOT listed.
- If something breaks, paste the error into Claude Code and say "fix this".
