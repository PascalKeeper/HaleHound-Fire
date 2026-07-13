package com.halehoundforge.fire.debug

import android.content.Context
import com.halehoundforge.fire.privacy.PiiScrubber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash / hang report store — no phone-home.
 * Path: app filesDir/debug_reports/
 */
object DebugVault {

    fun root(context: Context): File {
        val dir = File(context.filesDir, "debug_reports")
        dir.mkdirs()
        return dir
    }

    fun writeReport(context: Context, kind: String, body: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safe = kind.replace(Regex("""[^\w\-]+"""), "_")
        val f = File(root(context), "${safe}_$stamp.txt")
        val scrubbed = PiiScrubber.maybeScrub(context, body)
        f.writeText(scrubbed)
        // Keep last N reports
        prune(context, keep = 40)
        return f
    }

    fun listReports(context: Context): List<File> =
        root(context).listFiles()
            ?.filter { it.isFile && it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun latest(context: Context): File? = listReports(context).firstOrNull()

    fun readLatest(context: Context, maxChars: Int = 12_000): String {
        val f = latest(context) ?: return "(no crash/hang reports yet)"
        return buildString {
            appendLine("FILE: ${f.name}")
            appendLine("SIZE: ${f.length()}b")
            appendLine("---")
            append(f.readText().take(maxChars))
        }
    }

    fun clear(context: Context): Int {
        val files = listReports(context)
        files.forEach { it.delete() }
        return files.size
    }

    private fun prune(context: Context, keep: Int) {
        val files = listReports(context)
        if (files.size <= keep) return
        files.drop(keep).forEach { it.delete() }
    }
}
