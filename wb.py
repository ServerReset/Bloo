
p = "app/src/main/java/com/bloo/bluelink/wear/WearBridge.kt"
s = open(p, encoding="utf-8").read()
def sub(old, new, why):
    global s
    assert s.count(old) == 1, (why, s.count(old))
    s = s.replace(old, new)

sub("""    /** Publish the on-disk snapshots as a Data Layer item (phone -> watch). */
    suspend fun publishNow(context: Context) {
        val data = SnapshotStore(context).current()""",
"""    /**
     * Publish the on-disk snapshots as a Data Layer item (phone -> watch).
     *
     * [snapshot] lets a caller that has ALREADY decoded the snapshot hand it over instead of
     * paying for a second decode. `current()` is a DataStore read plus a full JSON decode of
     * every vehicle, and [publishAll] used to trigger three of them in a row -- here, in
     * publishSettingsNow, and once more for its own VIN list. Null (the default) keeps every
     * other caller reading for itself, exactly as before.
     */
    suspend fun publishNow(context: Context, snapshot: SnapshotStore.SnapshotData? = null) {
        val data = snapshot ?: SnapshotStore(context).current()""",
    "publishNow")

sub("""    suspend fun publishSettingsNow(context: Context, appearance: SettingsStore.Appearance) {""",
"""    suspend fun publishSettingsNow(
        context: Context,
        appearance: SettingsStore.Appearance,
        /** See [publishNow] -- an already-decoded snapshot, to skip a redundant decode. */
        snapshot: SnapshotStore.SnapshotData? = null,
    ) {""",
    "publishSettingsNow sig")

sub("""        val vins = SnapshotStore(context).current().vehicles.map { it.vin }
        val pebbleOrders = vins.associateWith { store.sectionOrder(it) }""",
"""        val vins = (snapshot ?: SnapshotStore(context).current()).vehicles.map { it.vin }
        val pebbleOrders = vins.associateWith { store.sectionOrder(it) }""",
    "publishSettingsNow vins")

sub("""        com.bloo.bluelink.data.AppLog.log("Watch: full resync requested")
        runCatching { publishNow(context) }
        runCatching { publishAuth(context) }
        runCatching {
            val appearance = com.bloo.bluelink.data.SettingsStore(context).appearance.first()
            publishSettingsNow(context, appearance)
        }
        runCatching {
            val store = com.bloo.bluelink.data.SettingsStore(context)
            val vins = SnapshotStore(context).current().vehicles.map { it.vin }""",
"""        com.bloo.bluelink.data.AppLog.log("Watch: full resync requested")
        // Decoded ONCE and threaded through all three publishes below. `current()` is a
        // DataStore read plus a full JSON decode of the whole vehicle list, and this function
        // used to trigger three of them: publishNow's, publishSettingsNow's, and the VIN list
        // for presets. Reading once also makes the three payloads consistent with each other
        // by construction rather than by luck -- they used to be three independent reads that
        // a concurrent write could land between.
        //
        // Its own runCatching, and null on failure, so a snapshot read that throws still
        // leaves each publish to read for itself exactly as it did before.
        val snapshot = runCatching { SnapshotStore(context).current() }.getOrNull()
        runCatching { publishNow(context, snapshot) }
        runCatching { publishAuth(context) }
        runCatching {
            val appearance = com.bloo.bluelink.data.SettingsStore(context).appearance.first()
            publishSettingsNow(context, appearance, snapshot)
        }
        runCatching {
            val store = com.bloo.bluelink.data.SettingsStore(context)
            val vins = (snapshot ?: SnapshotStore(context).current()).vehicles.map { it.vin }""",
    "publishAll")

open(p, "w", encoding="utf-8", newline="").write(s)
print("WearBridge: 3 decodes -> 1")
