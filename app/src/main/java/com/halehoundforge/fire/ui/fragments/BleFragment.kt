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
import com.halehoundforge.fire.radio.BleSurvey
import com.halehoundforge.fire.ui.ScanRow
import com.halehoundforge.fire.ui.ScanRowAdapter
import kotlinx.coroutines.launch

class BleFragment : Fragment() {
    private var _binding: FragmentScanListBinding? = null
    private val binding get() = _binding!!
    private val adapter = ScanRowAdapter()
    private var scanning = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) startScan()
        else Toast.makeText(requireContext(), "Bluetooth / location permission needed", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScanListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.title.text = "BLUETOOTH › BLE SURVEY"
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.btnScan.setOnClickListener { ensurePermsAndScan() }
        binding.swipe.setOnRefreshListener { ensurePermsAndScan() }
    }

    private fun ensurePermsAndScan() {
        val perms = PermissionsHelper.bleScanPermissions()
        if (PermissionsHelper.hasAll(requireContext(), perms)) startScan()
        else permLauncher.launch(perms)
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        binding.btnScan.isEnabled = false
        binding.status.text = "Scanning BLE advertisements (~8s)…"
        binding.swipe.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = BleSurvey(requireContext()).scan(8_000L)
                adapter.submit(rows.map { ScanRow(it.line1, it.line2) })
                binding.status.text = if (rows.isEmpty()) {
                    "0 devices · ensure Bluetooth is ON · passive only"
                } else {
                    "${rows.size} device(s) · passive advertisement survey"
                }
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
