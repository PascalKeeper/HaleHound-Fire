package com.halehoundforge.fire.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.halehoundforge.fire.BuildConfig
import com.halehoundforge.fire.perf.LatencyProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opt-in Wi‑Fi updates — no dojo USB required.
 *
 * Default: does nothing until the operator taps Check / runs `update`.
 * Fetches public GitHub Releases (HTTPS). Does not phone home to any
 * private sensei server unless [customFeedUrl] is set later.
 *
 * Ninja posture: no background polling unless you add it later (still off).
 */
object AppUpdateChecker {

    const val DEFAULT_FEED =
        "https://api.github.com/repos/PascalKeeper/HaleHound-Fire/releases/latest"

    data class ReleaseInfo(
        val tag: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val apkName: String?,
        val newer: Boolean,
        val notes: String
    )

    data class DownloadResult(
        val file: File,
        val bytes: Long
    )

    suspend fun checkLatest(feedUrl: String = DEFAULT_FEED): ReleaseInfo =
        withContext(Dispatchers.IO) {
            val p = LatencyProfiles.active
            val conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = p.httpConnectMs * 2
                readTimeout = p.httpReadMs * 3
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "HaleHound-Fire/${BuildConfig.VERSION_NAME}")
                // Public unauthenticated API — no token stored
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    return@withContext ReleaseInfo(
                        tag = "?",
                        name = "check failed",
                        body = "",
                        htmlUrl = "https://github.com/PascalKeeper/HaleHound-Fire/releases",
                        apkUrl = null,
                        apkName = null,
                        newer = false,
                        notes = "HTTP $code — ${raw.take(200)}"
                    )
                }
                val json = JSONObject(raw)
                val tag = json.optString("tag_name", "")
                val name = json.optString("name", tag)
                val body = json.optString("body", "")
                val html = json.optString("html_url", "")
                var apkUrl: String? = null
                var apkName: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    // Prefer debug APK for debug builds, else any .apk
                    val wantDebug = BuildConfig.DEBUG
                    val list = (0 until assets.length()).map { assets.getJSONObject(it) }
                    val pick = list.firstOrNull { o ->
                        val n = o.optString("name", "").lowercase()
                        n.endsWith(".apk") && if (wantDebug) n.contains("debug") else !n.contains("debug")
                    } ?: list.firstOrNull { o ->
                        o.optString("name", "").lowercase().endsWith(".apk")
                    }
                    if (pick != null) {
                        apkUrl = pick.optString("browser_download_url", "").ifBlank { null }
                        apkName = pick.optString("name", "update.apk").ifBlank { "update.apk" }
                    }
                }
                val newer = isNewer(tag, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                ReleaseInfo(
                    tag = tag,
                    name = name,
                    body = body,
                    htmlUrl = html.ifBlank {
                        "https://github.com/PascalKeeper/HaleHound-Fire/releases"
                    },
                    apkUrl = apkUrl,
                    apkName = apkName,
                    newer = newer,
                    notes = buildString {
                        appendLine("installed : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("latest    : $tag  $name")
                        appendLine(
                            when {
                                newer && apkUrl != null -> "APK asset found — can download over Wi‑Fi"
                                newer -> "Newer tag, but no .apk on release — open browser to install notes"
                                else -> "You are on latest (or same) tagged release"
                            }
                        )
                        if (apkName != null) appendLine("asset     : $apkName")
                    }.trimEnd()
                )
            } finally {
                conn.disconnect()
            }
        }

    suspend fun downloadApk(context: Context, url: String, fileName: String): DownloadResult =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, fileName.replace(Regex("""[^\w.\-]+"""), "_"))
            val p = LatencyProfiles.active
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = p.httpConnectMs * 3
                readTimeout = 120_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HaleHound-Fire/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("Download HTTP ${conn.responseCode}")
                }
                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(out).use { output ->
                        input.copyTo(output)
                    }
                }
                if (out.length() < 10_000L) {
                    out.delete()
                    throw IllegalStateException("APK too small — download incomplete?")
                }
                DownloadResult(out, out.length())
            } finally {
                conn.disconnect()
            }
        }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun intentUnknownSources(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    /** Open system installer for a downloaded APK (user still confirms). */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openBrowser(context: Context, url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Compare release tag to installed version.
     * Tags like v1.2.0, 1.2.0-fire, v1.2.0-fire-debug → numeric triple.
     */
    fun isNewer(remoteTag: String, localName: String, localCode: Int): Boolean {
        val r = parseSemver(remoteTag)
        val l = parseSemver(localName)
        if (r != null && l != null) {
            for (i in 0..2) {
                if (r[i] != l[i]) return r[i] > l[i]
            }
            // same semver — allow newer if remote code hint in tag body unused; use name length
            return false
        }
        // Fallback: remote tag not equal and looks different
        val cleanR = remoteTag.trim().removePrefix("v")
        val cleanL = localName.substringBefore("-debug").removePrefix("v")
        return cleanR.isNotBlank() && cleanR != cleanL && cleanR > cleanL
    }

    private fun parseSemver(raw: String): IntArray? {
        val m = Regex("""v?(\d+)\.(\d+)\.(\d+)""").find(raw.trim()) ?: return null
        return intArrayOf(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt()
        )
    }

}
