#!/usr/bin/env bash
# Sends one finished run played outside the loop's generations (a marathon game on the cloud) to
# the loop: checkpoints go straight to the checkpoints release, the small files to loop/inbox/<name>/
# on trial-results, where the next generation takes them in (training data, bank, dashboard).
#   scripts/loop/send_run.sh NAME GENOME MINUTES MACHINE        (run from the repo root, after a play)
# Needs GH_TOKEN. The run's files are where the play action leaves them (mod/...).
set -euo pipefail
name=$1 genome=$2 minutes=$3 machine=$4
run=mod/build/run/clientGameTest
grep -aq "FINAL" mod/autopilot-test.log || { echo "the run didn't finish; nothing sent"; exit 0; }
scripts/loop/results.sh checkout results
inbox="results/loop/inbox/$name"
mkdir -p "$inbox/logs" "$inbox/checkpoints"
cp mod/autopilot-test.log "$inbox/"
cp "$run"/mc-autopilot/logs/*.jsonl "$inbox/logs/" 2>/dev/null || true
if gh release view checkpoints >/dev/null 2>&1 || gh release create checkpoints --prerelease --title "Checkpoint bank" \
     --notes "Saved worlds from the learning loop's runs, one per stage reached (Go-Explore starts)."; then
  for z in "$run"/checkpoints/*.zip; do
    [ -f "$z" ] || continue
    stage=$(basename "$z" .zip)
    asset="$stage-$name.zip"
    cp "$z" "$RUNNER_TEMP/$asset"
    if gh release upload checkpoints "$RUNNER_TEMP/$asset" --clobber; then
      # The json names the uploaded asset; the loop indexes it without uploading again.
      python3 - "$run/checkpoints/$stage.json" "$inbox/checkpoints/$stage.json" "$asset" <<'PY'
import json, sys
meta = json.load(open(sys.argv[1]))
meta["asset"] = sys.argv[3]
json.dump(meta, open(sys.argv[2], "w"))
PY
    fi
  done
fi
printf '{"genome": "%s", "minutes": %s, "machine": "%s", "commit": "%s"}\n' "$genome" "$minutes" "$machine" "${GITHUB_SHA:0:7}" > "$inbox/run.json"
scripts/loop/results.sh commit results "$machine run $name ($genome)"
