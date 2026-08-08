#!/usr/bin/env python3
"""Catch Kotlin's "exposes its private type" error before CI does.

Kotlin refuses to let an `internal` or public declaration mention a `private` type in its
signature -- parameter, return type, or an inferred generic argument:

    private data class SeatPosition(...)
    internal val SeatPositions = listOf(SeatPosition(...))   // List<SeatPosition> -> ERROR

That is a compile error, not a warning, and it is invisible to the other checkers here:
braces balance, comments nest correctly, no new symbols appear. It cost a red CI run while
splitting Screens.kt, during a commit whose whole point was that it could not change
behaviour -- promoting 35 declarations from `private` to `internal` tripped exactly one of
them, because a promoted property's INFERRED type still pointed at a private class.

Splitting a 14.6k-line file means many more private -> internal promotions, so this is worth
having as a gate rather than a lesson.

Scope, deliberately narrow to stay false-positive-free:
  - only TOP-LEVEL declarations (column 0), which is where file-scoped `private` bites;
  - only private types declared in the SAME file, since that is the only case Kotlin can
    complain about for a top-level declaration;
  - the signature is captured by balancing parentheses from the declaration line, plus a few
    following lines for a property whose type is inferred from its initializer.

Exit 1 and print each offender. Run before every push, alongside check-comment-nesting.py
and check-new-symbols.py.
"""
import re
import sys
from pathlib import Path

TYPE_DECL = re.compile(
    r"^private (?:@\w+\s+)?(?:value )?(?:data |sealed |enum |annotation )?"
    r"(?:class|object|interface)\s+(\w+)"
)
# Top-level internal/public declaration. No leading whitespace: nested members are governed
# by their container's visibility, not the file's.
OPEN_DECL = re.compile(
    r"^(?:internal |public )?(?:@\w+\s+)?(?:expect |actual )?(?:suspend )?"
    r"(?:inline |value |data |sealed |enum |annotation )*"
    r"(?:fun|val|var|class|object|interface|typealias)\s"
)
IS_PRIVATE = re.compile(r"^private\s")


def signature_of(lines, i):
    """Text of the declaration starting at line i, up to the end of its parameter list.

    A declaration with no `(` gets its own line and nothing more. Reading ahead a few lines
    "in case the type is inferred" is what produced this checker's one false positive:
    `enum class PinFlowMode { … }` was flagged for a `private enum class PinFlowStep`
    declared just below it. The inferred-type case that actually matters --
    `internal val Xs = listOf(...)` -- contains a `(`, so the paren-balancing path already
    covers it and the look-ahead bought nothing but noise.
    """
    line = lines[i]
    if "(" not in line:
        return line
    out, depth = [], 0
    for j in range(i, min(i + 80, len(lines))):
        out.append(lines[j])
        depth += lines[j].count("(") - lines[j].count(")")
        if depth <= 0:
            # Include the return type, which trails the closing paren.
            break
    return "\n".join(out)


def check(path):
    lines = path.read_text(encoding="utf-8", errors="replace").split("\n")
    private_types = {m.group(1) for l in lines for m in [TYPE_DECL.match(l)] if m}
    if not private_types:
        return []
    bad = []
    for i, line in enumerate(lines):
        if IS_PRIVATE.match(line) or not OPEN_DECL.match(line):
            continue
        sig = signature_of(lines, i)
        for t in sorted(private_types):
            if re.search(r"\b%s\b" % re.escape(t), sig):
                bad.append((i + 1, t, line.strip()[:90]))
                break
    return bad


def main():
    root = Path(__file__).resolve().parent.parent
    files = [p for p in root.rglob("*.kt") if "build" not in p.parts]
    failures = 0
    for p in sorted(files):
        for lineno, type_name, decl in check(p):
            failures += 1
            rel = p.relative_to(root).as_posix()
            print(f"{rel}:{lineno}: non-private declaration exposes private type "
                  f"'{type_name}'\n    {decl}")
    print(f"checked {len(files)} Kotlin files for visibility leaks")
    if failures:
        print(f"FAILED: {failures} declaration(s) expose a private type "
              f"-- promote the type to internal, or keep the declaration private")
        return 1
    print("no visibility leaks: every non-private declaration keeps private types out of "
          "its signature")
    return 0


if __name__ == "__main__":
    sys.exit(main())
