# Setup details

Moved out of CLAUDE.md; CLAUDE.md links here.

## The user's machine
- Windows 11, PowerShell. Minecraft Java **26.3** (installed in the official launcher).
- Fabric Loader + Fabric API for 26.3 (Fabric API 0.161.0+26.3 was newest on 2026-09-26).
- Baritone **v1.20.0** (Fabric build, "For Minecraft 26.3", LGPL-3.0) for pathfinding and mining,
  used through its API from our mod. Installed separately by the user, never bundled.
- Our mod: Java 25, built with Gradle (Fabric Loom 1.17, via the Gradle wrapper; no mappings:
  26.x ships unobfuscated). Needs a JDK 25 to build: Temurin 25 JDK installed 2026-09-26 in
  `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`; set JAVA_HOME to it when running gradlew.
- `claude` CLI 2.1.283 (`%USERPROFILE%\.local\bin\claude.exe`). ClaudeCli finds claude.exe on PATH
  and starts it directly (cmd /c only for an npm .cmd shim). Java doesn't escape quotes in Windows
  arguments, so every argument goes through ClaudeCli.winQuote (MSVC rules).
- Node.js was installed for the old plan and isn't needed now.

## Runtime files (in the Minecraft folder, not the repo)
- `%APPDATA%\.minecraft\config\mc-autopilot.env`: settings and API keys, created on first start.
- `%APPDATA%\.minecraft\mc-autopilot\logs\`: run-DATE.jsonl (decisions, skill results with
  failure codes, deaths, milestones), usage-DATE.json (rate limiter counts).
- `%APPDATA%\.minecraft\mc-autopilot\progress-<world>.json`: furthest milestone per world.
- `%APPDATA%\.minecraft\mc-autopilot\lessons.json`: failure codes per action across all runs.
- `%APPDATA%\.minecraft\mc-autopilot\claude-cwd\`: empty working directory for claude -p.

## Cloud trials (GitHub Actions)
- `.github/workflows/trials.yml`: a plan job turns inputs into runs (seeds x scenario + extra
  runs); each run gets its own ubuntu machine: Xvfb + Mesa (the game renders through Vulkan on
  lavapipe; OpenGL finds no GLX visual), Gradle cache from setup-java (Gradle + Loom's Minecraft
  downloads, ~460 MB), a watchdog for a game that never opens its window.
- Public repo: Actions minutes are free; a job may run 6 hours; ~20 jobs in parallel.
- Cloud Claude sessions can't reach maven.fabricmc.net or Mojang's hosts, so they can't build
  locally; the unit-tests job compiles (including the in-game test code) in about a minute.
