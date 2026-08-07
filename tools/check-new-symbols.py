"""Flag Capitalized( calls added by the working diff that are neither imported
nor declared in the file.

Exists because this repo has no local Gradle (the plugin repo is unreachable
from the dev container), so the compiler only runs in CI and a missing import
costs a full red build to discover. Two have now: detectDragGestures and
TextButton, both added mid-edit to a 12k-line file that already imports
hundreds of Compose symbols, where nothing about the call site looks wrong.

Deliberately crude and diff-scoped: it is not a type checker, it only asks
whether a NEWLY-added constructor-or-composable call resolves to a name the
file has actually brought into scope. Usage:  python3 tools/check-new-symbols.py
Compare a commit range by editing the git args below.
"""
import re, subprocess, sys, os
diff = subprocess.run(["git","diff","HEAD","-U0"],capture_output=True,encoding="utf-8",errors="replace").stdout
cur=None; added={}
for line in diff.splitlines():
    if line.startswith("+++ b/"): cur=line[6:]
    elif line.startswith("+") and cur and cur.endswith(".kt"):
        added.setdefault(cur,[]).append(line[1:])
bad=0
for f,lines in added.items():
    if not os.path.exists(f): continue
    # encoding pinned: these sources are UTF-8 and contain "·", "—" and friends.
    # Bare open() uses the platform default, which is cp1252 on Windows, and the
    # checker died with a UnicodeDecodeError before examining a single symbol --
    # i.e. the one local guard against a red build was itself unusable there.
    src=open(f, encoding="utf-8").read()
    imported={m.split(".")[-1] for m in re.findall(r'^import\s+([\w.]+)', src, re.M)}
    declared=set(re.findall(r'\b(?:class|object|interface|enum class|fun)\s+([A-Z]\w*)', src))
    declared|=set(re.findall(r'\bval\s+([A-Z]\w*)', src))
    # Enum constants: `NAME(args...)` at the top of an enum body reads
    # exactly like a call to the checker's diff-side pattern below, so a
    # newly-added constant (e.g. WidgetAction.UNLOCK("unlock", ...)) flagged
    # itself as "not imported, not declared" -- it's declared, just via enum
    # entry syntax rather than one of the keywords above. ALL_CAPS_STYLE is
    # every existing constant's own convention (LOCK, CLIMATE, HORN, ...),
    # not a guess.
    declared|=set(re.findall(r'^\s+([A-Z][A-Z0-9_]*)\(', src, re.M))
    # Kotlin needs no import for a type declared in another file of the SAME
    # package -- e.g. WidgetInfoField (in WidgetConfig.kt) used unqualified
    # from WidgetScaleTest.kt, both `package com.bloo.bluelink.widget`. The
    # checks above only look inside the one file being diffed, so a symbol
    # whose only declaration lives in a sibling file was never found and
    # always got flagged, entirely correctly-compiling code included.
    # Scoped to .kt files that declare the SAME package line -- a real
    # match on Kotlin's own visibility rule, not a blanket "search the whole
    # repo" that would hide a genuinely-wrong cross-package reference. A
    # src/test file's package commonly mirrors a src/main one without living
    # in the same directory, so this walks the repo rather than just the
    # diffed file's own directory.
    pkg_m = re.search(r'^package\s+([\w.]+)', src, re.M)
    if pkg_m:
        pkg = pkg_m.group(1)
        for root, _dirs, files in os.walk("."):
            if "/.git" in root:
                continue
            for sib in files:
                if not sib.endswith(".kt"):
                    continue
                sib_path = os.path.join(root, sib)
                if os.path.abspath(sib_path) == os.path.abspath(f):
                    continue
                sib_src = open(sib_path, encoding="utf-8", errors="ignore").read()
                if re.search(rf'^package\s+{re.escape(pkg)}\s*$', sib_src, re.M):
                    declared|=set(re.findall(r'\b(?:class|object|interface|enum class|fun)\s+([A-Z]\w*)', sib_src))
                    declared|=set(re.findall(r'\bval\s+([A-Z]\w*)', sib_src))
    # Names already used in the committed version of this file resolve today,
    # whatever the mechanism -- kotlin stdlib (Regex, Pair), a same-package
    # declaration from another module, a typealias. Only a name that is NEW to
    # the file is worth asking about; anything else is noise that trains you to
    # ignore the check, which is worse than not having it.
    # encoding pinned for the same reason as the open() calls above: text=True
    # decodes with the platform default, so on Windows this died on the first
    # non-cp1252 byte and left .stdout as None, which then blew up in re.findall.
    prev = subprocess.run(["git","show",f"HEAD:{f}"],capture_output=True,encoding="utf-8",errors="replace").stdout or ""
    # Same shape as the new-code pattern below (Name( or Name.member() --
    # this was widened to catch qualified calls without widening THIS one to
    # match, so a name used only in qualified form anywhere in the file's
    # history (e.g. WearPhotoCache.ingest() already committed) was never
    # recognised as "already fine" and got flagged on every later edit near
    # it. Caught by the checker's own false positive, not by review.
    already = set(re.findall(r'(?<![\w.])([A-Z]\w+)\s*[(.]', prev))
    # Kotlin stdlib constructors/factories that never need importing. Not an
    # exhaustive list -- just the ones common enough that flagging them trains
    # you to ignore the tool.
    already |= {
        "Pair", "Triple", "Regex", "Exception", "RuntimeException", "Error",
        "IllegalStateException", "IllegalArgumentException", "String", "Array",
        "IntArray", "ByteArray", "FloatArray", "BooleanArray", "Comparator",
    }
    for ln in lines:
        code=re.sub(r'//.*','',ln)
        # An ANNOTATION is not a call. @Suppress("DEPRECATION"), @OptIn(Foo::class)
        # and friends read as `Name(` to the pattern below, so every one of them was
        # reported as an unresolved symbol -- and @Suppress("DEPRECATION") is the
        # house pattern for the legacy-API fallbacks in this codebase (four files use
        # it), which meant adding one always produced a spurious failure. The
        # annotation-specific check further down handles imports for these; this scan
        # is about call targets, so skip them here.
        #
        # Matched strictly: the line must be NOTHING BUT an annotation, so a real call
        # sharing a line with one (`@Suppress("x") val y = Foo()`) is still checked.
        # `[^)]*` and not `.*` for the argument list: a greedy `.*` runs to the LAST
        # ")" on the line, so `@Suppress("x") val y = Undefined(1)` matched in full and
        # the real call went unchecked. Verified by mutation test, which is the only
        # reason I noticed.
        if re.match(r'^\s*@(?:file:)?[\w.]+(?:\([^)]*\))?\s*$', code):
            continue
        # Both "Name(" and "Name.member(" -- a qualified static/object call is
        # just as unresolvable, and missing it is what let an unimported
        # WearPhotoCache.pathFor( through to a red build.
        for name in re.findall(r'(?<![\w.])([A-Z]\w+)\s*[(.]', code):
            if name not in imported and name not in declared and name not in already:
                print(f"{f}: {name}(  <- not imported, not declared")
                bad+=1

