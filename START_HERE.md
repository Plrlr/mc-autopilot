# Start here (for you, not for Claude Code)

MC Autopilot is a self-learning bot that plays your own character in a normal single-player
survival world and tries to beat the game. Full details are in README.md.

## Playing
1. Open the Minecraft Launcher and pick the **fabric-loader-26.3** profile, then Play.
2. Create a **new survival world** for testing (the AI will dig, die and lose items).
3. In the world, press **K** and click **Autopilot: OFF** to turn it on.
4. Watch. The line in the top-left corner shows the brain, the goal and the current action.
5. To take over, press any movement key (W, A, S, D, space). Press K for the panel.

Chat commands (type T, then the command; they never go to the world):
`!status`, `!stop`, `!start`, `!goal <name>`.

## Settings
There are none to fill in: no API keys, no accounts. The bot learns on GitHub (the learning loop);
its current best genes and model can be copied into `%APPDATA%\.minecraft\mc-autopilot\` as
`params.json` and `learned.json`.

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
