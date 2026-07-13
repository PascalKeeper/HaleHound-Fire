package com.halehoundforge.fire.companion

import android.content.Context
import com.halehoundforge.fire.perf.LatencyProfiles
import com.halehoundforge.fire.privacy.HomeCallPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Best-effort CYD companion bridge.
 * Probes common softAP/LAN HTTP surfaces used by ESP32 toolkits + HaleHound-style SD layouts.
 * Private softAP/LAN always OK. Public hosts only if HomeCallPolicy allows (HTTPS sensei path).
 */
object CydLinkClient {

    data class Telemetry(
        val baseUrl: String,
        val online: Boolean,
        val latencyMs: Long,
        val title: String,
        val heapHint: String?,
        val freeSdHint: String?,
        val modeHint: String?,
        val rawPaths: Map<String, Int>, // path -> HTTP code
        val bodySnippets: Map<String, String>,
        val lootHints: List<LootEntry>,
        val notes: List<String>
    )

    data class LootEntry(
        val name: String,
        val path: String,
        val category: String,
        val sizeHint: String? = null
    )

    /** Paths commonly exposed by ESP web UIs / companion firmwares */
    private val PROBE_PATHS = listOf(
        "/",
        "/status",
        "/api/status",
        "/api/info",
        "/info",
        "/system",
        "/heap",
        "/json",
        "/api",
        "/sd",
        "/list",
        "/files",
        "/api/files",
        "/loot",
        "/api/loot",
        "/eapol",
        "/wardriving",
        "/captures",
        "/subghz",
        "/wifi",
        "/ble"
    )

    /** SD layout from HaleHound README (loot categories) */
    private val LOOT_HINT_PATHS = listOf(
        "/sd/eapol/",
        "/sd/wardriving/",
        "/sd/wp_loot/",
        "/sd/loot/",
        "/sd/subghz/",
        "/eapol/",
        "/wardriving/",
        "/loot/",
        "/subghz/",
        "/captures/"
    )

