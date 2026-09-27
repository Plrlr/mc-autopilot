# Runs local trials back to back without anyone watching, and leaves one summary per run for the
# reviewer session to read (it shares this laptop's disk). Rules brain only, never Opus.
#
#   .\scripts\trial-loop.ps1                          blaze and nether scenarios in turn, 12 runs
#   .\scripts\trial-loop.ps1 -Runs 6 -Plan "blaze:5"  only the blaze scenario, 5 game minutes each
#
# Output: .trials\loop-<start>\<n>-<scenario>\ (logs, as local-trial.ps1 saves them) with a
# summary.md in each, and .trials\loop-<start>\INDEX.md, one line per run (commit, scenario,
# FINAL line). Stop it any time with Ctrl+C or by ending the process; finished runs stay.
#
# W5 (review, whole-repo check): if the same death cause repeats in two runs in a row, that's
# likely a bug, not bad luck (the fortress/blaze hurt-bypass bug burned every blaze run the same
# way before it was fixed) - stop instead of burning the rest of the loop on it unwatched.
param(
	[int]$Runs = 12,
	[string]$Plan = "blaze:5,nether:10",
	[string]$Seed = "a"
)
$root = (git rev-parse --show-toplevel).Trim()
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$loop = Join-Path $root ".trials\loop-$stamp"
New-Item -ItemType Directory -Force $loop | Out-Null
$index = Join-Path $loop "INDEX.md"
"# Trial loop $stamp (seed $Seed, plan $Plan)`n" | Set-Content -Encoding utf8 $index
$steps = $Plan.Split(",")
$lastDeathCause = $null
for ($n = 1; $n -le $Runs; $n++) {
	$step = $steps[($n - 1) % $steps.Count].Split(":")
	$scenario = $step[0]
	$minutes = [int]$step[1]
	$batch = "loop-$stamp\$n-$scenario"
	$commit = (git rev-parse --short HEAD).Trim()
	$started = Get-Date -Format "HH:mm"
	$summary = & (Join-Path $root "scripts\local-trial.ps1") -Batch $batch -Seed $Seed -Scenario $scenario -Minutes $minutes 2>&1 | Out-String
	$dir = Join-Path $root ".trials\$batch"
	$summary | Set-Content -Encoding utf8 (Join-Path $dir "summary.md")
	$final = Get-ChildItem -Recurse -Filter autopilot-test.log $dir -ErrorAction SilentlyContinue |
		Select-String -Pattern "FINAL" | Select-Object -Last 1
	$line = if ($final) { ($final.Line -replace '^.*FINAL ', '') } else { "no FINAL line (crash or stopped)" }
	"- run $n $scenario ${minutes}m at $commit, started $started`: $line" | Add-Content -Encoding utf8 $index
	$deaths = Get-ChildItem -Recurse -Filter "run-*.jsonl" $dir -ErrorAction SilentlyContinue |
		Select-String -Pattern '"event":"death"' |
		ForEach-Object { if ($_.Line -match '"detail":"([^"]*)"') { $Matches[1] } }
	$thisDeathCause = $deaths | Select-Object -Last 1
	if ($thisDeathCause -and $thisDeathCause -eq $lastDeathCause) {
		"`nStopped after run $n`: same death cause ('$thisDeathCause') twice in a row - likely a bug, not bad luck." |
			Add-Content -Encoding utf8 $index
		Write-Host "[trial-loop] stopping: '$thisDeathCause' twice in a row (run $($n - 1) and $n)"
		& (Join-Path $root "mod\gradlew.bat") -p (Join-Path $root "mod") --stop 2>$null | Out-Null
		return
	}
	$lastDeathCause = $thisDeathCause
	# Free the Gradle daemon's memory between runs (the laptop has 7.7 GB).
	& (Join-Path $root "mod\gradlew.bat") -p (Join-Path $root "mod") --stop 2>$null | Out-Null
}
"`nDone: $Runs runs." | Add-Content -Encoding utf8 $index
