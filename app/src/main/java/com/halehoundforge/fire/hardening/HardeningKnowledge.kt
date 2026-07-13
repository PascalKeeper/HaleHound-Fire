package com.halehoundforge.fire.hardening

/**
 * Knowledge base scraped from local Windows toolkit (Joseph Peransi / PascalKeeper):
 * - Desktop\Secure Firewall Ports.bat
 * - Documents\secure_gaming_firewall.ps1
 * - Documents\ultimate_net_optimizer.bat
 * - Documents\HardenServices.ps1
 * - SecuritySentinel_Pro.ps1 / SecurityMonitor.ps1
 * - Project PERSEUS SecurityAutomation HARDENING_MANIFEST
 * - WifiGuard / WiFiGuardianKiller
 * - NetworkMonitor_v2 predictive advice
 *
 * On Fire OS we cannot apply Windows firewall rules — we audit, probe, score, and guide.
 */
object HardeningKnowledge {

    data class DangerPort(
        val port: Int,
        val proto: String,
        val name: String,
        val risk: Risk,
        val why: String,
        val source: String
    )

    enum class Risk { CRITICAL, HIGH, MEDIUM, LOW, INFO }

    data class ChecklistItem(
        val id: String,
        val title: String,
        val detail: String,
        val risk: Risk,
        val category: String,
        val source: String
    )

    data class DnsProfile(
        val name: String,
        val primary: String,
        val secondary: String,
        val note: String
    )

    /** Inbound ports from Secure Firewall Ports.bat + secure_gaming_firewall + PERSEUS manifest */
    val dangerPorts: List<DangerPort> = listOf(
        DangerPort(21, "TCP", "FTP", Risk.MEDIUM, "Unencrypted file transfer — legacy malware path", "Secure Firewall Ports.bat"),
        DangerPort(22, "TCP", "SSH", Risk.HIGH, "Remote shell — lock down or key-only if needed", "Perseus hardener"),
        DangerPort(23, "TCP", "Telnet", Risk.HIGH, "Cleartext remote access — always block", "Secure Firewall Ports.bat / secure_gaming_firewall.ps1"),
        DangerPort(80, "TCP", "HTTP", Risk.LOW, "Web — OK if intentional; prefer HTTPS", "secure_gaming_firewall allow list"),
        DangerPort(135, "TCP", "RPC", Risk.HIGH, "Windows RPC surface", "Hardening knowledge"),
        DangerPort(139, "TCP", "NetBIOS", Risk.CRITICAL, "File/print legacy — worm path", "Secure Firewall / secure_gaming_firewall"),
        DangerPort(445, "TCP", "SMB", Risk.CRITICAL, "WannaCry-class worm vector — block WAN", "PERSEUS block_smb / Secure Firewall"),
        DangerPort(3389, "TCP", "RDP", Risk.CRITICAL, "#1 ransomware entry if exposed", "SecuritySentinel / PERSEUS block_rdp"),
        DangerPort(5900, "TCP", "VNC", Risk.HIGH, "Remote desktop often weak auth", "Secure Firewall Ports.bat"),
        DangerPort(8080, "TCP", "HTTP-ALT", Risk.MEDIUM, "Common IoT/camera panels (your IP cam notes)", "secure_gaming_firewall camera rule"),
        DangerPort(8443, "TCP", "HTTPS-ALT", Risk.MEDIUM, "Alt admin UIs on routers/NAS", "Hardening knowledge"),
        DangerPort(9100, "TCP", "Print", Risk.MEDIUM, "JetDirect — often unauth on LAN", "Hardening knowledge"),
        DangerPort(5555, "TCP", "ADB", Risk.HIGH, "Android Debug if exposed on Wi‑Fi", "Fire companion context"),
        DangerPort(62078, "TCP", "iOS lockdownd", Risk.LOW, "Mobile management surface", "Hardening knowledge")
    )

    val dnsProfiles: List<DnsProfile> = listOf(
        DnsProfile("Cloudflare", "1.1.1.1", "1.0.0.1", "From ultimate_net_optimizer.bat — speed + privacy"),
        DnsProfile("Cloudflare malware block", "1.1.1.2", "1.0.0.2", "Blocks known malicious domains"),
        DnsProfile("Quad9", "9.9.9.9", "149.112.112.112", "Security-focused recursive DNS"),
        DnsProfile("Google", "8.8.8.8", "8.8.4.4", "Reliable public DNS")
    )

