package com.halehoundforge.fire.terminal

import android.annotation.SuppressLint
import android.content.Context
import com.halehoundforge.fire.BuildConfig
import com.halehoundforge.fire.companion.CydDiscovery
import com.halehoundforge.fire.companion.CydLinkClient
import com.halehoundforge.fire.companion.CydLootVault
import com.halehoundforge.fire.core.DeviceProfile
import com.halehoundforge.fire.hardening.FirewallBatchGenerator
import com.halehoundforge.fire.hardening.HardeningEngine
import com.halehoundforge.fire.hardening.HardeningKnowledge
import com.halehoundforge.fire.perf.LatencyProfiles
import com.halehoundforge.fire.privacy.ExportCrypto
import com.halehoundforge.fire.privacy.HomeCallPolicy
import com.halehoundforge.fire.privacy.PiiScrubber
import com.halehoundforge.fire.privacy.PrivacySettings
import com.halehoundforge.fire.privacy.SecureStore
import java.io.File
import com.halehoundforge.fire.radio.BleSurvey
import com.halehoundforge.fire.radio.WifiSurvey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

/**
 * Easy, allowlisted ops terminal for humans + Grok Build agents.
 * No freeform root shell — curated commands that call real engines.
 */
class OpsTerminalEngine(private val context: Context) {

    data class Result(
        val output: String,
        val navigate: String? = null // fragment hint: harden|guard|wifi|cyd|ble|about|home
    )

    private val history = ArrayDeque<String>()

    fun historyLines(n: Int = 20): List<String> = history.toList().takeLast(n)

