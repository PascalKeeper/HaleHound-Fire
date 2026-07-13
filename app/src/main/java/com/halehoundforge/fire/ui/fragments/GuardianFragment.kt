package com.halehoundforge.fire.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.halehoundforge.fire.databinding.FragmentGuardianBinding
import com.halehoundforge.fire.guardian.NetworkGuardianEngine

/**
 * Fully local Guardian UI — no laptop / backend / Npcap calls.
 */
class GuardianFragment : Fragment(), NetworkGuardianEngine.Listener {

    private var _binding: FragmentGuardianBinding? = null
    private val binding get() = _binding!!
    private var engine: NetworkGuardianEngine? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGuardianBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        engine = NetworkGuardianEngine(requireContext().applicationContext).also { eng ->
            eng.addListener(this)
            eng.getPreferredSsid()?.let { binding.preferredSsid.setText(it) }
            eng.start(viewLifecycleOwner.lifecycleScope)
        }

        binding.btnSetPreferred.setOnClickListener {
            val ssid = binding.preferredSsid.text?.toString()?.trim().orEmpty()
            engine?.setPreferredSsid(ssid.ifEmpty { null })
            Toast.makeText(
                requireContext(),
                if (ssid.isEmpty()) "SSID lock cleared" else "SSID lock → $ssid",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnReconnect.setOnClickListener {
            val msg = engine?.attemptReconnectToPreferred() ?: "Engine offline"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSnapshot(snapshot: NetworkGuardianEngine.Snapshot) {
        val b = _binding ?: return
        requireActivity().runOnUiThread {
            b.modeBanner.text = "◆ ${snapshot.modeLabel}"

            b.predictiveStatus.text = snapshot.predictiveStatus
            b.predictiveStatus.setTextColor(
                when (snapshot.predictiveLevel) {
                    NetworkGuardianEngine.Level.STABLE -> Color.parseColor("#00FF41")
                    NetworkGuardianEngine.Level.WATCH -> Color.parseColor("#FFCC00")
                    NetworkGuardianEngine.Level.HIGH -> Color.parseColor("#FF8C42")
                    NetworkGuardianEngine.Level.CRITICAL -> Color.parseColor("#FF3344")
                }
            )

            b.linkBlock.text = buildString {
                appendLine("LINK (ON-DEVICE)")
                appendLine("SSID       : ${snapshot.ssid}${if (snapshot.ssidLockAlert) "  ⚠ not preferred" else ""}")
                appendLine("BSSID      : ${snapshot.bssid}")
                appendLine("RSSI       : ${snapshot.rssi} dBm")
                appendLine("Link       : ${snapshot.linkMbps} Mbps  ·  ${snapshot.frequencyMhz} MHz")
                appendLine("IP / GW    : ${snapshot.ip}  →  ${snapshot.gateway}")
                appendLine("Supplicant : ${snapshot.supplicant}  ·  up=${snapshot.connected}")
            }.trimEnd()

            b.bwBlock.text = buildString {
                appendLine("SENSORS (NO LAPTOP)")
                appendLine("TX/RX      : ${"%.3f".format(snapshot.txMbps)} / ${"%.3f".format(snapshot.rxMbps)} Mbps")
                appendLine(
                    "Latency    : ${if (snapshot.latencyMs < 0) "timeout" else "${snapshot.latencyMs} ms"}" +
                        "  ·  jitter ${"%.0f".format(snapshot.jitterMs)} ms"
                )
                appendLine("GW drops   : ${snapshot.packetDrops}")
                appendLine("Storm score: ${snapshot.stormScore}/100")
                appendLine("Signals    : ${snapshot.signals}")
            }.trimEnd()

            b.mitigation.text = snapshot.mitigation

            b.alertsBlock.text = if (snapshot.alerts.isEmpty()) {
                "No anomalies yet — leave this tab open while on Wi‑Fi.\n" +
                    "Local sensors: disconnects · RSSI cliffs · BSSID churn · latency."
            } else {
                snapshot.alerts.joinToString("\n")
            }
        }
    }

    override fun onDestroyView() {
        engine?.removeListener(this)
        engine?.stop()
        engine = null
        _binding = null
        super.onDestroyView()
    }
}
