package com.halehoundforge.fire.debug

import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import com.halehoundforge.fire.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Elite-enough field crash capture for Fire OS sideload:
 * - Default uncaught exception handler (all threads)
 * - Main-thread-specific handler
 * - Breadcrumb dump + device fingerprint into local DebugVault
 * - Never phones home
 */
object CrashGuard {

    private const val TAG = "HHF-Crash"
    private val installed = AtomicBoolean(false)
    private var appContext: Context? = null
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        appContext = context.applicationContext
        previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(thread, throwable, fatal = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record crash", e)
            }
            // Chain to system / previous so process still dies cleanly
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    Log.e(TAG, "Uncaught on ${thread.name}", throwable)
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
        }

        // Surface main looper exceptions that some OEMs soft-swallow
        try {
            Looper.getMainLooper().thread.uncaughtExceptionHandler =
                Thread.UncaughtExceptionHandler { t, e ->
                    Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(t, e)
                }
        } catch (_: Exception) {
        }

        Breadcrumbs.add("CRASH", "CrashGuard installed v${BuildConfig.VERSION_NAME}")
        Log.i(TAG, "CrashGuard armed")
    }

    fun recordNonFatal(tag: String, throwable: Throwable) {
        Breadcrumbs.error("$tag: ${throwable.javaClass.simpleName}: ${throwable.message}")
        val ctx = appContext ?: return
        try {
            val body = buildReport(Thread.currentThread(), throwable, fatal = false, extra = tag)
            DebugVault.writeReport(ctx, "nonfatal", body)
        } catch (_: Exception) {
        }
    }

    fun recordHang(detail: String, stackHint: String) {
        val ctx = appContext ?: return
        Breadcrumbs.warn("HANG $detail")
        try {
            val body = buildString {
                appendLine("═══ HALEHOUND FIRE HANG ═══")
                appendLine(deviceHeader())
                appendLine("detail: $detail")
                appendLine("--- main stack hint ---")
                appendLine(stackHint)
                appendLine("--- breadcrumbs ---")
                appendLine(Breadcrumbs.dump())
            }
            DebugVault.writeReport(ctx, "hang", body)
            Log.e(TAG, "Hang recorded: $detail")
        } catch (_: Exception) {
        }
    }

    private fun recordCrash(thread: Thread, throwable: Throwable, fatal: Boolean) {
        val ctx = appContext ?: return
        val body = buildReport(thread, throwable, fatal, extra = null)
        DebugVault.writeReport(ctx, if (fatal) "crash" else "nonfatal", body)
        Log.e(TAG, "Crash recorded fatal=$fatal", throwable)
    }

    private fun buildReport(
        thread: Thread,
        throwable: Throwable,
        fatal: Boolean,
        extra: String?
    ): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("═══ HALEHOUND FIRE ${if (fatal) "CRASH" else "NONFATAL"} ═══")
            appendLine(deviceHeader())
            if (extra != null) appendLine("extra: $extra")
            appendLine("thread: ${thread.name} id=${thread.id} state=${thread.state}")
            appendLine("--- exception ---")
            appendLine(sw.toString())
            appendLine("--- breadcrumbs ---")
            appendLine(Breadcrumbs.dump())
            appendLine("--- threads (sample) ---")
            appendLine(threadDumpSample())
        }
    }

    private fun deviceHeader(): String = buildString {
        appendLine("app     : ${BuildConfig.APPLICATION_ID}")
        appendLine("version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("debug   : ${BuildConfig.DEBUG}")
        appendLine("sdk     : ${Build.VERSION.SDK_INT} ${Build.VERSION.RELEASE}")
        appendLine("device  : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("board   : ${Build.BOARD}  hardware=${Build.HARDWARE}")
        appendLine("time    : ${System.currentTimeMillis()}")
    }

    private fun threadDumpSample(): String {
        return try {
            Thread.getAllStackTraces().entries
                .sortedBy { it.key.name }
                .take(24)
                .joinToString("\n") { (t, st) ->
                    val top = st.take(4).joinToString(" <- ") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                    "${t.name} [${t.state}] $top"
                }
        } catch (e: Exception) {
            "(thread dump failed: ${e.message})"
        }
    }
}
