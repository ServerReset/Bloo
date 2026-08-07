#!/usr/bin/env python3
"""Fail if any Kotlin file has unbalanced block comments.

Kotlin block comments NEST -- unlike Java, C, or JavaScript. So a `/*` appearing
inside a KDoc block opens a second level, and the next `*/` returns to level 1
instead of closing the comment. Everything after it, to end of file, is silently
swallowed as comment text.

This is not hypothetical. It cost a full CI cycle and five bisect runs on this
repo: a KDoc explaining that a layout uses `?android:attr/*` for light/dark
theming opened a nested comment with that trailing `/*`, and the remaining ~2,840
lines of CarWidget.kt vanished. The compiler's report was a cascade -- "expecting
'}'", "class is not abstract and does not implement provideGlance", and every
function in the file suddenly undefined -- none of which pointed at a comment.

Writing `/*` inside a comment is almost never deliberate, and the failure mode is
disproportionate to the typo, so this rejects it outright rather than trying to
work out whether it balances.

Run from the repo root:  python tools/check-comment-nesting.py
"""

import sys
from pathlib import Path

SKIP_DIRS = {".git", "build", ".gradle", ".idea", "generated"}


def scan(text):
    """Walk the file tracking block-comment depth.

    Returns (depth_at_eof, [(line, kind)]) where kind is 'nested-open' for a `/*`
    found while already inside a comment. Skips over line comments, string
    literals, raw strings and char literals so their contents can't be mistaken
    for comment delimiters.
    """
    events = []
    depth = 0
    line = 1
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if depth > 0:
            if text.startswith("/*", i):
                events.append((line, "nested-open"))
                depth += 1
                i += 2
                continue
            if text.startswith("*/", i):
                depth -= 1
                i += 2
                continue
            i += 1
            continue
        # depth == 0: real code, so strings and line comments are live
        if text.startswith("/*", i):
            depth = 1
            i += 2
            continue
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            if j < 0:
                return depth, events
            line += text.count("\n", i, j)
            i = j + 3
            continue
        if c in "\"'":
            i += 1
            while i < n and text[i] != c:
                if text[i] == "\\":
                    i += 1
                elif text[i] == "\n":
                    break
                i += 1
            i += 1
            continue
        i += 1
    return depth, events


def main():
    root = Path(__file__).resolve().parent.parent
    failures = []
    checked = 0
    for path in sorted(root.rglob("*.kt")):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        checked += 1
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        depth, events = scan(text)
        rel = path.relative_to(root).as_posix()
        # Only the FIRST nested open is a real finding. Once a file is stuck inside
        # a comment, every later `/**` registers as another nested open, so a single
        # typo reports dozens of times -- and the extras all sit below the line you
        # actually have to edit. Show a few, then say how many followed.
        for ln, _kind in events[:3]:
            failures.append(
                f"{rel}:{ln}: '/*' inside a block comment -- Kotlin nests these, "
                "so the next '*/' will not close it"
            )
        if len(events) > 3:
            failures.append(
                f"{rel}: ...and {len(events) - 3} more, which are almost certainly "
                f"knock-on effects of line {events[0][0]} -- fix that one first"
            )
        if depth != 0:
            failures.append(
                f"{rel}: unterminated block comment (depth {depth} at end of file) "
                "-- the rest of this file is being parsed as comment text"
            )

    print(f"checked {checked} Kotlin files")
    if failures:
        print(f"\n{len(failures)} problem(s):")
        for f in failures:
            print("  " + f)
        return 1
    print("block comments balanced in all files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
