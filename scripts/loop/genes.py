#!/usr/bin/env python3
"""Gene audit: where every gene in Tune.java stands, so the gene list shrinks instead of growing.

    python scripts/loop/genes.py --state /tmp/tr/loop/state.json

Policy (2026-09-29, 164 genes and growing, most never raced):
  - A gene the champion plays that its defender has too (so it's settled) becomes the default in
    Tune.java: local play then matches the champion, and the old branch is dead code to delete.
  - A gene that lost two solo races judged by its own metric ("_metric") is a deletion candidate:
    remove the gene and the code behind it.
  - Losses inside a bundle, or under the old whole-game rule (before gen 62), are not evidence
    against one gene: those genes race again, alone, with a metric.
Lists: settled in the champion, racing, lost solo on a metric (with margins), lost only in
bundles or the old rule, never raced.
"""
import argparse
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402


def audit(st, genes):
    champ = st["genomes"][st["champion"]]
    defenders = [g for g in st["genomes"].values() if g["status"] == "contender" and g["note"].endswith("(defending)")]
    races = collections.defaultdict(list)
    for g in st["genomes"].values():
        for n in g.get("mutated", []):
            races[n].append(g)
    out = collections.defaultdict(list)
    for n, spec in genes.items():
        v = champ["genes"].get(n)
        if v is not None and all(d["genes"].get(n) == v for d in defenders):
            out["settled in the champion (make it the default)" if v != spec["def"] else "champion = default"].append(
                "%s = %g (default %g)" % (n, v, spec["def"]))
            continue
        rs = races.get(n, [])
        if not rs:
            out["never raced"].append(n)
        elif any(g["status"] in ("contender", "champion") for g in rs):
            out["racing or champion"].append(n)
        else:
            solo = [g for g in rs if len(g.get("mutated", [])) == 1 and g.get("metric")]
            if len(solo) >= 2:
                out["lost 2+ solo metric races (delete)"].append("%s: %s" % (n, ", ".join(
                    "%s z %s" % (g["id"], g.get("metric_z")) for g in solo)))
            else:
                out["lost only in bundles or the old rule (race again, alone)"].append(n)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    a = ap.parse_args()
    out = audit(json.load(open(a.state, encoding="utf-8")), common.load_genes())
    for k, v in out.items():
        print("== %s: %d" % (k, len(v)))
        for line in v:
            print("   " + line)


if __name__ == "__main__":
    main()
