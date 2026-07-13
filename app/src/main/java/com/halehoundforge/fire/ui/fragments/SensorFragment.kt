package com.halehoundforge.fire.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.halehoundforge.fire.databinding.FragmentSensorBinding
import com.halehoundforge.fire.debug.Breadcrumbs
import com.halehoundforge.fire.debug.CrashGuard
import com.halehoundforge.fire.sensor.SensorHuntEngine
import com.halehoundforge.fire.sensor.SensorHit
import kotlinx.coroutines.launch

/**
 * Flock / AirTag / cheap-cam passive hunt UI.
 */
class SensorFragment : Fragment() {

    private var _binding: FragmentSensorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSensorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Breadcrumbs.nav("SensorFragment")
        binding.btnHunt.setOnClickListener { runHunt(includeLan = true) }
        binding.btnHuntNoLan.setOnClickListener { runHunt(includeLan = false) }
    }

    private fun runHunt(includeLan: Boolean) {
        val b = _binding ?: return
        b.btnHunt.isEnabled = false
        b.btnHuntNoLan.isEnabled = false
        b.huntSummary.setTextColor(Color.parseColor("#FFCC00"))
        b.huntSummary.text = if (includeLan) "Hunting BLE + Wi‑Fi + LAN…" else "Hunting BLE + Wi‑Fi (no LAN)…"
        b.huntResults.text = "Scanning…"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val report = SensorHuntEngine.hunt(requireContext(), includeLan = includeLan)
                if (!isAdded) return@launch
                val live = _binding ?: return@launch
                live.huntSummary.setTextColor(Color.parseColor("#00FF41"))
                live.huntSummary.text = buildString {
                    appendLine("hits=${report.hits.size}  ble=${report.bleScanned}  wifi=${report.wifiScanned}  lan=${report.lanHosts}")
                    append(report.notes.take(2).joinToString(" · "))
                }.trimEnd()
                live.huntResults.text = formatHits(report.hits, report.notes)
            } catch (e: Exception) {
                CrashGuard.recordNonFatal("sensor_hunt", e)
                if (isAdded) {
                    _binding?.huntSummary?.setTextColor(Color.parseColor("#FF3344"))
                    _binding?.huntSummary?.text = "Hunt failed: ${e.message}"
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                val live = _binding
                if (live != null && isAdded) {
                    live.btnHunt.isEnabled = true
                    live.btnHuntNoLan.isEnabled = true
                }
            }
        }
    }

    private fun formatHits(hits: List<SensorHit>, notes: List<String>): String {
        if (hits.isEmpty()) {
            return buildString {
                appendLine("No fingerprint hits this pass.")
                appendLine()
                notes.forEach { appendLine("· $it") }
            }
        }
        return buildString {
            hits.forEachIndexed { i, h ->
                appendLine("── ${i + 1}. [${h.confidence}] ${h.kind} ──")
                appendLine(h.title)
                appendLine("${h.transport}  ${h.address}  ${h.rssi?.let { "${it}dBm" } ?: ""}")
                h.signals.forEach { appendLine("  · $it") }
                appendLine(h.detail)
                if (h.networkHints.isNotEmpty()) {
                    appendLine("NET HINTS:")
                    h.networkHints.forEach { appendLine("  → $it") }
                }
                appendLine()
            }
            appendLine("═══ NOTES ═══")
            notes.forEach { appendLine("· $it") }
        }.trimEnd()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
