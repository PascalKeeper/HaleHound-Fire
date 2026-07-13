package com.halehoundforge.fire.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.halehoundforge.fire.databinding.FragmentHardenBinding
import com.halehoundforge.fire.hardening.FirewallBatchGenerator
import com.halehoundforge.fire.hardening.HardeningEngine
import com.halehoundforge.fire.hardening.HardeningKnowledge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-the-go network hardening console — knowledge from local Windows harden/optim stack.
 * Generates Windows netsh .bat for PC apply (Fire cannot run netsh itself).
 */
class HardenFragment : Fragment() {

    private var _binding: FragmentHardenBinding? = null
    private val binding get() = _binding!!
    private var lastReport: String = ""
    private var lastAudit: HardeningEngine.AuditResult? = null
    private var lastBatPath: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHardenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.optimBlock.text = buildString {
            appendLine("OPTIM / HARDEN NOTES (from your Windows kit)")
            HardeningKnowledge.optimNotes.forEach { appendLine("• $it") }
        }.trimEnd()

        binding.checklistBlock.text = buildString {
            appendLine("OPERATOR CHECKLIST (sources tagged)")
            HardeningKnowledge.checklist.take(8).forEach {
                appendLine("[${it.risk}] ${it.title}")
            }
            appendLine("… run audit for live status")
        }.trimEnd()

        binding.btnAudit.setOnClickListener { runAudit() }
        binding.btnCopyReport.setOnClickListener { copyReport() }
        binding.btnGenFirewallBat.setOnClickListener { genFirewallBat() }
        binding.btnWifiSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Cannot open Wi‑Fi settings", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnDnsSettings.setOnClickListener {
            val intents = listOf(
                Intent("android.settings.WIRELESS_SETTINGS"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                Intent(Settings.ACTION_WIFI_SETTINGS)
            )
            var ok = false
            for (i in intents) {
                try {
                    startActivity(i)
                    ok = true
                    break
                } catch (_: Exception) {
                }
            }
            if (!ok) Toast.makeText(requireContext(), "Open Settings → Network → Private DNS", Toast.LENGTH_LONG).show()
            else Toast.makeText(
                requireContext(),
                "Set Private DNS: 1dot1dot1dot1.cloudflare-dns.com or dns.quad9.net",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun runAudit() {
        binding.btnAudit.isEnabled = false
        binding.scoreBanner.text = "AUDITING…"
        binding.scoreBanner.setTextColor(Color.parseColor("#FFCC00"))
        binding.findingsBlock.text = "Probing gateway + this device against danger-port set\n(from Secure Firewall Ports / PERSEUS / gaming firewall)…"

        val extra = binding.extraHost.text?.toString()?.trim().orEmpty()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = HardeningEngine(requireContext().applicationContext)
                    .runFullAudit(extraHosts = if (extra.isBlank()) emptyList() else listOf(extra))
                lastAudit = result
                lastReport = result.reportText

                binding.scoreBanner.text = "SCORE ${result.score}/100 · ${result.grade}"
                binding.scoreBanner.setTextColor(
                    when {
                        result.score >= 75 -> Color.parseColor("#00FF41")
                        result.score >= 50 -> Color.parseColor("#FFCC00")
                        else -> Color.parseColor("#FF3344")
                    }
                )

                binding.postureBlock.text = buildString {
                    appendLine("POSTURE")
                    appendLine("Wi‑Fi  : ${result.wifiLine}")
                    appendLine("Local  : ${result.localIp}")
                    appendLine("Gateway: ${result.gateway}")
                    appendLine(result.dnsLine)
                }.trimEnd()

                binding.findingsBlock.text = buildString {
                    appendLine("FINDINGS (${result.findings.size})")
                    result.findings.forEach { appendLine("• $it") }
                }.trimEnd()

                binding.portsBlock.text = buildString {
                    appendLine("DANGER PORT PROBE (open only)")
                    val open = result.portHits.filter { it.open }
                    if (open.isEmpty()) appendLine("None of the hardened port set answered — good for those hosts.")
                    else open.forEach {
                        appendLine("${it.host}:${it.port}  ${it.name}  [${it.risk}]  ${it.latencyMs}ms")
                    }
                    appendLine()
                    appendLine("Closed-set size: ${result.portHits.count { !it.open }}/${result.portHits.size}")
                }.trimEnd()

                binding.checklistBlock.text = buildString {
                    appendLine("CHECKLIST STATUS")
                    result.checklistStatus.forEach { (item, st) ->
                        appendLine("[${item.risk}] ${item.title}")
                        appendLine("   → $st")
                    }
                }.trimEnd()

                binding.optimBlock.text = buildString {
                    appendLine("FIELD PLAYBOOK")
                    result.optimTips.forEach { appendLine("• $it") }
                    appendLine()
                    appendLine("DNS targets (ultimate_net_optimizer):")
                    HardeningKnowledge.dnsProfiles.forEach {
                        appendLine("  ${it.name}: ${it.primary} / ${it.secondary}")
                    }
                    appendLine()
                    appendLine("WINDOWS FIREWALL: tap GEN WINDOWS FIREWALL .BAT")
                    appendLine("  → saves netsh script · pull to PC · Run as Admin")
                }.trimEnd()

                Toast.makeText(requireContext(), "Audit complete · ${result.score}/100", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.scoreBanner.text = "AUDIT FAILED"
                binding.findingsBlock.text = "Error: ${e.message}"
                Toast.makeText(requireContext(), "Audit failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnAudit.isEnabled = true
            }
        }
    }

    private fun copyReport() {
        if (lastReport.isBlank()) {
            Toast.makeText(requireContext(), "Run an audit first", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("halehound-harden-report", lastReport))
        Toast.makeText(requireContext(), "Report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun genFirewallBat() {
        binding.btnGenFirewallBat.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val audit = lastAudit
                val openPorts = audit?.portHits?.filter { it.open }?.map { it.port }?.distinct() ?: emptyList()
                val bat = FirewallBatchGenerator.generate(
                    FirewallBatchGenerator.Options(
                        includeBaselineBlocks = true,
                        includeGamingAllowHints = true,
                        openPortsFromAudit = openPorts,
                        reportScore = audit?.score,
                        reportGrade = audit?.grade
                    )
                )

                val path = withContext(Dispatchers.IO) {
                    writeBatFiles(bat)
                }
                lastBatPath = path

                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("hhf-firewall.bat", bat))

                binding.firewallBatHint.text = buildString {
                    appendLine("SAVED + COPIED Windows netsh .bat")
                    appendLine(path)
                    appendLine()
                    appendLine("On PC (USB):")
                    appendLine("  adb pull \"$path\" .")
                    appendLine("  Right-click → Run as administrator")
                    appendLine()
                    appendLine("Or repo fallback:")
                    appendLine("  tools\\hhf-firewall-baseline.bat")
                    appendLine("Undo: tools\\hhf-firewall-undo.bat")
                }.trimEnd()

                Toast.makeText(requireContext(), "Firewall .bat saved + clipboard", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gen failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.firewallBatHint.text = "Gen failed: ${e.message}"
            } finally {
                binding.btnGenFirewallBat.isEnabled = true
            }
        }
    }

    private fun writeBatFiles(bat: String): String {
        val dir = requireContext().getExternalFilesDir(null)
            ?: requireContext().filesDir
        val out = File(dir, "HHF_firewall_harden.bat")
        out.writeText(bat)
        // also undo next to it for convenience
        File(dir, "HHF_firewall_undo.bat").writeText(FirewallBatchGenerator.undoBatch())
        return out.absolutePath
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
