#!/usr/bin/env python3
"""Catch the one mistake that has broken this build more than any other:
code moved into a new file without the imports it needs.

Three times during the widget rebuild, a function was lifted out of
CarWidget.kt into a new file and the build failed on `Unresolved reference`
for a symbol whose import stayed behind -- R, actionRunCallback, sp,
formatDistance, relativeLabel, actionParametersOf. Every one was invisible
to review (the moved code reads fine; the missing line is in a file you are
not looking at) and every one cost a full CI round trip to discover, because
there is no local Android toolchain to compile against.

The check is deliberately conservative, since it has no compiler and must not
cry wolf. It only reports a symbol when ALL of these hold:

  1. the file references the simple name,
  2. that name is DECLARED somewhere in this project, in a package other than
     the file's own (so it genuinely needs an import),
  3. the file does not import it -- not by name, not by a `.*` wildcard on its
     package,
  4. the name is not declared in the file's own package (same-package
     references need no import),
  5. the name is not shadowed by a local declaration, parameter or import
     alias in that file.

That means it stays silent about anything it cannot prove, including every
symbol from outside the project (androidx, kotlin stdlib, java.*) -- those are
the compiler's job. It exists to catch the specific, repeated, mechanical
mistake of a MOVE losing an import, which is exactly the case where the
symbol's declaration is sitting right there in the project to be found.

Exit code 1 if anything is reported.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Declarations we can recognise at file scope or as members that are referenced
# by simple name. Kept intentionally narrow: top-level decls are what a move
# actually loses.
_DECL_TAIL = (
    r'(?:public\s+|internal\s+|private\s+|protected\s+)?'
    r'(?:expect\s+|actual\s+)?'
    r'(?:abstract\s+|final\s+|open\s+|sealed\s+|data\s+|value\s+|inline\s+|suspend\s+|inner\s+|annotation\s+|const\s+)*'
    r'(?:class|interface|object|enum\s+class|fun|val|var|typealias)\s+'
    r'(?:<[^>]*>\s*)?'
    r'(?:[\w.]+\.)?'          # extension receiver, e.g. ColumnScope.MapFill
    r'([A-Za-z_]\w*)'
)

# ONLY column-0 declarations count as package-scope. A `val` indented inside a
# function body is a LOCAL -- importable by nobody -- and treating those as
# package declarations made this check unusable: names like `cancel`, `get`,
# `action` and `window` appear as locals in dozens of files, so every file that
# happened to use the same word was reported. Anchoring to column 0 is the
# difference between a check that finds real missing imports and one that
# prints hundreds of lines nobody will read.
DECL_RE = re.compile(r'^(?:@\w+(?:\([^)]*\))?\s*)*' + _DECL_TAIL)
# For "is this name declared anywhere in THIS file", indentation is fine --
# a nested declaration still means the file is not missing an import for it.
ANY_DECL_RE = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*' + _DECL_TAIL)
PACKAGE_RE = re.compile(r'^\s*package\s+([\w.]+)', re.M)
IMPORT_RE = re.compile(r'^\s*import\s+([\w.]+)(?:\.(\*)|\s+as\s+(\w+))?\s*$', re.M)


def strip_noise(text: str) -> str:
    """Remove comments and string literals so their words are not read as code."""
    out, i, n = [], 0, len(text)
    while i < n:
        two = text[i:i + 2]
        if two == '/*':
            depth, i = 1, i + 2          # Kotlin block comments nest
            while i < n and depth:
                if text[i:i + 2] == '/*':
                    depth, i = depth + 1, i + 2
                elif text[i:i + 2] == '*/':
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
            continue
        if two == '//':
            while i < n and text[i] != '\n':
                i += 1
            continue
        if text[i:i + 3] == '"""':
            i += 3
            while i < n and text[i:i + 3] != '"""':
                i += 1
            i += 3
            continue
        if text[i] == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == '\\' else 1
            i += 1
            continue
        if text[i] == '`':
            # A backtick-quoted identifier -- overwhelmingly a Kotlin test name,
            # which is an English sentence. `fun \`no blueprint ever commits more
            # height than the tile has\`()` contains the word "height", and
            # reading that as a reference to androidx.glance.layout.height is
            # how a checker earns a reputation for crying wolf.
            i += 1
            while i < n and text[i] != '`':
                i += 1
            i += 1
            continue
        out.append(text[i])
        i += 1
    return ''.join(out)


def kotlin_files() -> list[str]:
    found = []
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in ('build', '.git', '.gradle')]
        found += [os.path.join(base, f) for f in files if f.endswith('.kt')]
    return sorted(found)




