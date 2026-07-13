package com.halehoundforge.fire.hardening

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a Windows .bat that applies netsh advfirewall rules.
 * Fire cannot run netsh — we generate the script for the PC (echo-batch ship).
 *
 * Sources: Secure Firewall Ports.bat, secure_gaming_firewall.ps1, PERSEUS manifest.
 */
object FirewallBatchGenerator {

    data class Options(
        val includeBaselineBlocks: Boolean = true,
        val includeGamingAllowHints: Boolean = false,
        /** Ports discovered OPEN during Fire audit — extra emphasis rules */
        val openPortsFromAudit: List<Int> = emptyList(),
        val reportScore: Int? = null,
        val reportGrade: String? = null
    )

    fun generate(options: Options = Options()): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()

        sb.appendLine("@echo off")
        sb.appendLine("setlocal EnableExtensions")
        sb.appendLine("title HaleHound Fire — Windows Firewall Harden")
        sb.appendLine("color 0A")
        sb.appendLine("echo ============================================================")
        sb.appendLine("echo   HALEHOUND-FIRE  WINDOWS FIREWALL HARDEN")
        sb.appendLine("echo   Generated: $ts")
        if (options.reportScore != null) {
            sb.appendLine("echo   Fire audit score: ${options.reportScore}/100 ${options.reportGrade ?: ""}")
        }
        sb.appendLine("echo   Blue Team / authorized host only")
        sb.appendLine("echo ============================================================")
        sb.appendLine("echo.")
        sb.appendLine("net session >nul 2>&1")
        sb.appendLine("if errorlevel 1 (")
        sb.appendLine("  echo [!] Run as Administrator.")
        sb.appendLine("  echo     Right-click this .bat -^> Run as administrator")
        sb.appendLine("  pause")
        sb.appendLine("  exit /b 1")
        sb.appendLine(")")
        sb.appendLine("echo [+] Admin OK — applying inbound block rules...")
        sb.appendLine("echo.")

        // Helper macro as labels would be verbose — inline each rule with remove-then-add
        fun blockRule(name: String, proto: String, ports: String, comment: String) {
            val safe = name.replace("\"", "'")
            sb.appendLine("echo [!] BLOCK $proto $ports  ^($comment^)")
            sb.appendLine("netsh advfirewall firewall delete rule name=\"$safe\" >nul 2>&1")
            sb.appendLine(
                "netsh advfirewall firewall add rule name=\"$safe\" dir=in action=block protocol=$proto " +
                    "localport=$ports enable=yes profile=any " +
                    "description=\"HaleHound Fire harden · $comment\""
            )
            sb.appendLine("if errorlevel 1 (echo     [x] failed: $safe) else (echo     [+] ok: $safe)")
            sb.appendLine()
        }

        if (options.includeBaselineBlocks) {
            sb.appendLine("echo --- BASELINE ^(Secure Firewall Ports / gaming firewall / PERSEUS^) ---")
            blockRule("HHF-BLOCK Telnet TCP-23", "TCP", "23", "cleartext remote")
            blockRule("HHF-BLOCK FTP TCP-21", "TCP", "21", "legacy FTP")
            blockRule("HHF-BLOCK SMB TCP-445", "TCP", "445", "Anti-WannaCry SMB")
            blockRule("HHF-BLOCK NetBIOS TCP-139", "TCP", "139", "NetBIOS session")
            blockRule("HHF-BLOCK NetBIOS TCP-137-138", "TCP", "137-138", "NetBIOS name/datagram")
            blockRule("HHF-BLOCK NetBIOS UDP-137-139", "UDP", "137-139", "NetBIOS UDP")
            blockRule("HHF-BLOCK SMB UDP-445", "UDP", "445", "SMB UDP")
            blockRule("HHF-BLOCK RDP TCP-3389", "TCP", "3389", "RDP ransomware vector")
            blockRule("HHF-BLOCK VNC TCP-5900", "TCP", "5900", "VNC remote")
            blockRule("HHF-BLOCK RPC TCP-135", "TCP", "135", "Windows RPC")
        }

        val auditPorts = options.openPortsFromAudit.distinct().sorted()
        if (auditPorts.isNotEmpty()) {
            sb.appendLine("echo --- FROM FIRE AUDIT ^(open on scanned hosts^) ---")
            for (p in auditPorts) {
                val meta = HardeningKnowledge.portByNumber(p)
                val label = meta?.name ?: "port"
                // skip if already in baseline core set
                val core = setOf(21, 23, 135, 137, 138, 139, 445, 3389, 5900)
                if (p in core && options.includeBaselineBlocks) {
                    sb.appendLine("echo [=] audit hit $p $label already in baseline")
                    continue
                }
                blockRule(
                    "HHF-AUDIT BLOCK TCP-$p $label",
                    "TCP",
                    p.toString(),
                    "Fire audit open · ${meta?.why ?: "danger port"}"
                )
            }
        }

        if (options.includeGamingAllowHints) {
            sb.appendLine("echo --- OPTIONAL GAME ALLOWS ^(commented — uncomment if needed^) ---")
            sb.appendLine("echo Rem secure_gaming_firewall.ps1 style allows — disabled by default for harden posture")
            sb.appendLine("REM netsh advfirewall firewall add rule name=\"HHF-ALLOW Web TCP-80,443\" dir=in action=allow protocol=TCP localport=80,443")
            sb.appendLine("REM netsh advfirewall firewall add rule name=\"HHF-ALLOW QUIC UDP-443\" dir=in action=allow protocol=UDP localport=443")
            sb.appendLine()
        }

        sb.appendLine("echo.")
        sb.appendLine("echo [SUCCESS] HHF firewall rules applied.")
        sb.appendLine("echo Remove via: Windows Defender Firewall ^> Advanced ^> Inbound Rules ^> HHF-*")
        sb.appendLine("echo Or run: tools\\hhf-firewall-undo.bat on the PC repo")
        sb.appendLine("echo.")
        sb.appendLine("pause")
        sb.appendLine("endlocal")

        return sb.toString().replace("\n", "\r\n")
    }

    fun undoBatch(): String {
        val names = listOf(
            "HHF-BLOCK Telnet TCP-23",
            "HHF-BLOCK FTP TCP-21",
            "HHF-BLOCK SMB TCP-445",
            "HHF-BLOCK NetBIOS TCP-139",
            "HHF-BLOCK NetBIOS TCP-137-138",
            "HHF-BLOCK NetBIOS UDP-137-139",
            "HHF-BLOCK SMB UDP-445",
            "HHF-BLOCK RDP TCP-3389",
            "HHF-BLOCK VNC TCP-5900",
            "HHF-BLOCK RPC TCP-135"
        )
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        sb.appendLine("setlocal")
        sb.appendLine("net session >nul 2>&1 || (echo Run as Admin & pause & exit /b 1)")
        sb.appendLine("echo Removing HHF-* baseline rules...")
        for (n in names) {
            sb.appendLine("netsh advfirewall firewall delete rule name=\"$n\" >nul 2>&1")
            sb.appendLine("echo removed: $n")
        }
        sb.appendLine("echo Also delete any HHF-AUDIT* rules in the firewall MMC if present.")
        sb.appendLine("pause")
        sb.appendLine("endlocal")
        return sb.toString().replace("\n", "\r\n")
    }
}
