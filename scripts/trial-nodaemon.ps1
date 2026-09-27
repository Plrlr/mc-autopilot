# local-trial.ps1 but gradlew runs with --no-daemon, so a concurrent session's
# `gradlew --stop` (trial-loop.ps1 does this between runs) can't kill our game.
param(
	[string]$Seed = "a",
	[int]$Minutes = 10,
	[string]$Scenario = "nether"
)
$ErrorActionPreference = "Stop"
$root = (git rev-parse --show-toplevel).Trim()
$batch = "nether-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$commit = (git rev-parse --short HEAD).Trim()
$out = Join-Path $root ".trials\$batch\trial-$Scenario-$Seed"
New-Item -ItemType Directory -Force $out | Out-Null
Write-Host "[no-daemon-trial] $Scenario-$Seed on $commit -> $out"

$run = Join-Path $root "mod\build\run\clientGameTest"
foreach ($p in @("mc-autopilot\progress-*.json", "mc-autopilot\lessons.json")) {
	Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $run $p)
}

Push-Location (Join-Path $root "mod")
$log = Join-Path $out "trial.log"
$writer = New-Object System.IO.StreamWriter($log, $false, (New-Object System.Text.UTF8Encoding($false)))
$ErrorActionPreference = "Continue"
try {
	$gradleArgs = @("runClientGameTest", "--console=plain", "--no-daemon",
		"-PtestMinutes=$Minutes", "-PtestSeed=$Seed", "-PtestScenario=$Scenario",
		"-PtestBrain=mock", "-PtestOpus=false", "-PtestTask=", "-PtestGive=")
	& .\gradlew.bat @gradleArgs 2>&1 | ForEach-Object { $line = "$_"; $writer.WriteLine($line); $writer.Flush(); $line }
} finally {
	$writer.Close()
	$ErrorActionPreference = "Stop"
	Pop-Location
}

Select-String -Path $log -Pattern "autopilot-test" | ForEach-Object { $_.Line } |
	Set-Content (Join-Path $out "autopilot-test.log")
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
	python (Join-Path $root "scripts\summarize_batch") (Split-Path $out) --run $batch --commit $commit --minutes $Minutes
}
