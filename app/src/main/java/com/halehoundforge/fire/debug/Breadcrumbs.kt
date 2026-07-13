package com.halehoundforge.fire.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Lock-free-ish ring of recent operator/system events for crash reports.
 * Survives until process death; flushed into crash files by [CrashGuard].
 */
object Breadcrumbs {

    private const val MAX = 120
    private val ring = ConcurrentLinkedDeque<String>()
    private val fmt = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    fun add(tag: String, msg: String) {
        val line = "${fmt.get()!!.format(Date())} [$tag] $msg"
        ring.addLast(line)
        while (ring.size > MAX) ring.pollFirst()
    }

    fun nav(where: String) = add("NAV", where)

    fun ui(msg: String) = add("UI", msg)

    fun net(msg: String) = add("NET", msg)

    fun term(cmd: String) = add("TERM", cmd.take(80))

    fun warn(msg: String) = add("WARN", msg)

    fun error(msg: String) = add("ERR", msg)

    fun snapshot(limit: Int = MAX): List<String> = ring.toList().takeLast(limit)

    fun dump(): String = snapshot().joinToString("\n").ifEmpty { "(no breadcrumbs yet)" }

    fun clear() = ring.clear()
}
