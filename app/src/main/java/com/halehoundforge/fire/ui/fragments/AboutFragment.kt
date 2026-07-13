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
            HaleHound Fire ${BuildConfig.VERSION_NAME}
            Package: ${BuildConfig.APPLICATION_ID}

            First Fire OS port of the HaleHound ecosystem concept.

            Official HaleHound (ESP32 CYD):
            • https://halehound.com
            • https://flash.halehound.com
            • https://github.com/JesseCHale/HaleHound-CYD

            This Fire app is independent companion software for Amazon Fire tablets.
            It does not redistribute proprietary HaleHound firmware binaries.

            LEGAL / VALHALLA PROTOCOL
            Use only with explicit authorization. Passive survey tools may still
            expose sensitive network metadata — treat lab results carefully.
            Offensive modules exist only on dedicated ESP32 CYD hardware with
            external radios; this tablet cannot legally or technically replace them
            for unauthorized attacks, and stock Fire OS blocks raw 802.11 injection.

            Target hardware validated: Amazon Fire 7 (12th gen) KFQUWI / quartz
            Fire OS 8.3.x (Android 11 API 30).
        """.trimIndent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
