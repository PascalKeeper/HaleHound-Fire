package com.halehoundforge.fire.debug

import android.os.Build
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Frame-time jank sampler via Choreographer (no Play Vitals dependency).
 * Logs breadcrumb when a frame exceeds budget; aggregates for TERM `jank`.
 */
object JankMonitor : Choreographer.FrameCallback {

    private val armed = AtomicBoolean(false)
    private var lastFrameNs = 0L
    /** 16.6ms @ 60Hz; Fire often 60Hz */
    private var budgetNs = 16_666_666L
    private val slowFrames = AtomicInteger(0)
    private val totalFrames = AtomicInteger(0)
    private val worstNs = AtomicLong(0L)

    fun start(targetFps: Int = 60) {
        if (!armed.compareAndSet(false, true)) return
        budgetNs = 1_000_000_000L / targetFps.coerceIn(30, 120)
        lastFrameNs = 0L
        slowFrames.set(0)
        totalFrames.set(0)
        worstNs.set(0L)
        Choreographer.getInstance().postFrameCallback(this)
        Breadcrumbs.add("JANK", "JankMonitor armed budget=${budgetNs / 1_000_000}ms")
    }

    fun stop() {
        if (!armed.compareAndSet(true, false)) return
        try {
            Choreographer.getInstance().removeFrameCallback(this)
        } catch (_: Exception) {
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!armed.get()) return
        if (lastFrameNs != 0L) {
            val dt = frameTimeNanos - lastFrameNs
            totalFrames.incrementAndGet()
            if (dt > worstNs.get()) worstNs.set(dt)
            // 2x budget = definite jank; ignore multi-second pauses (screen off)
            if (dt > budgetNs * 2 && dt < 500_000_000L) {
                val n = slowFrames.incrementAndGet()
                val ms = dt / 1_000_000L
                if (n <= 30 || n % 25 == 0) {
                    Breadcrumbs.warn("jank frame ${ms}ms (#$n)")
                }
            }
        }
        lastFrameNs = frameTimeNanos
        if (armed.get()) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun summary(): String {
        val total = totalFrames.get().coerceAtLeast(1)
        val slow = slowFrames.get()
        val worst = worstNs.get() / 1_000_000L
        val pct = 100.0 * slow / total
        return buildString {
            appendLine("═══ JANK (Choreographer) ═══")
            appendLine("frames   : $total")
            appendLine("slow     : $slow  (${"%.1f".format(pct)}%)")
            appendLine("worst    : ${worst}ms")
            appendLine("budget   : ${budgetNs / 1_000_000}ms")
            appendLine("sdk      : ${Build.VERSION.SDK_INT}")
            appendLine("armed    : ${armed.get()}")
        }.trimEnd()
    }

    fun resetCounters() {
        slowFrames.set(0)
        totalFrames.set(0)
        worstNs.set(0L)
    }
}
