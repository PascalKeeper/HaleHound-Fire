package com.halehoundforge.fire.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.halehoundforge.fire.BuildConfig
import com.halehoundforge.fire.databinding.FragmentAboutBinding
import com.halehoundforge.fire.privacy.PrivacySettings
import com.halehoundforge.fire.privacy.SecureStore

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

            SENSEI / NINJA
              PC trains the kit at home (identity stays in the dojo).
              Fire + CYD operate in the field — no desktop footprint.

            PRIVACY
              No analytics · no vendor telemetry · no auto phone-home.
              Prefs in EncryptedSharedPreferences (Keystore).
              Exports can scrub PII + AES-GCM encrypt (HHF1).
              Call-home only if you enable it; public hosts HTTPS.

            OFFICIAL CYD
              halehound.com · flash.halehound.com
              github.com/JesseCHale/HaleHound-CYD

            VALHALLA — authorized use only.
            Fire 7 KFQUWI · Fire OS 8.3.x · API 30
        """.trimIndent()

        refreshPrivacy()

        binding.btnToggleScrub.setOnClickListener {
            PrivacySettings.setScrubPii(requireContext(), !PrivacySettings.scrubPii(requireContext()))
            refreshPrivacy()
        }
        binding.btnToggleEncrypt.setOnClickListener {
            PrivacySettings.setEncryptExports(requireContext(), !PrivacySettings.encryptExports(requireContext()))
            refreshPrivacy()
        }
        binding.btnToggleHome.setOnClickListener {
            val next = !PrivacySettings.allowHomeCalls(requireContext())
            if (next) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Enable call-home?")
                    .setMessage(
                        "Only for rare sensei sync. Public hosts require HTTPS. " +
                            "Default is OFF so ninjas do not phone the dojo."
                    )
                    .setPositiveButton("Enable") { _, _ ->
                        PrivacySettings.setAllowHomeCalls(requireContext(), true)
                        refreshPrivacy()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                PrivacySettings.setAllowHomeCalls(requireContext(), false)
                refreshPrivacy()
            }
        }
        binding.btnWipeSecure.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Wipe secure prefs?")
                .setMessage("Clears encrypted markers (SSID locks, backend URL, device export key material, VALHALLA flag). App will re-gate.")
                .setPositiveButton("Wipe") { _, _ ->
                    SecureStore.clearAll(requireContext())
                    Toast.makeText(requireContext(), "Secure store wiped", Toast.LENGTH_LONG).show()
                    refreshPrivacy()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshPrivacy() {
        binding.privacyStatus.text = PrivacySettings.summary(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
