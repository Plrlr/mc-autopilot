# The laptop's part of the learning loop: plays the loop's current champion (with its learned model)
# in a visible Minecraft window, run after run, and sends each run back to the cloud loop, where its
# decisions become training data for the learned brain and its score shows on the dashboard.
#
#   .\scripts\laptop-loop.ps1                    run forever (Ctrl+C to stop; a finished run is kept)
#   .\scripts\laptop-loop.ps1 -Runs 3 -Minutes 20
#   .\scripts\laptop-loop.ps1 -PerfMods ""       without the speed-up mods
#   .\scripts\laptop-loop.ps1 -NoPush            play and keep the logs here only
#
# It waits instead of starting a run while the Minecraft Launcher is open (you're playing) or while
# less than 3 GB of memory is free. Screenshots stay on this PC in .trials\laptop\ (for videos).
# Needs JAVA_HOME pointing at a JDK 25 (docs/setup.md) and Python 3 on PATH.
param(
	[int]$Runs = 0,
	[int]$Minutes = 20,
	[string]$PerfMods = "lithium,ferritecore,sodium",
	[switch]$NoPush
)
$ErrorActionPreference = "Stop"
$root = (git rev-parse --show-toplevel).Trim()
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot" }
$results = Join-Path $root ".trials\results"
$runDir = Join-Path $root "mod\build\run\clientGameTest"

function Wait-Until-Free {
	while ($true) {
		$launcher = Get-Process -Name "MinecraftLauncher" -ErrorAction SilentlyContinue
		$freeGb = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 1)
		if (-not $launcher -and $freeGb -ge 3) { return }
		$why = if ($launcher) { "the Minecraft Launcher is open" } else { "only $freeGb GB of memory free" }
		Write-Host "[laptop-loop] waiting: $why (checking again in 2 minutes)"
		Start-Sleep -Seconds 120
	}
}

function Git-Quiet([string[]]$a) {
	# Native stderr lines are error records in Windows PowerShell 5.1: run git with Continue.
	$old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
	try { & git @a 2>&1 | Out-Null; return $LASTEXITCODE } finally { $ErrorActionPreference = $old }
}

function Sync-Results {
	Git-Quiet @("-C", $root, "fetch", "-q", "origin", "trial-results") | Out-Null
	if (-not (Test-Path (Join-Path $results ".git"))) {
		Git-Quiet @("-C", $root, "worktree", "prune") | Out-Null
		Git-Quiet @("-C", $root, "worktree", "add", "-q", "--detach", $results, "origin/trial-results") | Out-Null
	} else {
		# A run that couldn't be pushed is a local commit (or files in loop/inbox): keep it on top
		# of the newest results instead of resetting it away; it goes with the next push.
		$ahead = [int](git -C $results rev-list --count origin/trial-results..HEAD)
		if ($ahead -gt 0) {
			if ((Git-Quiet @("-C", $results, "rebase", "-q", "origin/trial-results")) -ne 0) { Git-Quiet @("-C", $results, "rebase", "--abort") | Out-Null }
		} else {
			Git-Quiet @("-C", $results, "checkout", "-q", "--detach", "origin/trial-results") | Out-Null
		}
	}
	New-Item -ItemType Directory -Force (Join-Path $results "loop") | Out-Null
}

function Sync-Code {
	# Play the newest code when this folder is a clean checkout of main; otherwise play what's here.
	$branch = (git -C $root rev-parse --abbrev-ref HEAD).Trim()
	$dirty = (git -C $root status --porcelain --untracked-files=no)
	if ($branch -eq "main" -and -not $dirty) {
		Git-Quiet @("-C", $root, "fetch", "-q", "origin", "main") | Out-Null
		Git-Quiet @("-C", $root, "merge", "-q", "--ff-only", "origin/main") | Out-Null
	} else {
		Write-Host "[laptop-loop] note: playing the code in this folder ($branch$(if ($dirty) { ', with local changes' }))"
	}
}

