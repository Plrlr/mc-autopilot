# Start here (for you, not for Claude Code)

MC Autopilot lets Claude Opus 5.5 play your own character in a normal single-player
survival world and try to beat the game. Full details are in README.md.

## Playing
1. Open the Minecraft Launcher and pick the **fabric-loader-26.3** profile, then Play.
2. Create a **new survival world** for testing (the AI will dig, die and lose items).
3. In the world, press **K** and click **Autopilot: OFF** to turn it on.
4. Watch. The line in the top-left corner shows the brain, the goal and the current action.
5. To take over, press any movement key (W, A, S, D, space). Press K for the panel.

Chat commands (type T, then the command; they never go to the world):
`!status`, `!stop`, `!start`, `!brain auto|mock|groq|cerebras|gemini|opus`, `!goal <name>`, `!opus on|off`.

## Settings and keys
The first time Minecraft starts with the mod, it creates
`%APPDATA%\.minecraft\config\mc-autopilot.env`. Open it with Notepad to change:
- how many Opus calls per hour are allowed (these count toward your Claude plan's limits),
- the starting brain,
- optional free keys for the quick action decisions (you make these accounts yourself; one
  key is enough, more keys give a fallback when one hits its limit):
  - **Groq:** sign in at https://console.groq.com/keys, click Create API Key, paste it after `GROQ_API_KEY=`.
  - **Cerebras:** sign in at https://cloud.cerebras.ai, open API Keys, paste it after `CEREBRAS_API_KEY=`.
  - **Google AI Studio:** sign in at https://aistudio.google.com/apikey, create a key, paste it
    after `GEMINI_API_KEY=`.
  With `TACTICIAN=auto` (the default) the first one with a key is used. With no keys, the free
  rules decide, which works fine too.

That file is outside this project, so keys can never be uploaded by accident.
Don't paste keys into chats, screenshots, or GitHub. Restart Minecraft after editing it.

## Rebuilding the mod after changes
In PowerShell:
```
cd $HOME\OneDrive\Desktop\Python\mc-build-crew\mod
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
.\gradlew.bat build
copy build\libs\mc-autopilot-0.1.0.jar $env:APPDATA\.minecraft\mods\
```

## Working with Claude Code
Open PowerShell in this folder and run `claude`. For example:
```
The autopilot keeps failing at X. Here's the log line: ... fix it.
```
Logs are in `%APPDATA%\.minecraft\mc-autopilot\logs\`.
