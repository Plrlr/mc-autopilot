# One trial on this PC (real GPU, real speed, and Opus through your own `claude` login), saved in the
# same layout as the cloud batches so scripts/summarize_batch reads both.
#
#   .\scripts\local-trial.ps1 -Seed a -Minutes 20                 rules brain, goals by rules
#   .\scripts\local-trial.ps1 -Seed a -Minutes 20 -Opus           goals by Opus (claude -p, plan usage)
#   .\scripts\local-trial.ps1 -Seed a -Scenario cast -Minutes 10  staged portal-casting test
#   .\scripts\local-trial.ps1 -Seed a -Task "craft furnace:1" -Give "cobblestone 8,crafting_table" -Minutes 3
#   .\scripts\local-trial.ps1 -Batch local-opus -Seed b -Opus     put several runs in one batch folder
#
# Output: .trials\<batch>\trial-<name>\ (trial.log, autopilot-test.log, jsonl log, lessons, screenshots),
# then the batch summary is printed (needs Python 3 on PATH; otherwise read autopilot-test.log).
# Needs JAVA_HOME pointing at a JDK 25 (see docs/setup.md).
param(
	[string]$Seed = "a",
	[int]$Minutes = 20,
	[string]$Scenario = "natural",
	[string]$Brain = "mock",
	[switch]$Opus,
	[string]$Task = "",
	[string]$Give = "",
	[string]$Batch = ""
)
$ErrorActionPreference = "Stop"
$root = (git rev-parse --show-toplevel).Trim()
if (-not $Batch) { $Batch = "local-" + (Get-Date -Format "yyyyMMdd-HHmmss") }
$name = "$Scenario-$Seed"
if ($Opus) { $name += "-opus" }
if ($Brain -ne "mock") { $name += "-$Brain" }
if ($Task) { $name += "-task" }
$out = Join-Path $root ".trials\$Batch\trial-$name"
New-Item -ItemType Directory -Force $out | Out-Null
$commit = (git rev-parse --short HEAD).Trim()
Write-Host "[local-trial] $name on $commit -> $out"

Push-Location (Join-Path $root "mod")
try {
	$gradleArgs = @("runClientGameTest", "--console=plain", "-PtestMinutes=$Minutes", "-PtestSeed=$Seed",
		"-PtestScenario=$Scenario", "-PtestBrain=$Brain", "-PtestOpus=$($Opus.IsPresent.ToString().ToLower())",
		"-PtestTask=$Task", "-PtestGive=$Give")
	# The game exits with Baritone's known shutdown-watchdog crash after saving; that's expected.
	& .\gradlew.bat @gradleArgs 2>&1 | Tee-Object -FilePath (Join-Path $out "trial.log")
} finally {
	Pop-Location
}

$run = Join-Path $root "mod\build\run\clientGameTest"
Select-String -Path (Join-Path $out "trial.log") -Pattern "autopilot-test" | ForEach-Object { $_.Line } |
	Set-Content (Join-Path $out "autopilot-test.log")
# The run folder is wiped at the next start: copy what the summary reads.
foreach ($p in @("mc-autopilot\logs", "mc-autopilot\lessons.json", "screenshots", "crash-reports")) {
	$src = Join-Path $run $p
	if (Test-Path $src) {
		$dst = Join-Path $out ("build\run\clientGameTest\" + $p)
		New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null
		Copy-Item -Recurse -Force $src $dst
	}
}
Select-String -Path (Join-Path $out "autopilot-test.log") -Pattern "FINAL|TASK|LESSON" | ForEach-Object { $_.Line }
if (Get-Command python -ErrorAction SilentlyContinue) {
	python (Join-Path $root "scripts\summarize_batch") (Split-Path $out) --run $Batch --commit $commit
}
