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
import com.halehoundforge.fire.guardian.BackendStore
import com.halehoundforge.fire.guardian.GuardianBackendClient
import com.halehoundforge.fire.guardian.NetworkGuardianEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GuardianFragment : Fragment(), NetworkGuardianEngine.Listener {

    private var _binding: FragmentGuardianBinding? = null
    private val binding get() = _binding!!
    private var engine: NetworkGuardianEngine? = null
    private var backendJob: Job? = null
    private var backendUrl: String = ""
    private var lastRemoteAlerts: List<String> = emptyList()
    private var lastLocalAlerts: List<String> = emptyList()
    private var backendLive = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGuardianBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val saved = BackendStore.getUrl(requireContext())
        // Prefer adb reverse (USB) first — works when Fire cannot route to PC LAN IP
        binding.backendUrl.setText(
            saved.ifEmpty { "http://127.0.0.1:8765" }
        )

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

        binding.btnBackendConnect.setOnClickListener {
            val url = binding.backendUrl.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Enter backend URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val normalized = if (url.startsWith("http")) url else "http://$url"
            BackendStore.setUrl(requireContext(), normalized)
            startBackendPoll(normalized)
            Toast.makeText(requireContext(), "Connecting $normalized", Toast.LENGTH_SHORT).show()
        }

        binding.btnBackendDisconnect.setOnClickListener {
            stopBackendPoll()
            backendLive = false
            binding.backendStatus.text = "Backend: offline · local heuristics only"
            binding.backendStatus.setTextColor(Color.parseColor("#7A9A7A"))
            mergeAlerts()
            Toast.makeText(requireContext(), "Local-only mode", Toast.LENGTH_SHORT).show()
        }

        // Auto-connect: saved URL, else adb-reverse localhost, else LAN IP
        val autoUrl = when {
            saved.isNotEmpty() -> saved
            else -> "http://127.0.0.1:8765"
        }
        startBackendPoll(autoUrl)
    }

    private fun startBackendPoll(url: String) {
        backendUrl = url.trim().trimEnd('/')
        backendJob?.cancel()
        backendJob = viewLifecycleOwner.lifecycleScope.launch {
            val client = GuardianBackendClient(backendUrl)
            while (isActive) {
                val result = client.fetchStream()
                val b = _binding
                if (b == null) break
                result.fold(
                    onSuccess = { st ->
                        backendLive = true
                        lastRemoteAlerts = st.alerts.map { a ->
                            val tag = if (a.kind.contains("virtual")) "VIRTUAL" else "NPCAP"
                            "[$tag] ${a.message}" + if (a.note.isNotBlank()) " · ${a.note}" else ""
                        }
                        b.backendStatus.text = st.rawSummary
                        b.backendStatus.setTextColor(
                            when {
                                st.snifferOk -> Color.parseColor("#00FF41")
                                st.virtual || st.mode == "virtual" -> Color.parseColor("#00D4FF")
                                else -> Color.parseColor("#FFCC00")
                            }
                        )
                        // Elevate predictive banner when remote deauths arrive
                        if (st.totalDeauths > 0 && st.alerts.isNotEmpty()) {
                            val top = st.alerts.first()
                            if (top.kind.contains("virtual")) {
                                b.predictiveStatus.text =
                                    "BACKEND VIRTUAL RADAR · ${st.totalDeauths} event(s) · pipeline OK"
                                b.predictiveStatus.setTextColor(Color.parseColor("#00D4FF"))
                            } else {
                                b.predictiveStatus.text =
                                    "NPCAP DEAUTH DETECTED · ${st.totalDeauths} frame(s) · ${top.source} → ${top.dest}"
                                b.predictiveStatus.setTextColor(Color.parseColor("#FF3344"))
                            }
                        }
                        mergeAlerts()
                    },
                    onFailure = { e ->
                        backendLive = false
                        b.backendStatus.text =
                            "Backend unreachable: ${e.message}\nURL: $backendUrl\nStart windows-backend\\start-backend.bat on PC"
                        b.backendStatus.setTextColor(Color.parseColor("#FF3344"))
                        lastRemoteAlerts = emptyList()
                        mergeAlerts()
                    }
                )
                delay(2000L)
            }
        }
    }

    private fun stopBackendPoll() {
        backendJob?.cancel()
        backendJob = null
        lastRemoteAlerts = emptyList()
    }

    private fun mergeAlerts() {
        val b = _binding ?: return
        val combined = (lastRemoteAlerts + lastLocalAlerts).take(40)
        b.alertsBlock.text = if (combined.isEmpty()) {
            "No deauth / disconnect anomalies yet.\n" +
                "Start PC backend for Npcap frames, or wait for local link events."
        } else {
            combined.joinToString("\n")
        }
    }

    override fun onSnapshot(snapshot: NetworkGuardianEngine.Snapshot) {
        val b = _binding ?: return
        requireActivity().runOnUiThread {
            // Only overwrite predictive from local if backend not live with alerts
            if (!backendLive || lastRemoteAlerts.isEmpty()) {
                b.predictiveStatus.text = snapshot.predictiveStatus
                b.predictiveStatus.setTextColor(
                    when (snapshot.predictiveLevel) {
                        NetworkGuardianEngine.Level.STABLE -> Color.parseColor("#00FF41")
                        NetworkGuardianEngine.Level.WATCH -> Color.parseColor("#FFCC00")
                        NetworkGuardianEngine.Level.HIGH -> Color.parseColor("#FF8C42")
                        NetworkGuardianEngine.Level.CRITICAL -> Color.parseColor("#FF3344")
                    }
                )
            }

            b.linkBlock.text = buildString {
                appendLine("LINK (FIRE LOCAL)")
                appendLine("SSID       : ${snapshot.ssid}${if (snapshot.ssidLockAlert) "  ⚠ not preferred" else ""}")
                appendLine("BSSID      : ${snapshot.bssid}")
                appendLine("RSSI       : ${snapshot.rssi} dBm")
                appendLine("Link       : ${snapshot.linkMbps} Mbps  ·  ${snapshot.frequencyMhz} MHz")
                appendLine("IP / GW    : ${snapshot.ip}  →  ${snapshot.gateway}")
                appendLine("Supplicant : ${snapshot.supplicant}  ·  connected=${snapshot.connected}")
            }.trimEnd()

            b.bwBlock.text = buildString {
                appendLine("BANDWIDTH / PREDICTIVE (local Fire)")
                appendLine("TX         : ${"%.3f".format(snapshot.txMbps)} Mbps")
                appendLine("RX         : ${"%.3f".format(snapshot.rxMbps)} Mbps")
                appendLine(
                    "Latency    : ${if (snapshot.latencyMs < 0) "timeout" else "${snapshot.latencyMs} ms"}" +
                        "  ·  jitter ${"%.0f".format(snapshot.jitterMs)} ms"
                )
                appendLine("Drops      : ${snapshot.packetDrops}  ·  disconnect events ${snapshot.disconnectEvents}")
                appendLine("Storm score: ${snapshot.stormScore}/100  (local heuristic)")
                if (backendLive) appendLine("Remote    : backend linked")
            }.trimEnd()

            b.mitigation.text = snapshot.mitigation

            lastLocalAlerts = snapshot.alerts.map { "[LOCAL] $it" }
            mergeAlerts()
        }
    }

    override fun onDestroyView() {
        stopBackendPoll()
        engine?.removeListener(this)
        engine?.stop()
        engine = null
        _binding = null
        super.onDestroyView()
    }
}