# --- second check: an annotation added directly above a declaration that
# already carries the same one, fully qualified or not. This is what cost a
# red build when @Immutable was re-added to a class that already had
# @androidx.compose.runtime.Immutable -- invisible to a grep for "^@Immutable",
# and invisible to a reader who starts at the line being edited.
for f in added:
    if not os.path.exists(f):
        continue
    lines = open(f, encoding="utf-8").read().split("\n")
    for i, ln in enumerate(lines):
        m = re.match(r'\s*@(?:[\w.]+\.)?(\w+)\s*$', ln)
        if not m:
            continue
        name = m.group(1)
        # look back over KDoc/comments for the same annotation
        j = i - 1
        while j >= 0 and (lines[j].strip().startswith(("*", "/*", "//", "*/")) or not lines[j].strip()):
            j -= 1
        if j >= 0:
            m2 = re.match(r'\s*@(?:[\w.]+\.)?(\w+)\s*$', lines[j])
            if m2 and m2.group(1) == name:
                print(f"{f}:{i+1}: @{name} is already applied at line {j+1}")
                bad += 1

# --- third check: lowercase Compose Modifier extension functions (e.g.
# Modifier.width(...)) added by the diff but never imported into the file.
# The two checks above only match capitalized names ([A-Z]\w+), so they were
# blind to this shape entirely -- exactly what let Modifier.width(4.dp)
# through to a red build in WidgetConfigActivity.kt when only .height/.size/
# .weight had been imported into that file before. Not exhaustive: just the
# layout modifiers common enough to show up mid-edit without a fresh import.
MODIFIER_EXTENSIONS = {
    "width", "height", "size", "padding", "offset", "fillMaxWidth",
    "fillMaxHeight", "fillMaxSize", "weight", "align", "wrapContentSize",
    "wrapContentWidth", "wrapContentHeight", "requiredWidth", "requiredHeight",
    "requiredSize", "widthIn", "heightIn", "sizeIn", "aspectRatio",
}
for f, lines in added.items():
    if not os.path.exists(f):
        continue
    src = open(f, encoding="utf-8").read()
    imported_members = {m.split(".")[-1] for m in re.findall(r'^import\s+([\w.]+)', src, re.M)}
    prev = subprocess.run(["git", "show", f"HEAD:{f}"], capture_output=True, encoding="utf-8", errors="replace").stdout or ""
    # Same rationale as the first check: a call already resolving somewhere
    # in the committed file (however it got there) isn't this diff's problem.
    already_used = set(re.findall(r'\.(\w+)\s*\(', prev))
    for ln in lines:
        code = re.sub(r'//.*', '', ln)
        for name in re.findall(r'\.(\w+)\s*\(', code):
            if name in MODIFIER_EXTENSIONS and name not in imported_members and name not in already_used:
                print(f"{f}: .{name}(  <- Compose modifier extension not imported, not previously used")
                bad += 1

sys.exit(1 if bad else 0)
