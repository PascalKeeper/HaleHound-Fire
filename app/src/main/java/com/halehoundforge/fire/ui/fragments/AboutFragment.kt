package com.halehoundforge.fire.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.halehoundforge.fire.BuildConfig
import com.halehoundforge.fire.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.aboutBody.text = """
            HALEHOUND-FIRE  ${BuildConfig.VERSION_NAME}
            ${BuildConfig.APPLICATION_ID}

            WHY FIRE + CYD
              CYD = radio edge (tight flash/RAM/compute).
              Fire = debug/storage/operator plane (screen,
              disk, APK iterate without reflashing radios).
              Not “firmware on Kindle” — companion architecture.

            OFFICIAL CYD FIRMWARE
              halehound.com
              flash.halehound.com
              github.com/JesseCHale/HaleHound-CYD

            THIS APP
              Unofficial Fire OS companion.
              No firmware binaries redistributed.
              Local Guardian + surveys work offline.
              Roadmap: CYD telemetry, loot pull, serial debug.

            VALHALLA PROTOCOL
              Authorized use only. TX modules stay on CYD
              under written scope.

            VALIDATED
              Fire 7 12th gen  KFQUWI / quartz
              Fire OS 8.3.x  ·  Android 11 API 30
        """.trimIndent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
