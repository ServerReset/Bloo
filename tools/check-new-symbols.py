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
    for ln in lines:
        code=re.sub(r'//.*','',ln)
        for name in re.findall(r'(?<![\w.])([A-Z]\w+)\s*\(', code):
            if name not in imported and name not in declared:
                print(f"{f}: {name}(  <- not imported, not declared")
                bad+=1
sys.exit(1 if bad else 0)
