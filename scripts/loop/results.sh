#!/usr/bin/env bash
# The trial-results branch as a working folder, for the loop's jobs (and the laptop).
#   scripts/loop/results.sh checkout DIR      DIR = trial-results (a git worktree, detached)
#   scripts/loop/results.sh commit DIR MSG    commit everything in DIR and push, rebasing on
#                                             whatever else was pushed meanwhile (5 tries)
# A worktree of the current clone, so pushing uses the same credentials as the checkout.
set -euo pipefail
cmd=$1 dir=$2
case "$cmd" in
  checkout)
    rm -rf "$dir"; git worktree prune
    if git fetch -q --depth 1 origin trial-results 2>/dev/null; then
      git worktree add -q --detach "$dir" FETCH_HEAD
    else
      git worktree add -q --detach "$dir"
      git -C "$dir" checkout -q --orphan trial-results-new
      git -C "$dir" rm -rqf . || true
    fi
    mkdir -p "$dir/loop"
    ;;
  commit)
    msg=$3
    cd "$dir"
    git config user.name >/dev/null || git config user.name "github-actions[bot]"
    git config user.email >/dev/null || git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git add -A
    git diff --cached --quiet && { echo "nothing to commit"; exit 0; }
    git commit -q -m "$msg"
    for i in 1 2 3 4 5; do
      git push -q origin HEAD:refs/heads/trial-results && exit 0
      sleep $((i * 5))
      git fetch -q --depth 20 origin trial-results && git rebase -q FETCH_HEAD || { git rebase --abort || true; }
    done
    echo "push failed" >&2; exit 1
    ;;
  *) echo "usage: $0 checkout|commit DIR [MSG]" >&2; exit 2 ;;
esac
