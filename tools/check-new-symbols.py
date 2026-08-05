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
diff = subprocess.run(["git","diff","HEAD","-U0"],capture_output=True,text=True).stdout
cur=None; added={}
for line in diff.splitlines():
    if line.startswith("+++ b/"): cur=line[6:]
    elif line.startswith("+") and cur and cur.endswith(".kt"):
        added.setdefault(cur,[]).append(line[1:])
bad=0
for f,lines in added.items():
    if not os.path.exists(f): continue
    src=open(f).read()
    imported={m.split(".")[-1] for m in re.findall(r'^import\s+([\w.]+)', src, re.M)}
    declared=set(re.findall(r'\b(?:class|object|interface|enum class|fun)\s+([A-Z]\w*)', src))
    declared|=set(re.findall(r'\bval\s+([A-Z]\w*)', src))
    # Names already used in the committed version of this file resolve today,
    # whatever the mechanism -- kotlin stdlib (Regex, Pair), a same-package
    # declaration from another module, a typealias. Only a name that is NEW to
    # the file is worth asking about; anything else is noise that trains you to
    # ignore the check, which is worse than not having it.
    prev = subprocess.run(["git","show",f"HEAD:{f}"],capture_output=True,text=True).stdout
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
    lines = open(f).read().split("\n")
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

sys.exit(1 if bad else 0)