def locals_of(body):
    """Every name that is bound INSIDE this file and therefore needs no import:
    declarations at any nesting, typed parameters, vals/vars, loop variables
    (including destructured ones) and explicit lambda parameters.

    Both rules below need this identically. They did not share it at first, and
    the weaker copy in the move rule reported `size` in a `for ((c, r, size) in
    ...)` as a missing import -- a false positive produced purely by the two
    copies disagreeing about what counts as local.
    """
    out = {d.group(1) for line in body.split('\n') if (d := ANY_DECL_RE.match(line))}
    out |= set(re.findall(r'\b(\w+)\s*:\s*[A-Z]', body))
    # Function-type parameters: `onClick: () -> Unit`, `onSelect: (String) -> Unit`.
    # The rule above only recognises a parameter whose type starts with an
    # uppercase letter, so these were invisible as locals -- and a body that then
    # forwards one (`onClick = onClick`) looked like a reference to something
    # unimported. That is how a watch login screen with an `onClick` parameter got
    # reported the moment an unrelated file in the same commit happened to shed
    # lines and become a donor.
    out |= set(re.findall(r'\b(\w+)\s*:\s*\(', body))
    out |= set(re.findall(r'\b(?:val|var)\s+(\w+)', body))
    out |= set(re.findall(r'\bfor\s*\(\s*\(?\s*([\w\s,]+?)\s*\)?\s+in\b', body))
    out |= {n.strip() for group in re.findall(r'\bfor\s*\(\s*\(([^)]*)\)\s+in\b', body)
            for n in group.split(',')}
    out |= {n.strip() for group in re.findall(r'\{\s*([\w\s,]+?)\s*->', body)
            for n in group.split(',')}
    return {n for n in out if n}


def moved_import_problems(raw, code, pkg_of, base='HEAD'):
    """The rule this script was actually built for: code that MOVED into a
    file without the imports it needs.

    The fingerprint is structural, not textual. In one change, some file
    SHRANK (code left it) and another file GREW or was ADDED (the code landed
    there). Any simple name the grown file uses, which the shrunk file
    imports, and which the grown file does not import, is a lost import.

    That works where a plain "was an import line deleted" test does not, and
    the difference is the whole point: when CarWidget.kt gave up six hundred
    lines, its own import block was left completely untouched -- the lines
    were still needed by what stayed, or simply not cleaned up. Nothing was
    removed, so nothing textual signalled the loss; only the shape of the
    change did.

    It needs no idea what a symbol IS, so it covers the library half that a
    project-declaration scan cannot see: R, sp, actionRunCallback and
    actionParametersOf are androidx or generated, and all four are caught
    here purely because the file they came from still imports them.

    Scoped to the files one change touches, which is what keeps it quiet: a
    scope member like `size` or `onClick` is only ever reported if some file
    that just shed code imports that exact name.
    """
    def git(*args):
        return subprocess.run(['git', *args], cwd=ROOT,
                              capture_output=True, text=True, timeout=30).stdout

    try:
        numstat = git('diff', base, '--numstat', '--', '*.kt')
    except Exception:
        return []
    if not numstat.strip():
        return []

    shrank, grew = [], []
    for line in numstat.strip().split('\n'):
        parts = line.split('\t')
        if len(parts) != 3:
            continue
        added, deleted, rel = parts
        if added == '-' or deleted == '-':
            continue
        f = os.path.join(ROOT, rel)
        # A DONOR is a file that genuinely gave code away, which means it ended
        # up smaller. Treating any file with deletions as a donor made every
        # ordinary edit one -- two files touched in the same commit became each
        # other's donors, and every name one imported was reported against the
        # other. Requiring a net loss keeps the rule pointed at moves.
        if int(deleted) > int(added):
            shrank.append(f)
        if int(added) > 0:
            grew.append(f)

    def imports_of(f):
        out = {}
        for m in IMPORT_RE.finditer(raw.get(f, '')):
            path, star, alias = m.group(1), m.group(2), m.group(3)
            if not star and not alias:
                out[path.rsplit('.', 1)[-1]] = path
        return out

    donor = {}
    for f in shrank:
        donor.update(imports_of(f))
    if not donor:
        return []

    out = []
    for f in grew:
        if f not in code:
            continue
        file_imports = imports_of(f)
        have = set(file_imports)
        wildcards = {m.group(1) for m in IMPORT_RE.finditer(raw[f]) if m.group(2)}
        have |= {m.group(3) for m in IMPORT_RE.finditer(raw[f]) if m.group(3)}
        body = IMPORT_RE.sub('', code[f])
        local = locals_of(body)
        for name, path in sorted(donor.items()):
            if name in have or name in local:
                continue
            if path.rsplit('.', 1)[0] in wildcards:
                continue
            if path.rsplit('.', 1)[0] == pkg_of.get(f):
                continue
            # Plain reference, OR the numeric-literal extension idiom that
            # Compose is built on: `14.sp`, `8.dp`. The dot in `14.sp` makes it
            # look like member access, so the usual "not preceded by a dot"
            # guard skips it -- and `sp` was one of the seven symbols that
            # broke this build, missed for exactly that reason.
            plain = re.search(r'(?<![\w.])' + re.escape(name) + r'(?![\w])', body)
            if plain:
                out.append((f, name,
                            f'used here, imported by a file that shed code: {path}', True))
                continue
            # Extension property on some expression -- `heroSp.sp`, `value.dp`.
            # Textually identical to ordinary member access (`car.percent`), so
            # this cannot be proven without a compiler and is reported as
            # ADVISORY rather than failing the check. It is still worth
            # printing: `sp` was one of the seven symbols that broke this
            # build, and `heroSp.sp` is precisely this shape.
            # `x.name(` is a member FUNCTION call, never an extension property:
            # `14.sp` and `value.dp` are the shape being looked for, and neither
            # is ever followed by an argument list. Without this, every
            # `blueprint.height(MODULE)` reads as a possible lost import of
            # androidx.glance.layout.height.
            if re.search(r'[\w)]\s*\.\s*' + re.escape(name) + r'(?![\w(])', body):
                out.append((f, name,
                            f'possible extension-property use, imported by a file '
                            f'that shed code: {path}', False))
    return out