    suspend fun connectAndProbe(baseRaw: String, context: Context? = null): Telemetry =
        withContext(Dispatchers.IO) {
        val base = normalizeBase(baseRaw)
        val notes = mutableListOf<String>()
        val pathCodes = linkedMapOf<String, Int>()
        val snippets = linkedMapOf<String, String>()
        val loot = ConcurrentLinkedQueue<LootEntry>()

        // Policy: softAP/LAN private always OK; public only via HomeCallPolicy
        denyIfPublicBlocked(base, context)?.let { reason ->
            return@withContext Telemetry(
                baseUrl = base,
                online = false,
                latencyMs = -1,
                title = "blocked",
                heapHint = null,
                freeSdHint = null,
                modeHint = null,
                rawPaths = emptyMap(),
                bodySnippets = emptyMap(),
                lootHints = emptyList(),
                notes = listOf(reason)
            )
        }

        // TCP open check
        val hostPort = parseHostPort(base)
        val lat = if (hostPort != null) {
            val start = System.currentTimeMillis()
            val open = try {
                Socket().use { s ->
                    s.tcpNoDelay = true
                    s.connect(
                        InetSocketAddress(hostPort.first, hostPort.second),
                        LatencyProfiles.active.connectTimeoutMs
                    )
                }
                true
            } catch (_: Exception) {
                false
            }
            if (!open) {
                return@withContext Telemetry(
                    baseUrl = base,
                    online = false,
                    latencyMs = -1,
                    title = "offline",
                    heapHint = null,
                    freeSdHint = null,
                    modeHint = null,
                    rawPaths = emptyMap(),
                    bodySnippets = emptyMap(),
                    lootHints = emptyList(),
                    notes = listOf("TCP closed — join CYD softAP or same LAN, check IP (often 192.168.4.1)")
                )
            }
            System.currentTimeMillis() - start
        } else -1L

        coroutineScope {
            PROBE_PATHS.map { path ->
                async {
                    val r = httpGet(base + path)
                    if (r != null) {
                        synchronized(pathCodes) { pathCodes[path] = r.code }
                        if (r.body.isNotBlank()) {
                            val snip = r.body.take(400).replace("\n", " ").trim()
                            synchronized(snippets) { snippets[path] = snip }
                            parseLootFromBody(path, r.body).forEach { loot += it }
                        }
                    }
                }
            }.awaitAll()

            LOOT_HINT_PATHS.map { path ->
                async {
                    val r = httpGet(base + path)
                    if (r != null && r.code in 200..399) {
                        synchronized(pathCodes) { pathCodes[path] = r.code }
                        parseLootFromBody(path, r.body).forEach { loot += it }
                        if (loot.none { it.path.startsWith(path) }) {
                            loot += LootEntry(
                                name = path.trimEnd('/').substringAfterLast('/').ifBlank { "index" },
                                path = path,
                                category = categoryFor(path),
                                sizeHint = "dir?"
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        val allBodies = snippets.values.joinToString("\n")
        val heap = Regex("""(?i)heap["\s:=]+(\d+)""").find(allBodies)?.groupValues?.get(1)
        val free = Regex("""(?i)(free|fs_free|sd_free)["\s:=]+(\d+)""").find(allBodies)?.groupValues?.getOrNull(2)
        val mode = Regex("""(?i)(mode|status|state)["\s:=]+([A-Za-z0-9_\- ]{2,24})""")
            .find(allBodies)?.groupValues?.getOrNull(2)

        if (pathCodes.isEmpty()) {
            notes += "Host open on TCP but no HTTP paths answered — firmware may not expose web UI on this port."
        } else {
            notes += "Probed ${pathCodes.size} live HTTP path(s). Loot list is best-effort until official companion API."
        }
        notes += "Official SD layout: /eapol /wardriving /wp_loot /loot /subghz (HaleHound README)."

        Telemetry(
            baseUrl = base,
            online = true,
            latencyMs = lat,
            title = snippets["/"]?.take(80) ?: "CYD HTTP ${pathCodes.size} paths",
            heapHint = heap,
            freeSdHint = free,
            modeHint = mode,
            rawPaths = pathCodes.toMap(),
            bodySnippets = snippets.toMap(),
            lootHints = loot.toList().distinctBy { it.path + it.name }.sortedBy { it.category + it.name },
            notes = notes
        )
    }

    suspend fun pullLootEntry(baseRaw: String, entry: LootEntry, destDir: File): File? =
        withContext(Dispatchers.IO) {
            destDir.mkdirs()
            val base = normalizeBase(baseRaw)
            val url = if (entry.path.startsWith("http")) entry.path
            else base.trimEnd('/') + (if (entry.path.startsWith("/")) entry.path else "/${entry.path}")
            val r = httpGetBytes(url) ?: return@withContext null
            val safeName = entry.name.replace(Regex("""[^\w.\-]+"""), "_").ifBlank { "loot.bin" }
            val out = File(destDir, "${entry.category}_$safeName")
            out.writeBytes(r)
            out
        }

    private fun parseLootFromBody(basePath: String, body: String): List<LootEntry> {
        val out = mutableListOf<LootEntry>()
        // JSON array of strings or objects
        try {
            if (body.trimStart().startsWith("[")) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val o = arr.opt(i)
                    when (o) {
                        is String -> out += LootEntry(o, join(basePath, o), categoryFor(basePath))
                        is JSONObject -> {
                            val name = o.optString("name", o.optString("file", o.optString("path", "item$i")))
                            val path = o.optString("path", join(basePath, name))
                            val size = o.optString("size", "").ifBlank { null }
                            out += LootEntry(name, path, categoryFor(path), size)
                        }
                    }
                }
            } else if (body.trimStart().startsWith("{")) {
                val obj = JSONObject(body)
                val keys = listOf("files", "loot", "entries", "list")
                for (k in keys) {
                    val arr = obj.optJSONArray(k) ?: continue
                    for (i in 0 until arr.length()) {
                        val name = arr.optString(i)
                        if (name.isNotBlank()) {
                            out += LootEntry(name, join(basePath, name), categoryFor(basePath))
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        // HTML href scrape
        Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(body).forEach { m ->
            val href = m.groupValues[1]
            if (href.startsWith("?") || href == "/" || href.startsWith("#")) return@forEach
            if (href.contains("..")) return@forEach
            val name = href.substringAfterLast('/').ifBlank { href }
            if (name.contains('.')) {
                out += LootEntry(name, join(basePath, href), categoryFor(href))
            }
        }
        // plain filenames
        Regex("""([\w.\-]+\.(?:pcap|cap|hc22000|csv|txt|sub|json|bin|log))""", RegexOption.IGNORE_CASE)
            .findAll(body).forEach { m ->
                val name = m.groupValues[1]
                out += LootEntry(name, join(basePath, name), categoryFor(name))
            }
        return out
    }

    private fun join(base: String, rel: String): String {
        if (rel.startsWith("http")) return rel
        if (rel.startsWith("/")) return rel
        val b = base.trimEnd('/')
        return if (b.endsWith(rel)) b else "$b/$rel".replace("//", "/")
    }

    private fun categoryFor(path: String): String {
        val p = path.lowercase()
        return when {
            p.contains("eapol") || p.contains("hc22000") || p.contains("pcap") -> "eapol"
            p.contains("wardriv") || p.endsWith(".csv") -> "wardriving"
            p.contains("wp_loot") || p.contains("whisper") || p.contains("ble") -> "wp_loot"
            p.contains("subghz") || p.endsWith(".sub") -> "subghz"
            p.contains("loot") -> "loot"
            else -> "misc"
        }
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun httpGet(url: String): HttpResult? {
        return try {
            val p = LatencyProfiles.active
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = p.httpConnectMs
                readTimeout = p.httpReadMs
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charset.forName("UTF-8"))).use { it.readText() }
            } else ""
            conn.disconnect()
            HttpResult(code, body.take(32_000))
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetBytes(url: String): ByteArray? {
        return try {
            val p = LatencyProfiles.active
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = p.httpConnectMs * 2
                readTimeout = p.httpReadMs * 3
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code !in 200..399) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.size > 8_000_000) bytes.copyOf(8_000_000) else bytes
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeBase(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (!u.startsWith("http")) u = "http://$u"
        return u
    }

    private fun parseHostPort(base: String): Pair<String, Int>? {
        return try {
            val url = URL(base)
            val host = url.host ?: return null
            val port = if (url.port > 0) url.port else if (url.protocol == "https") 443 else 80
            host to port
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Private softAP/LAN always allowed (CYD edge).
     * Non-private ("call home" sensei) requires HomeCallPolicy.
     */
    private fun denyIfPublicBlocked(base: String, context: Context?): String? {
        val host = parseHostPort(base)?.first ?: return "Bad CYD URL"
        if (HomeCallPolicy.isPrivateHost(host)) return null
        if (context == null) {
            return "Public host blocked (ninja): pass context or use private softAP/LAN only"
        }
        return when (val d = HomeCallPolicy.evaluate(context, base)) {
            is HomeCallPolicy.Decision.Allowed -> null
            is HomeCallPolicy.Decision.Denied -> "Public host blocked: ${d.reason}"
        }
    }

    /** SoftAP candidates when user hasn't set host */
    suspend fun autoDiscoverTelemetry(context: Context? = null): List<Telemetry> =
        withContext(Dispatchers.IO) {
        val hosts = listOf(
            "http://192.168.4.1",
            "http://192.168.0.1",
            "http://192.168.1.1",
            "http://10.0.0.1"
        )
        hosts.mapNotNull { base ->
            try {
                val t = connectAndProbe(base, context)
                if (t.online) t else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
