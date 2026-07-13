package com.halehoundforge.fire.companion

import android.content.Context
import com.halehoundforge.fire.privacy.ExportCrypto
import com.halehoundforge.fire.privacy.PiiScrubber
import com.halehoundforge.fire.privacy.PrivacySettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local loot vault on Fire — offloads CYD SD pressure onto tablet storage.
 * Optional AES wrap when privacy encrypt is on.
 */
object CydLootVault {

    fun vaultRoot(context: Context): File {
        val base = context.getExternalFilesDir("cyd_loot") ?: File(context.filesDir, "cyd_loot")
        base.mkdirs()
        return base
    }

    fun sessionDir(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(vaultRoot(context), "session_$stamp")
        dir.mkdirs()
        return dir
    }

    fun listLocal(context: Context): List<File> {
        val root = vaultRoot(context)
        if (!root.exists()) return emptyList()
        return root.walkTopDown().filter { it.isFile }.toList().sortedByDescending { it.lastModified() }
    }

    fun writeTelemetrySnapshot(context: Context, telemetry: CydLinkClient.Telemetry): File {
        val dir = vaultRoot(context)
        val f = File(dir, "last_telemetry.txt")
        val body = buildString {
            appendLine("CYD TELEMETRY SNAPSHOT")
            appendLine("base=${telemetry.baseUrl}")
            appendLine("online=${telemetry.online} latencyMs=${telemetry.latencyMs}")
            appendLine("title=${telemetry.title}")
            appendLine("heap=${telemetry.heapHint} freeSd=${telemetry.freeSdHint} mode=${telemetry.modeHint}")
            appendLine("--- paths ---")
            telemetry.rawPaths.forEach { (k, v) -> appendLine("$v  $k") }
            appendLine("--- loot hints ---")
            telemetry.lootHints.forEach { appendLine("[${it.category}] ${it.name}  ${it.path}") }
            appendLine("--- notes ---")
            telemetry.notes.forEach { appendLine(it) }
        }
        val scrubbed = PiiScrubber.maybeScrub(context, body)
        f.writeText(scrubbed)
        if (PrivacySettings.encryptExports(context)) {
            val enc = File(dir, "last_telemetry.txt.hhf")
            ExportCrypto.encryptToFile(context, scrubbed.toByteArray(Charsets.UTF_8), enc)
            return enc
        }
        return f
    }

    suspend fun pullAll(
        context: Context,
        baseUrl: String,
        entries: List<CydLinkClient.LootEntry>,
        maxFiles: Int = 24
    ): List<File> {
        val session = sessionDir(context)
        val saved = mutableListOf<File>()
        for (e in entries.take(maxFiles)) {
            val f = CydLinkClient.pullLootEntry(baseUrl, e, session) ?: continue
            if (PrivacySettings.encryptExports(context) && f.length() < 2_000_000L) {
                try {
                    val enc = File(session, f.name + ".hhf")
                    ExportCrypto.encryptToFile(context, f.readBytes(), enc)
                    saved += enc
                } catch (_: Exception) {
                    saved += f
                }
            } else {
                saved += f
            }
        }
        File(session, "INDEX.txt").writeText(
            PiiScrubber.maybeScrub(
                context,
                saved.joinToString("\n") { "${it.name}  ${it.length()}b" }
            )
        )
        return saved
    }
}
