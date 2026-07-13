package com.halehoundforge.fire.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.halehoundforge.fire.companion.CydDiscovery
import com.halehoundforge.fire.databinding.FragmentCydBinding
import kotlinx.coroutines.launch

class CydFragment : Fragment() {
    private var _binding: FragmentCydBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCydBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.hostInput.setText("http://192.168.4.1")

        binding.btnDiscover.setOnClickListener {
            binding.discoverStatus.text = "Probing common CYD / ESP AP addresses…"
            binding.btnDiscover.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val hits = CydDiscovery.discover()
                    if (hits.isEmpty()) {
                        binding.discoverStatus.text =
                            "No HaleHound CYD discovered.\n\n• Flash firmware on Windows: flash.halehound.com\n• Join the CYD softAP or same LAN\n• Or enter IP manually below"
                    } else {
                        binding.discoverStatus.text = hits.joinToString("\n\n") {
                            "${it.url}\n${it.title} · ${it.latencyMs}ms"
                        }
                        binding.hostInput.setText(hits.first().url)
                    }
                } catch (e: Exception) {
                    binding.discoverStatus.text = "Discovery error: ${e.message}"
                } finally {
                    binding.btnDiscover.isEnabled = true
                }
            }
        }

        binding.btnOpenHost.setOnClickListener {
            val raw = binding.hostInput.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                Toast.makeText(requireContext(), "Enter a host URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = if (raw.startsWith("http")) raw else "http://$raw"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cannot open: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