    /** Operator checklist — things Fire can teach / verify partially */
    val checklist: List<ChecklistItem> = listOf(
        ChecklistItem(
            "wifi_encryption",
            "Wi‑Fi encryption WPA2/WPA3",
            "Never use OPEN or WEP. Prefer WPA3-SAE or WPA2-AES.",
            Risk.CRITICAL,
            "Wi‑Fi",
            "Network posture / lab standard"
        ),
        ChecklistItem(
            "pmf",
            "Enable PMF / 802.11w (Protected Management Frames)",
            "Stops classic deauth kicks when clients+AP support it. From NetworkMonitor mitigation advice.",
            Risk.HIGH,
            "Wi‑Fi",
            "NetworkMonitor_v2 / Guardian"
        ),
        ChecklistItem(
            "wps_off",
            "Disable WPS on router",
            "WPS PIN is breakable — turn off in AP admin.",
            Risk.HIGH,
            "Router",
            "Hardening playbook"
        ),
        ChecklistItem(
            "router_admin",
            "Change default router admin password",
            "Default creds = instant LAN takeover.",
            Risk.CRITICAL,
            "Router",
            "PERSEUS IoT recon awareness"
        ),
        ChecklistItem(
            "guest_wifi",
            "Isolate IoT on guest VLAN/SSID",
            "Cameras/printers off main LAN. Your secure_gaming camera rule idea — restrict by IP.",
            Risk.HIGH,
            "LAN",
            "secure_gaming_firewall.ps1 camera pattern"
        ),
        ChecklistItem(
            "block_smb_wan",
            "Never expose SMB 445 to the Internet",
            "From Secure Firewall Ports + PERSEUS block_smb (Anti-WannaCry).",
            Risk.CRITICAL,
            "Ports",
            "Secure Firewall Ports.bat"
        ),
        ChecklistItem(
            "block_rdp_wan",
            "Never expose RDP 3389 without VPN + MFA",
            "SecuritySentinel trusted-IP model — RDP is #1 ransomware vector.",
            Risk.CRITICAL,
            "Ports",
            "SecuritySentinel_Pro.ps1 / PERSEUS"
        ),
        ChecklistItem(
            "no_telnet_ftp",
            "Kill Telnet 23 / cleartext FTP 21",
            "Legacy remote protocols from Secure Firewall Ports.bat.",
            Risk.HIGH,
            "Ports",
            "Secure Firewall Ports.bat"
        ),
        ChecklistItem(
            "private_dns",
            "Use Private DNS / trusted resolvers",
            "ultimate_net_optimizer Cloudflare 1.1.1.1 path — on Android use Private DNS (DoT).",
            Risk.MEDIUM,
            "DNS",
            "ultimate_net_optimizer.bat"
        ),
        ChecklistItem(
            "ssid_lock",
            "SSID lock to trusted network",
            "WifiGuard / WiFiGuardianKiller — don't auto-join evil twins.",
            Risk.HIGH,
            "Wi‑Fi",
            "WifiGuard.ps1"
        ),
        ChecklistItem(
            "upnp_off",
            "Disable UPnP on router",
            "HardenServices disables upnphost on Windows; same idea on the AP.",
            Risk.HIGH,
            "Router",
            "HardenServices.ps1"
        ),
        ChecklistItem(
            "admin_https",
            "Router admin only over HTTPS + LAN",
            "No remote management from WAN.",
            Risk.HIGH,
            "Router",
            "Hardening playbook"
        ),
        ChecklistItem(
            "firmware",
            "Router firmware current",
            "Patch WAN-facing CPE regularly.",
            Risk.MEDIUM,
            "Router",
            "Hardening playbook"
        ),
        ChecklistItem(
            "adb_wifi",
            "ADB not listening on Wi‑Fi without need",
            "Port 5555 — Fire debug surface. Keep USB-only when possible.",
            Risk.HIGH,
            "Device",
            "Fire companion context"
        ),
        ChecklistItem(
            "guardian_on",
            "Run local Wi‑Fi Guardian while mobile",
            "Disconnect/RSSI storm sensors on this tablet — no laptop.",
            Risk.INFO,
            "Ops",
            "HaleHound Fire Guardian v0.5"
        )
    )

    /** TCP optim “education” from ultimate_net_optimizer — not all apply to Android stack */
    val optimNotes: List<String> = listOf(
        "Flush DNS when poisoned redirects suspected (ultimate_net_optimizer / PERSEUS flush_dns).",
        "Prefer 5 GHz / 6 GHz over crowded 2.4 GHz for stability (NetworkMonitor mitigation).",
        "Disable WPS; enable WPA3 or WPA2-AES + PMF where clients allow.",
        "Cloudflare 1.1.1.1 / Quad9 for recursive DNS privacy+speed.",
        "Do not expose 445/3389/23/21 on WAN — ever.",
        "IoT cameras: allow only specific LAN IPs (secure_gaming camera pattern).",
        "After incidents: power-cycle AP, rotate Wi‑Fi PSK, review DHCP leases."
    )

    fun portByNumber(port: Int): DangerPort? = dangerPorts.find { it.port == port }
}
