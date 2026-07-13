package com.halehoundforge.fire.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.halehoundforge.fire.core.PermissionsHelper
import com.halehoundforge.fire.databinding.FragmentScanListBinding
import com.halehoundforge.fire.radio.WifiSurvey
import com.halehoundforge.fire.ui.ScanRow
import com.halehoundforge.fire.ui.ScanRowAdapter
import kotlinx.coroutines.launch

class WifiFragment : Fragment() {
    private var _binding: FragmentScanListBinding? = null
    private val binding get() = _binding!!
    private val adapter = ScanRowAdapter()
    private var scanning = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) startScan()
        else Toast.makeText(requireContext(), "Location permission needed for Wi‑Fi scan", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScanListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.title.text = "Wi‑Fi Survey (passive)"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.btnScan.setOnClickListener { ensurePermsAndScan() }
        binding.swipe.setOnRefreshListener { ensurePermsAndScan() }
    }

    private fun ensurePermsAndScan() {
        val perms = PermissionsHelper.wifiScanPermissions()
        if (PermissionsHelper.hasAll(requireContext(), perms)) startScan()
        else permLauncher.launch(perms)
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        binding.btnScan.isEnabled = false
        binding.status.text = "Scanning (passive Android WifiManager)…"
        binding.swipe.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = WifiSurvey(requireContext()).scanOnce()
                adapter.submit(rows.map { ScanRow(it.line1, it.line2) })
                binding.status.text = "${rows.size} AP(s) · Blue Team only · no TX attacks on Fire OS"
            } catch (e: Exception) {
                binding.status.text = "Scan failed: ${e.message}"
            } finally {
                scanning = false
                binding.btnScan.isEnabled = true
                binding.swipe.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
