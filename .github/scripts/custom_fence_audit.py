#!/usr/bin/env python3
"""Chimahon Custom: check that every fork change inside an upstream file is fenced.

A fork change in a file that also exists upstream must sit between `Custom -->` and
`Custom <--` markers (`// Custom -->`, `/* Custom --> */`, `<!-- Custom -->`), so an upstream
merge shows at a glance which lines are ours. Files the fork added are ours entirely and are
not checked.

Usage: custom_fence_audit.py [--base REF]

REF defaults to the merge base of HEAD and origin/main. The working tree is what gets checked,
so it can run before a commit. Exits 1 when something is unfenced.

Not checked, because a fence cannot be placed there: Kotlin `import` lines (ktlint rejects
comments in the import list) and blank lines. A hunk that only deletes upstream lines must
touch a fence, so the deletion is visible next to what replaced it.
"""

import argparse
import re
import subprocess
import sys

CHECKED_EXTENSIONS = (".kt", ".kts", ".js", ".xml", ".pro")
OPEN, CLOSE = "Custom -->", "Custom <--"
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def git(*args):
    return subprocess.run(("git",) + args, check=True, capture_output=True, text=True).stdout


def fenced_lines(lines):
    """1-based numbers of the lines inside a fence, markers included."""
    inside, fenced = False, set()
    for number, line in enumerate(lines, start=1):
        if OPEN in line:
            inside = True
        if inside:
            fenced.add(number)
        if CLOSE in line:
            inside = False
    return fenced


def unfenced_in(path, base):
    with open(path, encoding="utf-8") as file:
        lines = file.read().splitlines()
    fenced = fenced_lines(lines)
    problems = []
    for line in git("diff", "-U0", "--no-renames", base, "--", path).splitlines():
        match = HUNK.match(line)
        if not match:
            continue
        start, count = int(match.group(1)), int(match.group(2) or "1")
        if count == 0:
            if start not in fenced and start + 1 not in fenced:
                problems.append((start, "upstream lines deleted here without a fence"))
            continue
        for number in range(start, start + count):
            text = lines[number - 1].strip()
            if not text or number in fenced:
                continue
            if path.endswith((".kt", ".kts")) and text.startswith("import "):
                continue
            problems.append((number, text))
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", help="upstream ref to compare against")
    args = parser.parse_args()
    base = args.base or git("merge-base", "HEAD", "origin/main").strip()

    changed = git("diff", "--name-status", "--no-renames", base).splitlines()
    modified = [row.split("\t", 1)[1] for row in changed if row.startswith("M\t")]
    total = 0
    for path in sorted(p for p in modified if p.endswith(CHECKED_EXTENSIONS)):
        problems = unfenced_in(path, base)
        if not problems:
            continue
        total += len(problems)
        print(f"{path}: {len(problems)} unfenced")
        for number, text in problems:
            print(f"  {number}: {text[:100]}")
    if total:
        print(f"\n{total} unfenced fork lines in upstream files")
        return 1
    print("All fork changes in upstream files are fenced.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
