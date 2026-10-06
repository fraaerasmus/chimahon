#!/usr/bin/env python3
"""Chimahon Custom: check that every fork change inside an upstream file is fenced.

A fork change in a file that also exists upstream must sit between `Custom -->` and
`Custom <--` markers (`// Custom -->` ... `// Custom <--`, or `/* Custom --> */ ... /* Custom <-- */`
on one line), so an upstream merge shows at a glance which lines are ours. XML comments cannot
contain `--`, so XML closes with `<!-- /Custom -->` after opening with `<!-- Custom -->`. Files the fork added are ours entirely and are
not checked.

Usage: custom_fence_audit.py [--base REF]

REF defaults to the merge base of HEAD and origin/main. The working tree is what gets checked,
so it can run before a commit. Exits 1 when something is unfenced.

Not checked, because a fence cannot be placed there: Kotlin `import` lines (ktlint rejects
comments in the import list) and blank lines. A hunk that only deletes upstream lines must
touch a fence, so the deletion is visible next to what replaced it.

Which of two identical lines (a lone `),` or `}`) git reports as added depends on the git
version. A hunk therefore also passes when moving its edge to an identical neighbouring line
leaves every added line fenced, so the result is the same on every machine.
"""

import argparse
import re
import subprocess
import sys

CHECKED_EXTENSIONS = (".kt", ".kts", ".js", ".xml", ".pro")
OPEN, CLOSE, XML_CLOSE = "Custom -->", "Custom <--", "/Custom -->"
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def git(*args):
    return subprocess.run(("git",) + args, check=True, capture_output=True, text=True).stdout


def fenced_lines(lines):
    """1-based numbers of the lines inside a fence, markers included."""
    inside, fenced = False, set()
    for number, line in enumerate(lines, start=1):
        closes = CLOSE in line or XML_CLOSE in line
        if OPEN in line and XML_CLOSE not in line:
            inside = True
        if inside or closes:
            fenced.add(number)
        if closes:
            inside = False
    return fenced


def equivalent_alignments(lines, start, end):
    """The added line numbers of a hunk, plus the same hunk with one edge moved.

    When the unchanged line right after the hunk reads the same as a line inside it, the two
    can swap roles: the inner one is upstream's and the outer one is added. Likewise for the
    line right before the hunk.
    """
    added = set(range(start, end + 1))
    yield added
    for neighbour in (end + 1, start - 1):
        if not 1 <= neighbour <= len(lines):
            continue
        for number in range(start, end + 1):
            if lines[number - 1] == lines[neighbour - 1]:
                yield (added - {number}) | {neighbour}


def unfenced_in(path, base):
    with open(path, encoding="utf-8") as file:
        lines = file.read().splitlines()
    fenced = fenced_lines(lines)

    def unfenced(numbers):
        found = []
        for number in sorted(numbers):
            text = lines[number - 1].strip()
            if not text or number in fenced:
                continue
            if path.endswith((".kt", ".kts")) and text.startswith("import "):
                continue
            found.append((number, text))
        return found

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
        candidates = [unfenced(numbers) for numbers in equivalent_alignments(lines, start, start + count - 1)]
        if all(candidates):
            problems.extend(candidates[0])
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
