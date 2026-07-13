package com.halehoundforge.fire.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.halehoundforge.fire.R
import com.halehoundforge.fire.core.DeviceProfile
import com.halehoundforge.fire.databinding.FragmentHomeBinding
import com.halehoundforge.fire.ui.MainActivity

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = activity as? MainActivity

        binding.hostInfo.text = DeviceProfile.hostInfoBlock(requireContext())
        binding.capabilityMatrix.text = DeviceProfile.capabilityMatrix(requireContext())

        // Arsenal tiles → CYD menu-tree style navigation
        binding.tileWifi.setOnClickListener { host?.openWifi() }
        binding.tileBle.setOnClickListener { host?.openBle() }
        binding.tileGuard.setOnClickListener { host?.navigateTo(R.id.nav_guard) }
        binding.tileSigint.setOnClickListener {
            host?.openSensorHunt()
            Toast.makeText(requireContext(), "SENSOR HUNT › Flock · tags · cams", Toast.LENGTH_SHORT).show()
        }
        binding.tileCyd.setOnClickListener { host?.navigateTo(R.id.nav_cyd) }
        binding.tileAbout.setOnClickListener {
            // long-term: TERM is primary ops; About via terminal "open about"
            host?.navigateTo(R.id.nav_term)
        }
        // double-tap path: hold not available — status chip includes privacy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