function Push-Run([string]$name) {
	for ($i = 1; $i -le 5; $i++) {
		Git-Quiet @("-C", $results, "add", "loop/inbox") | Out-Null
		Git-Quiet @("-C", $results, "commit", "-q", "-m", "Laptop run $name") | Out-Null
		if ((Git-Quiet @("-C", $results, "push", "-q", "origin", "HEAD:refs/heads/trial-results")) -eq 0) { return $true }
		Start-Sleep -Seconds (5 * $i)
		Git-Quiet @("-C", $results, "fetch", "-q", "origin", "trial-results") | Out-Null
		if ((Git-Quiet @("-C", $results, "rebase", "-q", "origin/trial-results")) -ne 0) { Git-Quiet @("-C", $results, "rebase", "--abort") | Out-Null }
	}
	return $false
}

$done = 0
while ($Runs -eq 0 -or $done -lt $Runs) {
	Wait-Until-Free
	Sync-Code
	Sync-Results
	$params = Join-Path $root ".trials\champion-params.json"
	$genome = (python (Join-Path $root "scripts\loop\loop.py") export --state (Join-Path $results "loop") --out $params).Trim()
	$learned = Join-Path $results "loop\learned.json"
	if (-not (Test-Path $learned)) { $learned = "" }
	$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
	$seed = "laptop-$stamp"
	$name = "laptop-$stamp-$genome"
	$out = Join-Path $root ".trials\laptop\$name"
	New-Item -ItemType Directory -Force $out | Out-Null
	$commit = (git -C $root rev-parse --short HEAD).Trim()
	Write-Host "[laptop-loop] run $($done + 1): champion $genome on seed $seed, code $commit, $Minutes game minutes"

	Push-Location (Join-Path $root "mod")
	$log = Join-Path $out "trial.log"
	$writer = New-Object System.IO.StreamWriter($log, $false, (New-Object System.Text.UTF8Encoding($false)))
	$ErrorActionPreference = "Continue"
	try {
		$gradleArgs = @("runClientGameTest", "--console=plain", "-PtestMinutes=$Minutes", "-PtestSeed=$seed",
			"-PtestScenario=natural", "-PtestParams=$params", "-PtestLearned=$learned", "-PtestLean=false", "-PperfMods=$PerfMods")
		# The game exits with Baritone's known shutdown-watchdog crash after saving; that's expected.
		& .\gradlew.bat @gradleArgs 2>&1 | ForEach-Object { $line = "$_"; $writer.WriteLine($line); $writer.Flush()
			if ($line -match "\[autopilot-test\] (FINAL|SPEED|natural \d+s)") { Write-Host $line } }
	} finally {
		$writer.Close()
		$ErrorActionPreference = "Stop"
		Pop-Location
	}
	Select-String -Path $log -Pattern "autopilot-test" | ForEach-Object { $_.Line } | Set-Content -Encoding utf8 (Join-Path $out "autopilot-test.log")
	foreach ($p in @("mc-autopilot\logs", "screenshots")) {
		$src = Join-Path $runDir $p
		if (Test-Path $src) { Copy-Item -Recurse -Force $src (Join-Path $out (Split-Path $p -Leaf)) }
	}
	$done++
	if (-not (Select-String -Path (Join-Path $out "autopilot-test.log") -Pattern "FINAL" -Quiet)) {
		Write-Host "[laptop-loop] the game didn't finish the run (see $log); not sending it"
		continue
	}
	if ($NoPush) { continue }
	# The cloud loop picks it up from loop/inbox/<name>/ at its next generation.
	$inbox = Join-Path $results "loop\inbox\$name"
	New-Item -ItemType Directory -Force (Join-Path $inbox "logs") | Out-Null
	Copy-Item (Join-Path $out "autopilot-test.log") $inbox
	Copy-Item (Join-Path $out "logs\*.jsonl") (Join-Path $inbox "logs") -ErrorAction SilentlyContinue
	@{ genome = $genome; minutes = $Minutes; seed = $seed; machine = "laptop"; commit = $commit } | ConvertTo-Json |
		Set-Content -Encoding ascii (Join-Path $inbox "run.json")
	if (Push-Run $name) { Write-Host "[laptop-loop] sent $name to the loop" }
	else { Write-Host "[laptop-loop] couldn't push $name; it stays in $inbox and goes with the next run" }
}
