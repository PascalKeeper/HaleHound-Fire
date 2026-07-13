package com.halehoundforge.fire.debug

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Main-thread hang detector (ANR-adjacent).
 *
 * Pings the main looper on a cadence; if the ping is not acknowledged within
 * [timeoutMs], records a hang report with a best-effort main stack sample.
 *
 * Fire-class devices: slightly relaxed defaults to avoid false positives under
 * thermal/IO pressure, still catches TERM→GUARD class freezes.
 */
object HangWatchdog {

    private const val TAG = "HHF-Hang"
    private val running = AtomicBoolean(false)
    private val lastAck = AtomicLong(0L)
    private val hangLatched = AtomicBoolean(false)

    private var mainHandler: Handler? = null
    private var watcher: Thread? = null

    /** How often to post a ping */
    private var intervalMs = 2_000L
    /** If main hasn't acked within this, it's a hang */
    private var timeoutMs = 5_000L

    fun start(intervalMs: Long = 2_000L, timeoutMs: Long = 5_000L) {
        if (!running.compareAndSet(false, true)) return
        this.intervalMs = intervalMs
        this.timeoutMs = timeoutMs
        mainHandler = Handler(Looper.getMainLooper())
        lastAck.set(SystemClock.uptimeMillis())
        hangLatched.set(false)

        watcher = Thread({
            Breadcrumbs.add("HANG", "HangWatchdog started interval=${intervalMs}ms timeout=${timeoutMs}ms")
            while (running.get()) {
                val handler = mainHandler
                if (handler == null) break
                val postedAt = SystemClock.uptimeMillis()
                handler.post {
                    lastAck.set(SystemClock.uptimeMillis())
                    hangLatched.set(false)
                }
                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    break
                }
                val ack = lastAck.get()
                val now = SystemClock.uptimeMillis()
                // Hang if last ack is older than timeout (main never ran the ping)
                if (now - ack >= timeoutMs && hangLatched.compareAndSet(false, true)) {
                    val stuckMs = now - ack
                    val stack = mainStackSample()
                    Log.e(TAG, "Main hang ~${stuckMs}ms")
                    CrashGuard.recordHang(
                        detail = "main_thread_unresponsive ~${stuckMs}ms (timeout=${timeoutMs}ms)",
                        stackHint = stack
                    )
                }
                // prevent unused warning for postedAt in release minify edge cases
                if (postedAt < 0) break
            }
        }, "hhf-hang-watchdog").also {
            it.isDaemon = true
            it.priority = Thread.NORM_PRIORITY - 1
            it.start()
        }
    }

    fun stop() {
        running.set(false)
        watcher?.interrupt()
        watcher = null
        mainHandler = null
    }

    fun poke(reason: String) {
        Breadcrumbs.ui("poke:$reason")
        lastAck.set(SystemClock.uptimeMillis())
        hangLatched.set(false)
    }

    private fun mainStackSample(): String {
        return try {
            val main = Looper.getMainLooper().thread
            val st = main.stackTrace
            buildString {
                appendLine("main state=${main.state}")
                st.take(30).forEach { appendLine("  at $it") }
            }
        } catch (e: Exception) {
            "(stack sample failed: ${e.message})"
        }
    }
}
