package com.bloo.bluelink.perf

/** What one frame cost, in milliseconds, split the way the platform reports it. */
internal class FrameCost(
    val totalMs: Float,
    val inputMs: Float,
    val animationMs: Float,
    val layoutMs: Float,
    val drawMs: Float,
    val syncMs: Float,
    val commandMs: Float,
    val swapMs: Float,
    val gpuMs: Float,
    /** Waiting before the frame could start (vsync offset and unexplained delay): the main thread was busy with something else. */
    val delayMs: Float,
) {
    /** The phase that cost the most, for a one-word answer to "where did this frame go". */
    fun worstPhase(): String = listOf(
        "input" to inputMs, "animation/composition" to animationMs, "layout" to layoutMs, "draw" to drawMs,
        "sync" to syncMs, "command" to commandMs, "swap" to swapMs, "delay" to delayMs,
    ).maxByOrNull { it.second }?.first ?: "none"

    fun phases(): String =
        "input ${fmt(inputMs)} anim ${fmt(animationMs)} layout ${fmt(layoutMs)} draw ${fmt(drawMs)} sync ${fmt(syncMs)} cmd ${fmt(commandMs)} swap ${fmt(swapMs)} gpu ${fmt(gpuMs)} delay ${fmt(delayMs)}"
}

internal fun fmt(v: Float): String = if (v >= 10f) v.toInt().toString() else "%.1f".format(v)

/**
 * The frames seen under one label (a screen, or something the user was doing): how many, how many missed their deadline, how slow the slow
 * ones were, and where the time went on average. Frame times go into a one-millisecond histogram, so a percentile is a lookup.
 */
internal class ScopeStats {
    var frames = 0
        private set
    var missed = 0
        private set
    var over32 = 0
        private set
    var over100 = 0
        private set
    var worstMs = 0f
        private set
    private val histogram = IntArray(HistogramMs + 1)
    private var sumTotal = 0.0
    private var sumInput = 0.0
    private var sumAnimation = 0.0
    private var sumLayout = 0.0
    private var sumDraw = 0.0
    private var sumSync = 0.0
    private var sumCommand = 0.0
    private var sumSwap = 0.0
    private var sumGpu = 0.0
    private var sumDelay = 0.0

    /** [budgetMs] is the display's frame time: a frame that takes longer than it missed its deadline. */
    fun add(cost: FrameCost, budgetMs: Float) {
        frames++
        if (cost.totalMs > budgetMs) missed++
        if (cost.totalMs > 32f) over32++
        if (cost.totalMs > 100f) over100++
        if (cost.totalMs > worstMs) worstMs = cost.totalMs
        histogram[cost.totalMs.toInt().coerceIn(0, HistogramMs)]++
        sumTotal += cost.totalMs
        sumInput += cost.inputMs
        sumAnimation += cost.animationMs
        sumLayout += cost.layoutMs
        sumDraw += cost.drawMs
        sumSync += cost.syncMs
        sumCommand += cost.commandMs
        sumSwap += cost.swapMs
        sumGpu += cost.gpuMs
        sumDelay += cost.delayMs
    }

    /** The frame time at or below which [p] (0..1) of the frames fall, to the millisecond. */
    fun percentile(p: Double): Float {
        if (frames == 0) return 0f
        val target = kotlin.math.ceil(frames * p).toInt().coerceAtLeast(1)
        var seen = 0
        for (ms in histogram.indices) {
            seen += histogram[ms]
            if (seen >= target) return ms.toFloat()
        }
        return HistogramMs.toFloat()
    }

    fun avgTotal(): Float = if (frames == 0) 0f else (sumTotal / frames).toFloat()

    fun avgPhases(): String {
        if (frames == 0) return ""
        fun a(v: Double) = fmt((v / frames).toFloat())
        return "input ${a(sumInput)} anim ${a(sumAnimation)} layout ${a(sumLayout)} draw ${a(sumDraw)} sync ${a(sumSync)} cmd ${a(sumCommand)} swap ${a(sumSwap)} gpu ${a(sumGpu)} delay ${a(sumDelay)}"
    }

    fun missedPercent(): Int = if (frames == 0) 0 else (missed * 100f / frames).toInt()

    companion object {
        /** Frame times past this land in the last bucket. */
        const val HistogramMs = 400
    }
}

/** The label frames are filed under: what the user was doing (while it lasted), else the screen they were on. */
internal class ScopeClock {
    private var scope: String? = null
    private var scopeEndNs = 0L

    @Volatile var screen: String = "start"

    /** From now until [durationMs] has passed, frames count under [label]; a new scope replaces the old one. */
    @Synchronized fun begin(label: String, durationMs: Long, nowNs: Long) {
        scope = label
        scopeEndNs = nowNs + durationMs * 1_000_000L
    }

    /** The label for a frame whose vsync was at [frameNs]. */
    @Synchronized fun labelAt(frameNs: Long): String = scope?.takeIf { frameNs <= scopeEndNs }?.let { "${screen}/$it" } ?: screen
}

/** Turns the main thread's stack samples, taken while it was stalled, into one line naming the code that was running. */
internal object StallStacks {
    private val interesting = listOf("com.bloo", "androidx.compose", "coil", "okhttp3", "okio", "kotlinx.coroutines", "androidx.datastore", "dev.chrisbanes")

    /** The first frame of a sample that belongs to the app or a library it leans on, else the top frame. */
    fun topFrame(stack: List<StackTraceElement>): String {
        val e = stack.firstOrNull { f -> interesting.any { f.className.startsWith(it) } } ?: stack.firstOrNull() ?: return "?"
        return "${e.className.substringAfterLast('.')}.${e.methodName}(${e.fileName}:${e.lineNumber})"
    }

    /** "Foo.bar(Foo.kt:12) x3, Baz.qux(Baz.kt:9) x1 [via A < B < C]": the distinct places, most often seen first, and how the first sample got there. */
    fun describe(samples: List<List<StackTraceElement>>): String {
        if (samples.isEmpty()) return "no sample"
        val counts = samples.groupingBy { topFrame(it) }.eachCount().entries.sortedByDescending { it.value }
        val places = counts.take(3).joinToString(", ") { "${it.key} x${it.value}" }
        val via = samples.first()
            .filter { f -> interesting.any { f.className.startsWith(it) } }
            .take(6).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        return if (via.isEmpty()) places else "$places [via $via]"
    }
}
