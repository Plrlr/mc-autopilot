#!/usr/bin/env python3
"""Self-test: the evolved skills on this checkout keep the rules (python scripts/loop/test_evolved_genes.py).

Runs in pr-check and in evolve_skill.py's build check. Every entry of evolved-genes.json is a valid
BOOL gene defaulting to 0 and not one of Tune's; every registry line names a gene in that list and a
class file that exists; every skill file in skills/evolved/ passes the fair-play gate. When
origin/main is fetched, the gene list must extend main's (append-only).
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import fairplay  # noqa: E402


def main():
    genes_path = os.path.join(common.ROOT, fairplay.GENES_FILE)
    if not os.path.exists(genes_path):
        print("evolved genes: none on this checkout")
        return
    doc = json.load(open(genes_path, encoding="utf-8"))
    entries = doc.get("genes", [])
    tune = set(common.load_genes())
    bad = fairplay.check_gene_list([], entries, known=tune)
    try:
        r = subprocess.run(["git", "show", "origin/main:" + fairplay.GENES_FILE], cwd=common.ROOT,
                           capture_output=True, text=True, timeout=30)
        if r.returncode == 0:
            bad += fairplay.check_gene_list(json.loads(r.stdout).get("genes", []), entries, known=tune)
    except (subprocess.SubprocessError, OSError, ValueError):
        pass  # no main to compare with: the other checks still hold
    names = {e.get("name") for e in entries}
    reg_path = os.path.join(common.ROOT, fairplay.REGISTRY)
    reg = open(reg_path, encoding="utf-8").read() if os.path.exists(reg_path) else ""
    for gene, skill, cls in re.findall(r'add\(new Entry\("([\w.]+)",\s*"(\w+)",\s*(\w+)::new', reg):
        if gene not in names:
            bad.append("registry names %s, which evolved-genes.json lacks" % gene)
        if not os.path.exists(os.path.join(common.ROOT, fairplay.EVOLVED_DIR, cls + ".java")):
            bad.append("registry names class %s, which has no file" % cls)
    skills = {cls: skill for _, skill, cls in re.findall(r'add\(new Entry\("([\w.]+)",\s*"(\w+)",\s*(\w+)::new', reg)}
    d = os.path.join(common.ROOT, fairplay.EVOLVED_DIR)
    for name in sorted(os.listdir(d)) if os.path.isdir(d) else []:
        if name.endswith(".java") and name not in fairplay.FRAMEWORK:
            src = open(os.path.join(d, name), encoding="utf-8").read()
            bad += ["%s: %s" % (name, v) for v in fairplay.check_source(src, name, skills.get(name[:-5]))]
    if bad:
        print("\n".join(bad))
        sys.exit(1)
    print("evolved genes ok (%d)" % len(entries))


if __name__ == "__main__":
    main()
