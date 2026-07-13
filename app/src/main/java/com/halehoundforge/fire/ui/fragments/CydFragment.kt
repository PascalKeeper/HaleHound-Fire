package com.halehoundforge.fire.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.halehoundforge.fire.companion.CydLinkClient
import com.halehoundforge.fire.companion.CydLootVault
import com.halehoundforge.fire.databinding.FragmentCydBinding
import com.halehoundforge.fire.debug.Breadcrumbs
import com.halehoundforge.fire.privacy.SecureStore
import com.halehoundforge.fire.ui.CydControlActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * CYD brain-plane UI: nearest connect, live telemetry, loot pull, arsenal WebView.
 */
class CydFragment : Fragment() {

    private var _binding: FragmentCydBinding? = null
    private val binding get() = _binding!!
    private var lastTelemetry: CydLinkClient.Telemetry? = null
    private var autoJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCydBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val saved = SecureStore.getString(requireContext(), "cyd_base_url", "http://192.168.4.1")
        binding.hostInput.setText(saved.ifBlank { "http://192.168.4.1" })
        refreshVaultList()

        binding.btnConnectNearest.setOnClickListener { connectNearestAndControl() }
        binding.btnDiscover.setOnClickListener { probe(auto = true) }
        binding.btnRefresh.setOnClickListener { probe(auto = false) }
        binding.btnPullLoot.setOnClickListener { pullLoot() }
        binding.btnControl.setOnClickListener { openControl(currentBase()) }
        binding.btnOpenHost.setOnClickListener {
            val full = currentBase()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full)))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cannot open: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        probe(auto = true)
    }

    private fun currentBase(): String {
        val url = binding.hostInput.text?.toString()?.trim().orEmpty()
            .ifBlank { "http://192.168.4.1" }
        return if (url.startsWith("http")) url else "http://$url"
    }

    private fun openControl(base: String) {
        Breadcrumbs.net("open control $base")
        startActivity(
            Intent(requireContext(), CydControlActivity::class.java)
                .putExtra(CydControlActivity.EXTRA_URL, base)
        )
    }

    /**
     * Discover lowest-latency online CYD, store URL, open arsenal WebView.
     * User must already be on softAP or same LAN (Fire cannot join Wi‑Fi silently).
     */
    private fun connectNearestAndControl() {
        binding.btnConnectNearest.isEnabled = false
        binding.btnConnectNearest.text = "SEARCHING…"
        binding.telemetryBlock.setTextColor(Color.parseColor("#FFCC00"))
        binding.telemetryBlock.text =
            "Finding nearest CYD…\n(Join CYD softAP in system Wi‑Fi first if offline)"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val typed = binding.hostInput.text?.toString()?.trim().orEmpty()
                val candidates = mutableListOf<CydLinkClient.Telemetry>()

                // Prefer auto softAP list
                candidates += CydLinkClient.autoDiscoverTelemetry(ctx)
                // Also probe saved / typed host
                val extra = typed.ifBlank {
                    SecureStore.getString(ctx, "cyd_base_url", "http://192.168.4.1")
                }
                if (extra.isNotBlank()) {
                    candidates += CydLinkClient.connectAndProbe(extra, ctx)
                }
                // Dedupe by baseUrl, keep online only, pick lowest latency
                val best = candidates
                    .filter { it.online && it.latencyMs >= 0 }
                    .distinctBy { it.baseUrl }
                    .minByOrNull { it.latencyMs }

                if (best == null) {
                    binding.telemetryBlock.setTextColor(Color.parseColor("#FF8C42"))
                    binding.telemetryBlock.text = buildString {
                        appendLine("○ No CYD HTTP surface found.")
                        appendLine("1) Settings → Wi‑Fi → join CYD softAP")
                        appendLine("2) Return here → CONNECT NEAREST")
                        appendLine("Typical gateway: http://192.168.4.1")
                    }.trimEnd()
                    Toast.makeText(requireContext(), "Join CYD AP first, then retry", Toast.LENGTH_LONG).show()
                    return@launch
                }

                lastTelemetry = best
                SecureStore.putString(ctx, "cyd_base_url", best.baseUrl)
                binding.hostInput.setText(best.baseUrl)
                CydLootVault.writeTelemetrySnapshot(ctx, best)
                render(best)
                startAutoRefresh()
                Toast.makeText(
                    requireContext(),
                    "Linked ${best.baseUrl} (${best.latencyMs}ms) → arsenal",
                    Toast.LENGTH_LONG
                ).show()
                openControl(best.baseUrl)
            } catch (e: Exception) {
                binding.telemetryBlock.text = "Connect failed: ${e.message}"
                binding.telemetryBlock.setTextColor(Color.parseColor("#FF3344"))
            } finally {
                val live = _binding
                if (live != null && isAdded) {
                    live.btnConnectNearest.isEnabled = true
                    live.btnConnectNearest.text = "⚡ CONNECT NEAREST CYD + CONTROL"
                }
            }
        }
    }

    private fun probe(auto: Boolean) {
        binding.btnDiscover.isEnabled = false
        binding.btnRefresh.isEnabled = false
        binding.telemetryBlock.text = "Probing CYD HTTP surface…"
        binding.telemetryBlock.setTextColor(Color.parseColor("#FFCC00"))

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val host = binding.hostInput.text?.toString()?.trim().orEmpty()
                val ctx = requireContext()
                val tel = if (auto && host.isBlank()) {
                    CydLinkClient.autoDiscoverTelemetry(ctx).firstOrNull()
                        ?: CydLinkClient.connectAndProbe("http://192.168.4.1", ctx)
                } else {
                    val base = host.ifBlank { "http://192.168.4.1" }
                    CydLinkClient.connectAndProbe(base, ctx)
                }
                lastTelemetry = tel
                SecureStore.putString(requireContext(), "cyd_base_url", tel.baseUrl)
                binding.hostInput.setText(tel.baseUrl)
                CydLootVault.writeTelemetrySnapshot(requireContext(), tel)
                render(tel)
                if (tel.online) {
                    startAutoRefresh()
                }
            } catch (e: Exception) {
                binding.telemetryBlock.text = "Probe failed: ${e.message}"
                binding.telemetryBlock.setTextColor(Color.parseColor("#FF3344"))
            } finally {
                binding.btnDiscover.isEnabled = true
                binding.btnRefresh.isEnabled = true
            }
        }
    }

    private fun startAutoRefresh() {
        autoJob?.cancel()
        autoJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(8_000L)
                val base = binding.hostInput.text?.toString()?.trim().orEmpty()
                    .ifBlank { "http://192.168.4.1" }
                try {
                    val tel = CydLinkClient.connectAndProbe(base, requireContext())
                    lastTelemetry = tel
                    if (tel.online) render(tel)
                    else {
                        binding.telemetryBlock.text = "CYD link lost — rejoin softAP / LAN"
                        binding.telemetryBlock.setTextColor(Color.parseColor("#FF8C42"))
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun render(t: CydLinkClient.Telemetry) {
        binding.telemetryBlock.setTextColor(
            if (t.online) Color.parseColor("#00FF41") else Color.parseColor("#FF3344")
        )
        binding.telemetryBlock.text = buildString {
            appendLine(if (t.online) "● ONLINE" else "○ OFFLINE")
            appendLine("base     : ${t.baseUrl}")
            appendLine("latency  : ${t.latencyMs} ms")
            appendLine("title    : ${t.title.take(100)}")
            appendLine("heap     : ${t.heapHint ?: "—"}")
            appendLine("sd/free  : ${t.freeSdHint ?: "—"}")
            appendLine("mode     : ${t.modeHint ?: "—"}")
            t.notes.forEach { appendLine("· $it") }
        }.trimEnd()

        binding.pathsBlock.text = buildString {
            appendLine("HTTP PATHS (${t.rawPaths.size})")
            if (t.rawPaths.isEmpty()) appendLine("(none answered)")
            else t.rawPaths.entries.sortedBy { it.key }.forEach { (k, v) ->
                appendLine("$v  $k")
            }
        }.trimEnd()

        binding.lootBlock.text = buildString {
            appendLine("LOOT HINTS (${t.lootHints.size})")
            if (t.lootHints.isEmpty()) {
                appendLine("No file names scraped yet.")
                appendLine("When CYD serves /sd or loot HTML/JSON, entries appear here.")
            } else {
                t.lootHints.take(40).forEach {
                    appendLine("[${it.category}] ${it.name}")
                    appendLine("   ${it.path}")
                }
            }
        }.trimEnd()

        refreshVaultList()
    }

    private fun pullLoot() {
        val tel = lastTelemetry
        if (tel == null || !tel.online) {
            Toast.makeText(requireContext(), "Probe a live CYD first", Toast.LENGTH_SHORT).show()
            return
        }
        if (tel.lootHints.isEmpty()) {
            Toast.makeText(requireContext(), "No loot hints — saving telemetry only", Toast.LENGTH_SHORT).show()
            CydLootVault.writeTelemetrySnapshot(requireContext(), tel)
            refreshVaultList()
            return
        }
        binding.btnPullLoot.isEnabled = false
        binding.btnPullLoot.text = "PULLING…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val files = CydLootVault.pullAll(
                    requireContext(),
                    tel.baseUrl,
                    tel.lootHints,
                    maxFiles = 24
                )
                Toast.makeText(
                    requireContext(),
                    "Pulled ${files.size} file(s) into Fire vault",
                    Toast.LENGTH_LONG
                ).show()
                refreshVaultList()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Pull failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnPullLoot.isEnabled = true
                binding.btnPullLoot.text = "PULL LOOT → FIRE VAULT"
            }
        }
    }

    private fun refreshVaultList() {
        val files = CydLootVault.listLocal(requireContext())
        binding.vaultBlock.text = buildString {
            appendLine("LOCAL VAULT (${files.size} files)")
            appendLine(CydLootVault.vaultRoot(requireContext()).absolutePath)
            if (files.isEmpty()) appendLine("(empty — pull when CYD is linked)")
            else files.take(20).forEach {
                appendLine("${it.name}  ${it.length()}b")
            }
        }.trimEnd()
    }

    override fun onDestroyView() {
        autoJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
