#!/usr/bin/env python3
"""Self-test: every workflow file is valid YAML with jobs (python scripts/loop/test_workflows.py).

A broken loop.yml doesn't fail a pull request by itself: GitHub only shows a failed "push" run of
it, and the next generation can't start once it's on main (a ': ' in a plain run line, 2026-09-30).
"""
import glob
import os
import sys

try:
    import yaml
except ImportError:
    print("workflows: skipped (no pyyaml)")
    sys.exit(0)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

for path in sorted(glob.glob(os.path.join(ROOT, ".github", "workflows", "*.yml"))):
    doc = yaml.safe_load(open(path, encoding="utf-8"))
    assert isinstance(doc, dict) and doc.get("jobs"), path
    for name, job in doc["jobs"].items():
        assert "runs-on" in job or "uses" in job, "%s: job %s" % (path, name)
print("workflows ok")
