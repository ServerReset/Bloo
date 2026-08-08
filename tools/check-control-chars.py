#!/usr/bin/env python3
"""Catch RAW control characters written into source files.

These compile. That is the problem. Kotlin happily accepts a literal U+0000 inside a string
literal, so CI goes green and nothing tells you the byte is there -- but every text tool
downstream changes behaviour:

    keys.sorted().joinToString("<U+0000 here>") { ... }

`grep` classifies a file containing a NUL as BINARY and prints "Binary file ... matches"
INSTEAD of the matching lines. That is not a cosmetic problem: it silently truncated a search
in this repo, hiding two of the six lines I was checking, in the same session that introduced
the byte. A tool that answers a question with fewer results than the truth, without erroring,
is the worst possible failure mode -- and diffs, code review and editors hide it just as well.

The byte arrived from an escape (`\\u0000`) that got resolved to the character it denotes
before being written. The escape is what was meant: identical to the compiler, visible to
everything else.

Scope: text source files under the repo. Flags any C0 control character other than tab (9),
newline (10) and carriage return (13) -- CRLF is normal on Windows -- plus a few invisible
characters above the C0 range that have no business in source:
  - U+00A0 NO-BREAK SPACE, which looks exactly like a space and is not one (it survives
    copy-paste from docs and rendered web pages, and Kotlin does NOT accept it as whitespace);
  - U+200B/200C/200D zero-width space/non-joiner/joiner, and U+FEFF when it appears anywhere
    other than as a leading byte-order mark.

Deliberately NOT flagged: ordinary non-ASCII text. This repo's comments legitimately contain
em dashes, arrows, degree signs and box-drawing characters, and its strings contain emoji.

Exit 1 and print file:line:column for each offender, with the character named, so the fix is
mechanical: replace it with its escape.
"""
import sys
import unicodedata
from pathlib import Path

SUFFIXES = {".kt", ".kts", ".java", ".py", ".xml", ".pro", ".md", ".json", ".yml", ".yaml",
            ".toml", ".gradle", ".properties"}

ALLOWED_CONTROL = {0x09, 0x0A, 0x0D}
INVISIBLE = {0x00A0, 0x200B, 0x200C, 0x200D, 0xFEFF}


def offenders(text):
    """Yield (line, column, codepoint) for each disallowed character.

    Line and column are 1-based. A U+FEFF at offset 0 is a byte-order mark and is skipped;
    anywhere else it is a zero-width no-break space and is not.
    """
    line, col = 1, 1
    for offset, ch in enumerate(text):
        cp = ord(ch)
        bad = (cp < 0x20 and cp not in ALLOWED_CONTROL) or cp == 0x7F or cp in INVISIBLE
        if cp == 0xFEFF and offset == 0:
            bad = False
        if bad:
            yield line, col, cp
        if ch == "\n":
            line, col = line + 1, 1
        else:
            col += 1


def name_of(cp):
    try:
        return unicodedata.name(chr(cp))
    except ValueError:
        return "control character"


def main():
    root = Path(__file__).resolve().parent.parent
    files = [p for p in root.rglob("*")
             if p.suffix in SUFFIXES and p.is_file()
             and "build" not in p.parts and ".git" not in p.parts]
    failures = 0
    for p in sorted(files):
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for lineno, col, cp in offenders(text):
            failures += 1
            print(f"{p.relative_to(root).as_posix()}:{lineno}:{col}: raw U+{cp:04X} "
                  f"({name_of(cp)}) in source -- write it as an escape instead")
    print(f"checked {len(files)} text files for raw control characters")
    if failures:
        print(f"FAILED: {failures} raw control/invisible character(s). These COMPILE, which is "
              f"why this check exists: a NUL makes grep report the whole file as binary and "
              f"print no matching lines at all.")
        return 1
    print("no raw control characters: every invisible character is written as an escape")
    return 0


if __name__ == "__main__":
    sys.exit(main())