    suspend fun execute(raw: String): Result {
        val line = raw.trim()
        if (line.isEmpty()) return Result("")
        history.addLast(line)
        while (history.size > 80) history.removeFirst()

        val parts = tokenize(line)
        val cmd = parts.firstOrNull()?.lowercase(Locale.US) ?: return Result("")
        val args = parts.drop(1)

        return try {
            val result = when (cmd) {
                "help", "?", "h" -> Result(HELP)
                "agent", "grok", "protocol" -> Result(AGENT_PROTOCOL)
                "clear", "cls" -> Result("__CLEAR__")
                "history", "hist" -> Result(
                    historyLines().mapIndexed { i, s -> "${i + 1}  $s" }.joinToString("\n")
                        .ifEmpty { "(empty)" }
                )
                "status", "whoami", "device", "info" -> Result(statusBlock())
                "harden", "audit", "score" -> runHarden()
                "firewall", "fw", "netsh", "bat" -> runFirewallBat()
                "privacy", "opsec", "ninja" -> runPrivacy(args)
                "perf", "latency", "profile" -> runPerf(args)
                "wifi", "wlanscan", "scanwifi" -> runWifi()
                "ble", "blescan" -> runBle()
                "cyd" -> runCyd(args)
                "discover" -> runCyd(listOf("discover"))
                "guard", "guardian" -> Result(
                    "Local Guardian is always-on in the GUARD tab.\n" +
                        "Sensors: disconnect storms · RSSI cliffs · BSSID churn · latency.\n" +
                        "Tip: open guard  |  chips: GUARD",
                    navigate = "guard"
                )
                "ports", "portscan" -> runPorts(args)
                "ping" -> runPing(args)
                "dns" -> Result(dnsHelp())
                "getprop" -> runGetprop(args)
                "sh", "shell" -> runSafeShell(args)
                "open", "goto", "nav" -> {
                    val target = args.firstOrNull()?.lowercase(Locale.US) ?: "help"
                    val map = mapOf(
                        "harden" to "harden", "audit" to "harden",
                        "guard" to "guard", "guardian" to "guard",
                        "wifi" to "wifi", "wlan" to "wifi",
                        "cyd" to "cyd", "ble" to "ble",
                        "about" to "about", "home" to "home", "arsenal" to "home",
                        "term" to "term", "terminal" to "term"
                    )
                    val nav = map[target]
                    if (nav != null) Result("Opening $nav …", navigate = nav)
                    else Result("Unknown target. Try: open harden|guard|wifi|cyd|ble|about|home")
                }
                "chips", "menu" -> Result(CHIPS_HELP)
                "version", "ver" -> Result(
                    "HaleHound Fire ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        BuildConfig.APPLICATION_ID
                )
                "thanks", "dig", "family" -> Result(
                    "Copy that. Grok Build stays behind the glass;\n" +
                        "you keep living. Terminal is agent-friendly:\n" +
                        "  help · agent · harden · wifi · cyd · status"
                )
                else -> Result(
                    "Unknown command: $cmd\nType  help  or tap a chip below."
                )
            }
            // Scrub operator-facing transcript when privacy scrub is on
            if (result.output != "__CLEAR__" && result.output.isNotBlank()) {
                Result(PiiScrubber.maybeScrub(context, result.output), result.navigate)
            } else result
        } catch (e: Exception) {
            Result("ERROR: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun runPerf(args: List<String>): Result {
        val sub = args.firstOrNull()?.lowercase()
        return when (sub) {
            null, "status", "show" -> {
                val p = LatencyProfiles.active
                Result(
                    buildString {
                        appendLine("═══ LATENCY PROFILE (${p.name}) ═══")
                        appendLine("port probe timeout : ${p.portProbeTimeoutMs}ms")
                        appendLine("max concurrent     : ${p.maxConcurrentProbes}")
                        appendLine("guardian radio     : ${p.guardianRadioMs}ms")
                        appendLine("guardian gateway   : ${p.guardianGatewayMs}ms")
                        appendLine("ble scan           : ${p.bleScanMs}ms")
                        appendLine("http connect/read  : ${p.httpConnectMs}/${p.httpReadMs}ms")
                        appendLine("Velora lessons: budgets · coalesce UI · bounded parallel")
                        appendLine("Switch: perf ultra | perf balanced")
                    }.trimEnd()
                )
            }
            "ultra" -> {
                LatencyProfiles.useUltra()
                Result("Latency profile → ultra (Fire field default)")
            }
            "balanced", "lab" -> {
                LatencyProfiles.useBalanced()
                Result("Latency profile → balanced (lab / thorough)")
            }
            else -> Result("perf [status|ultra|balanced]")
        }
    }

    private fun runPrivacy(args: List<String>): Result {
        val sub = args.firstOrNull()?.lowercase()
        return when (sub) {
            null, "status", "show" -> Result(PrivacySettings.summary(context))
            "scrub" -> {
                val v = !PrivacySettings.scrubPii(context)
                PrivacySettings.setScrubPii(context, v)
                Result("scrub PII → $v")
            }
            "encrypt" -> {
                val v = !PrivacySettings.encryptExports(context)
                PrivacySettings.setEncryptExports(context, v)
                Result("encrypt exports → $v")
            }
            "home" -> {
                val v = !PrivacySettings.allowHomeCalls(context)
                PrivacySettings.setAllowHomeCalls(context, v)
                Result("allow call-home → $v  (HTTPS required for public hosts)")
            }
            "wipe" -> {
                SecureStore.clearAll(context)
                Result("Secure prefs wiped. Restart app for VALHALLA gate.")
            }
            "check" -> {
                val url = args.getOrNull(1) ?: return Result("Usage: privacy check <url>")
                when (val d = HomeCallPolicy.evaluate(context, url)) {
                    is HomeCallPolicy.Decision.Allowed -> Result("ALLOWED: $url")
                    is HomeCallPolicy.Decision.Denied -> Result("DENIED: ${d.reason}")
                }
            }
            else -> Result("privacy [status|scrub|encrypt|home|wipe|check <url>]")
        }
    }

    private fun tokenize(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQ = false
        for (c in line) {
            when {
                c == '"' -> inQ = !inQ
                c.isWhitespace() && !inQ -> {
                    if (cur.isNotEmpty()) {
                        out += cur.toString()
                        cur.clear()
                    }
                }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun statusBlock(): String = buildString {
        appendLine("═══ HALEHOUND-FIRE STATUS ═══")
        appendLine(DeviceProfile.hostBanner(context))
        appendLine()
        appendLine(DeviceProfile.hostInfoBlock(context))
        appendLine()
        appendLine("App ${BuildConfig.VERSION_NAME} · Blue Team · local engines ready")
        appendLine("Type  agent  for Grok Build command protocol")
    }

    private suspend fun runHarden(): Result = withContext(Dispatchers.IO) {
        val r = HardeningEngine(context).runFullAudit()
        Result(
            buildString {
                appendLine("═══ HARDEN AUDIT ═══")
                appendLine("SCORE ${r.score}/100 · ${r.grade}")
                appendLine(r.wifiLine)
                appendLine("${r.dnsLine} · gw=${r.gateway} ip=${r.localIp}")
                appendLine("--- findings ---")
                r.findings.take(12).forEach { appendLine("• $it") }
                appendLine("--- open danger ports ---")
                val open = r.portHits.filter { it.open }
                if (open.isEmpty()) appendLine("(none)")
                else open.take(15).forEach {
                    appendLine("${it.host}:${it.port} ${it.name} [${it.risk}]")
                }
                appendLine("Tip: firewall  → write Windows netsh .bat")
            }.trimEnd(),
            navigate = null
        )
    }

    private suspend fun runFirewallBat(): Result = withContext(Dispatchers.IO) {
        val audit = try {
            HardeningEngine(context).runFullAudit()
        } catch (_: Exception) {
            null
        }
        val open = audit?.portHits?.filter { it.open }?.map { it.port }?.distinct() ?: emptyList()
        val bat = FirewallBatchGenerator.generate(
            FirewallBatchGenerator.Options(
                includeBaselineBlocks = true,
                includeGamingAllowHints = true,
                openPortsFromAudit = open,
                reportScore = audit?.score,
                reportGrade = audit?.grade
            )
        )
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val out = File(dir, "HHF_firewall_harden.bat")
        out.writeText(bat)
        File(dir, "HHF_firewall_undo.bat").writeText(FirewallBatchGenerator.undoBatch())
        var encLine = ""
        if (PrivacySettings.encryptExports(context)) {
            val enc = File(dir, "HHF_firewall_harden.bat.hhf")
            ExportCrypto.encryptToFile(context, bat.toByteArray(Charsets.UTF_8), enc)
            encLine = enc.absolutePath
        }
        Result(
            buildString {
                appendLine("═══ WINDOWS FIREWALL .BAT ═══")
                appendLine("Wrote netsh block script (cannot run netsh on Fire).")
                appendLine(out.absolutePath)
                if (encLine.isNotBlank()) appendLine("encrypted: $encLine")
                appendLine("scrub=${PrivacySettings.scrubPii(context)} encrypt=${PrivacySettings.encryptExports(context)}")
                appendLine("call-home=${PrivacySettings.allowHomeCalls(context)} (default off)")
                appendLine()
                appendLine("Sensei PC (rare):")
                appendLine("  adb pull \"${out.absolutePath}\" .")
                appendLine("  Run as administrator")
                appendLine("Undo: tools\\hhf-firewall-undo.bat")
                if (open.isNotEmpty()) {
                    appendLine()
                    appendLine("Audit open ports folded in: ${open.joinToString(",")}")
                }
            }.trimEnd()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun runWifi(): Result = withContext(Dispatchers.IO) {
        try {
            val rows = WifiSurvey(context).scanOnce()
            Result(
                buildString {
                    appendLine("═══ WIFI SURVEY (${rows.size}) ═══")
                    rows.take(20).forEach {
                        appendLine("${it.rssi}dBm  ${it.line1}")
                        appendLine("   ${it.bssid} ch${it.channel} ${it.security}")
                    }
                    if (rows.size > 20) appendLine("… ${rows.size - 20} more")
                }.trimEnd()
            )
        } catch (e: Exception) {
            Result("WIFI scan failed: ${e.message}\nGrant location permission if prompted.")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runBle(): Result = withContext(Dispatchers.IO) {
        try {
            val rows = BleSurvey(context).scan(6_000L)
            Result(
                buildString {
                    appendLine("═══ BLE SURVEY (${rows.size}) ═══")
                    rows.take(20).forEach {
                        appendLine("${it.rssi}dBm  ${it.line1}")
                        appendLine("   ${it.address}")
                    }
                }.trimEnd()
            )
        } catch (e: Exception) {
            Result("BLE scan failed: ${e.message}")
        }
    }

    /**
     * CYD companion ops:
     *   cyd | cyd status | cyd telemetry  — full HTTP surface probe + telemetry snapshot
     *   cyd loot                          — loot hints from live probe
     *   cyd pull                          — pull loot into Fire vault
     *   cyd discover                      — quick softAP host scan only
     *   cyd vault                         — list local vault files
     *   cyd <url>                         — probe explicit base URL
     */
    private suspend fun runCyd(args: List<String>): Result {
        val sub = args.firstOrNull()?.lowercase(Locale.US)
        return when (sub) {
            null, "status", "telemetry", "probe", "tel" -> runCydTelemetry(null)
            "loot", "files" -> runCydLoot()
            "pull", "offload" -> runCydPull()
            "vault", "local" -> runCydVault()
            "discover", "scan", "find" -> runCydDiscover()
            "help", "?" -> Result(
                """
                ═══ CYD COMMANDS ═══
                cyd / cyd status   live telemetry probe
                cyd loot           list loot hints
                cyd pull           pull loot → Fire vault
                cyd vault          list local vault
                cyd discover       softAP host scan
                cyd <url>          probe explicit base
                open cyd           open CYD UI tab
                """.trimIndent()
            )
            else -> {
                // Treat first arg as host/URL if it looks like one
                if (sub.contains('.') || sub.startsWith("http") || sub.startsWith("192") || sub.startsWith("10.")) {
                    runCydTelemetry(args.first())
                } else {
                    Result("Unknown cyd subcommand: $sub\nTry: cyd help")
                }
            }
        }
    }

    private fun cydBaseFromStore(): String {
        val saved = SecureStore.getString(context, "cyd_base_url", "http://192.168.4.1")
        return saved.ifBlank { "http://192.168.4.1" }
    }

    private suspend fun runCydDiscover(): Result {
        val hits = CydDiscovery.discover()
        return Result(
            buildString {
                appendLine("═══ CYD DISCOVER ═══")
                if (hits.isEmpty()) {
                    appendLine("No softAP HTTP endpoints found.")
                    appendLine("Join CYD AP or same LAN, then: cyd status")
                } else {
                    hits.forEach { appendLine("${it.url}  ${it.title}  ${it.latencyMs}ms") }
                    appendLine("Tip: cyd status  |  cyd loot  |  cyd pull")
                }
            }.trimEnd(),
            navigate = if (hits.isNotEmpty()) "cyd" else null
        )
    }

    private suspend fun runCydTelemetry(explicitBase: String?): Result {
        val base = explicitBase?.let { CydLinkClient.normalizeBase(it) } ?: cydBaseFromStore()
        val tel = if (explicitBase == null) {
            // Prefer saved host; fall back to auto-discover
            val primary = CydLinkClient.connectAndProbe(base, context)
            if (primary.online) primary
            else CydLinkClient.autoDiscoverTelemetry(context).firstOrNull() ?: primary
        } else {
            CydLinkClient.connectAndProbe(base, context)
        }
        SecureStore.putString(context, "cyd_base_url", tel.baseUrl)
        CydLootVault.writeTelemetrySnapshot(context, tel)
        return Result(
            buildString {
                appendLine("═══ CYD TELEMETRY ═══")
                appendLine(if (tel.online) "● ONLINE" else "○ OFFLINE")
                appendLine("base     : ${tel.baseUrl}")
                appendLine("latency  : ${tel.latencyMs} ms")
                appendLine("title    : ${tel.title.take(100)}")
                appendLine("heap     : ${tel.heapHint ?: "—"}")
                appendLine("sd/free  : ${tel.freeSdHint ?: "—"}")
                appendLine("mode     : ${tel.modeHint ?: "—"}")
                appendLine("paths    : ${tel.rawPaths.size} live")
                appendLine("loot     : ${tel.lootHints.size} hint(s)")
                if (tel.rawPaths.isNotEmpty()) {
                    appendLine("--- HTTP ---")
                    tel.rawPaths.entries.sortedBy { it.key }.take(20).forEach { (k, v) ->
                        appendLine("  $v  $k")
                    }
                }
                tel.notes.forEach { appendLine("· $it") }
                if (tel.online && tel.lootHints.isNotEmpty()) {
                    appendLine("Next: cyd loot  |  cyd pull")
                } else if (!tel.online) {
                    appendLine("Join CYD softAP (often 192.168.4.1) then retry.")
                }
            }.trimEnd(),
            navigate = "cyd"
        )
    }

    private suspend fun runCydLoot(): Result {
        val base = cydBaseFromStore()
        val tel = CydLinkClient.connectAndProbe(base, context)
        SecureStore.putString(context, "cyd_base_url", tel.baseUrl)
        return Result(
            buildString {
                appendLine("═══ CYD LOOT HINTS ═══")
                appendLine("base: ${tel.baseUrl}  online=${tel.online}")
                if (!tel.online) {
                    appendLine("CYD offline — cannot list loot.")
                } else if (tel.lootHints.isEmpty()) {
                    appendLine("No filenames scraped yet.")
                    appendLine("Firmware must serve /sd, /loot, or directory HTML/JSON.")
                } else {
                    tel.lootHints.take(40).forEach {
                        appendLine("[${it.category}] ${it.name}")
                        appendLine("   ${it.path}")
                    }
                    if (tel.lootHints.size > 40) appendLine("… ${tel.lootHints.size - 40} more")
                    appendLine("Pull: cyd pull")
                }
            }.trimEnd(),
            navigate = "cyd"
        )
    }

    private suspend fun runCydPull(): Result {
        val base = cydBaseFromStore()
        val tel = CydLinkClient.connectAndProbe(base, context)
        if (!tel.online) {
            return Result("CYD offline — join softAP / set host, then: cyd pull", navigate = "cyd")
        }
        CydLootVault.writeTelemetrySnapshot(context, tel)
        if (tel.lootHints.isEmpty()) {
            return Result(
                "No loot hints to pull. Telemetry snapshot saved to vault.\n" +
                    CydLootVault.vaultRoot(context).absolutePath,
                navigate = "cyd"
            )
        }
        val files = CydLootVault.pullAll(context, tel.baseUrl, tel.lootHints, maxFiles = 24)
        return Result(
            buildString {
                appendLine("═══ CYD LOOT PULL ═══")
                appendLine("pulled ${files.size} file(s) (max 24)")
                appendLine(CydLootVault.vaultRoot(context).absolutePath)
                files.take(20).forEach { appendLine("  ${it.name}  ${it.length()}b") }
                if (files.size > 20) appendLine("… ${files.size - 20} more")
                appendLine("List: cyd vault")
            }.trimEnd(),
            navigate = "cyd"
        )
    }

    private fun runCydVault(): Result {
        val files = CydLootVault.listLocal(context)
        return Result(
            buildString {
                appendLine("═══ FIRE LOOT VAULT ═══")
                appendLine(CydLootVault.vaultRoot(context).absolutePath)
                if (files.isEmpty()) appendLine("(empty — cyd pull when linked)")
                else files.take(30).forEach {
                    appendLine("${it.name}  ${it.length()}b")
                }
            }.trimEnd(),
            navigate = "cyd"
        )
    }

    private suspend fun runPorts(args: List<String>): Result = withContext(Dispatchers.IO) {
        val host = args.firstOrNull() ?: return@withContext Result("Usage: ports <host>\nExample: ports 192.168.1.1")
        val ports = HardeningKnowledge.dangerPorts.map { it.port }
        val lines = ports.map { p ->
            val open = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, p), 300)
                    true
                }
            } catch (_: Exception) {
                false
            }
            val meta = HardeningKnowledge.portByNumber(p)
            if (open) "OPEN  $p  ${meta?.name ?: ""}  [${meta?.risk}]" else null
        }.filterNotNull()
        Result(
            buildString {
                appendLine("═══ PORTS $host ═══")
                if (lines.isEmpty()) appendLine("No danger-set ports open (or host down).")
                else lines.forEach { appendLine(it) }
            }.trimEnd()
        )
    }

    private suspend fun runPing(args: List<String>): Result = withContext(Dispatchers.IO) {
        val host = args.firstOrNull() ?: return@withContext Result("Usage: ping <host>")
        // TCP connect fallback — ICMP often blocked for apps
        val ports = listOf(80, 443, 53)
        val sb = StringBuilder("═══ REACH $host ═══\n")
        var any = false
        for (p in ports) {
            val start = System.currentTimeMillis()
            val ok = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, p), 800)
                    true
                }
            } catch (_: Exception) {
                false
            }
            if (ok) {
                any = true
                sb.appendLine("tcp/$p  ok  ${System.currentTimeMillis() - start}ms")
            }
        }
        if (!any) sb.appendLine("No TCP 80/443/53 response (ICMP not used).")
        // try system ping if present
        val sys = safeExec(listOf("ping", "-c", "1", "-W", "1", host), 3000)
        if (sys.isNotBlank()) {
            sb.appendLine("--- system ping ---")
            sb.appendLine(sys.lines().take(6).joinToString("\n"))
        }
        Result(sb.toString().trimEnd())
    }

    private fun dnsHelp(): String = buildString {
        appendLine("═══ DNS PLAYBOOK ═══")
        HardeningKnowledge.dnsProfiles.forEach {
            appendLine("${it.name}")
            appendLine("  ${it.primary} / ${it.secondary}")
            appendLine("  ${it.note}")
        }
        appendLine()
        appendLine("On Fire: Settings → Network → Private DNS")
        appendLine("  1dot1dot1dot1.cloudflare-dns.com")
        appendLine("  dns.quad9.net")
        appendLine("Or: open chips → HARDEN → DNS SETTINGS")
    }

    private suspend fun runGetprop(args: List<String>): Result = withContext(Dispatchers.IO) {
        val key = args.firstOrNull()
        if (key.isNullOrBlank()) {
            val interesting = listOf(
                "ro.product.model", "ro.build.version.release", "ro.build.version.fireos",
                "ro.serialno", "wifi.interface"
            )
            val sb = StringBuilder("═══ GETPROP ═══\n")
            interesting.forEach { k ->
                val v = safeExec(listOf("getprop", k), 1500).trim()
                sb.appendLine("$k=$v")
            }
            sb.appendLine("Usage: getprop <key>")
            return@withContext Result(sb.toString().trimEnd())
        }
        val v = safeExec(listOf("getprop", key), 2000).trim()
        Result("$key=$v")
    }

    private suspend fun runSafeShell(args: List<String>): Result = withContext(Dispatchers.IO) {
        if (args.isEmpty()) {
            return@withContext Result(
                "Safe shell allowlist only:\n" +
                    "  sh id | sh uname | sh getprop <k> | sh ip addr | sh ip route\n" +
                    "No freeform root. Prefer: status harden wifi cyd"
            )
        }
        val head = args[0].lowercase(Locale.US)
        val allowed = setOf("id", "uname", "getprop", "ip", "date", "echo")
        if (head !in allowed) {
            return@withContext Result("Blocked: $head (not in allowlist). Type: sh")
        }
        // reconstruct limited argv
        val argv = when (head) {
            "ip" -> {
                val sub = args.getOrNull(1)?.lowercase(Locale.US)
                if (sub !in setOf("addr", "route", "link")) {
                    return@withContext Result("Allowed: sh ip addr|route|link")
                }
                listOf("ip") + args.drop(1).take(3)
            }
            "getprop" -> listOf("getprop") + listOfNotNull(args.getOrNull(1)).take(1)
            "echo" -> listOf("echo") + args.drop(1).take(8)
            "uname" -> listOf("uname", "-a")
            "date" -> listOf("date")
            "id" -> listOf("id")
            else -> listOf(head)
        }
        val out = safeExec(argv, 4000)
        Result(out.ifBlank { "(no output)" })
    }

    private fun safeExec(argv: List<String>, timeoutMs: Long): String {
        return try {
            val pb = ProcessBuilder(argv)
                .redirectErrorStream(true)
            val p = pb.start()
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val deadline = System.currentTimeMillis() + timeoutMs
            val sb = StringBuilder()
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    sb.appendLine(line)
                    if (sb.length > 8000) break
                } else if (!p.isAlive) {
                    while (reader.ready()) {
                        sb.appendLine(reader.readLine() ?: break)
                    }
                    break
                } else {
                    Thread.sleep(20)
                }
            }
            if (p.isAlive) p.destroy()
            sb.toString().trim()
        } catch (e: Exception) {
            "(exec failed: ${e.message})"
        }
    }

    companion object {
        val QUICK_CHIPS = listOf(
            "help", "status", "harden", "firewall", "privacy", "perf", "wifi", "ble", "cyd", "guard", "dns", "agent", "clear"
        )

        private val HELP = """
            ╔══════════════════════════════════════╗
            ║  HHF OPS TERMINAL — easy mode        ║
            ║  Humans tap chips · Agents type cmds ║
            ╚══════════════════════════════════════╝

            CORE
              help          this screen
              agent         Grok Build protocol
              status        device + app info
              clear         wipe transcript

            ARSENAL (runs on this tablet)
              harden        full harden audit + score
              firewall      gen Windows netsh .bat (PC apply)
              wifi          passive Wi‑Fi survey
              ble           BLE advertisement survey
              cyd           CYD telemetry (see cyd help)
              cyd loot      list loot hints from CYD
              cyd pull      offload loot → Fire vault
              cyd vault     list local vault files
              guard         open local Guardian
              ports <ip>    danger-port probe host
              ping <host>   TCP reachability
              dns           Private DNS playbook

            NAV
              open harden|guard|wifi|cyd|ble|about|home

            SAFE SHELL
              sh id | sh uname | sh getprop [key]
              sh ip addr | sh ip route

            Dig it: keep living — Grok handles the glass.
        """.trimIndent()

        private val AGENT_PROTOCOL = """
            ═══ GROK BUILD / AGENT PROTOCOL ═══
            Goal: agents work behind the scenes; humans chill.

            PREFERRED COMMANDS (deterministic)
              status
              harden
              wifi
              cyd status
              cyd loot
              cyd pull
              cyd vault
              ports <gateway-or-host>
              ping <host>
              getprop ro.product.model

            NAV (UI)
              open harden
              open guard
              open cyd

            OUTPUT
              Plain text blocks. SCORE lines for harden.
              OPEN host:port for findings.
              CYD: ONLINE/OFFLINE + loot count + vault paths.

            WINDOWS SIDE (laptop, optional)
              tools\grok-fire-ops.ps1 install|launch|log|doctor

            DO NOT
              Request freeform root shell
              Assume Npcap on Fire
              Run offensive TX from this terminal

            ETHICS: authorized / Blue Team only.
        """.trimIndent()

        private val CHIPS_HELP = "Chips: " + QUICK_CHIPS.joinToString(" · ")
    }
}