def main() -> int:
    files = kotlin_files()
    raw = {f: open(f, encoding='utf-8').read() for f in files}
    code = {f: strip_noise(t) for f, t in raw.items()}

    # name -> set of packages declaring it
    declared: dict[str, set[str]] = {}
    pkg_of: dict[str, str] = {}
    for f, text in code.items():
        m = PACKAGE_RE.search(text)
        pkg = m.group(1) if m else ''
        pkg_of[f] = pkg
        for line in text.split('\n'):
            d = DECL_RE.match(line)
            if d:
                declared.setdefault(d.group(1), set()).add(pkg)

    problems = []
    for f, text in code.items():
        pkg = pkg_of[f]
        imported, wildcards = set(), set()
        for m in IMPORT_RE.finditer(raw[f]):
            path, star, alias = m.group(1), m.group(2), m.group(3)
            if star:
                wildcards.add(path)
            else:
                imported.add(alias or path.rsplit('.', 1)[-1])

        body = IMPORT_RE.sub('', text)
        # Names declared in THIS file (any nesting) shadow the need for an import.
        local = locals_of(body)

        used = set(re.findall(r'(?<![\w.])([A-Za-z_]\w*)', body))
        for name in sorted(used):
            if name in imported or name in local:
                continue
            # A NAMED ARGUMENT is a parameter's name, not a reference to
            # anything importable: `preferencesDataStore(corruptionHandler = x)`
            # names a parameter of that function. If every occurrence of the
            # name in this file is in `name =` position (and never `==`), it is
            # not a reference and needs no import. Without this the check
            # reports every file that passes a named argument which happens to
            # collide with some top-level val elsewhere in the project.
            total = len(re.findall(r'(?<![\w.])' + re.escape(name) + r'(?![\w])', body))
            as_named = len(re.findall(
                r'(?<![\w.])' + re.escape(name) + r'\s*=(?!=)', body))
            if total and total == as_named:
                continue
            homes = declared.get(name)
            if not homes:
                continue                      # not ours -- compiler's problem
            if pkg in homes:
                continue                      # same package, no import needed
            if any(h in wildcards for h in homes):
                continue
            problems.append((f, name, 'declared in ' + ', '.join(sorted(homes))))

    base = sys.argv[sys.argv.index('--base') + 1] if '--base' in sys.argv else 'HEAD'
    problems += moved_import_problems(raw, code, pkg_of, base)

    problems = [(f, n, w, True) if len(p) == 3 else p
                for p in problems for f, n, w in [p[:3]]]
    seen, strong, weak = set(), [], []
    for f, name, why, is_strong in problems:
        if (f, name) in seen:
            continue
        seen.add((f, name))
        (strong if is_strong else weak).append((f, name, why))

    if weak:
        print('ADVISORY -- cannot be proven without a compiler, check by eye:\n')
        for f, name, why in weak:
            print(f'  {os.path.relpath(f, ROOT)}: {name}  ({why})')
        print()

    if strong:
        print('MISSING IMPORTS -- referenced here, but not imported and not')
        print('reachable from this file\'s own package:\n')
        for f, name, why in strong:
            print(f'  {os.path.relpath(f, ROOT)}: {name}  ({why})')
        return 1

    print(f'checked {len(files)} Kotlin files for imports lost in a move')
    print('every project symbol referenced is imported or same-package')
    return 0


if __name__ == '__main__':
    sys.exit(main())
