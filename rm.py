import re, pathlib
FILES = [
    "app/src/main/java/com/bloo/bluelink/tiles/TileCommandWorker.kt",
    "app/src/main/java/com/bloo/bluelink/ui/AppViewModel.kt",
    "app/src/main/java/com/bloo/bluelink/wear/WearBridge.kt",
]
CALL = re.compile(r"^\s*(?:runCatching \{ )?(?:com\.bloo\.bluelink\.tiles\.)?BlooTileService\.requestUpdates\([^)]*\)(?: \})?\s*$")
removed = 0
for f in FILES:
    p = pathlib.Path(f)
    lines = p.read_text(encoding="utf-8").split("\n")
    keep = []
    for l in lines:
        if CALL.match(l):
            removed += 1
            continue
        keep.append(l)
    p.write_text("\n".join(keep), encoding="utf-8", newline="")
print("removed", removed, "call sites (expect 13)")
assert removed == 13, removed
