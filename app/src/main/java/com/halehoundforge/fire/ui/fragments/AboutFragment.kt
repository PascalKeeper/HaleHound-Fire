package com.halehoundforge.fire.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.halehoundforge.fire.BuildConfig
import com.halehoundforge.fire.databinding.FragmentAboutBinding
import com.halehoundforge.fire.privacy.PrivacySettings
import com.halehoundforge.fire.privacy.SecureStore
import com.halehoundforge.fire.update.AppUpdateChecker
import kotlinx.coroutines.launch

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private var lastRelease: AppUpdateChecker.ReleaseInfo? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.aboutBody.text = """
            HALEHOUND-FIRE  ${BuildConfig.VERSION_NAME}
            ${BuildConfig.APPLICATION_ID}

            UNOFFICIAL · NOT AFFILIATED · NOT ENDORSED
            HaleHound™ is a trademark of Jesse Hale,
            used with permission for interoperability.

            SENSEI / NINJA
              PC trains the kit at home (identity stays in the dojo).
              Fire + CYD operate in the field — no desktop footprint.
              Runtime features work without USB. New APKs can land
              over Wi‑Fi (About → CHECK) — opt-in only.

            PRIVACY
              No analytics · no vendor telemetry · no auto phone-home.
              Prefs in EncryptedSharedPreferences (Keystore).
              Exports can scrub PII + AES-GCM encrypt (HHF1).
              Call-home only if you enable it; public hosts HTTPS.
              Update check hits GitHub Releases only when you tap.

            OFFICIAL CYD
              halehound.com · flash.halehound.com
              github.com/JesseCHale/HaleHound-CYD

            VALHALLA — authorized use only.
            Fire 7 KFQUWI · Fire OS 8.3.x · API 30
        """.trimIndent()

        refreshPrivacy()
        binding.updateStatus.text =
            "installed ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "No auto update poll. Tap CHECK when on Wi‑Fi (needs internet once)."

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

        binding.btnCheckUpdate.setOnClickListener { checkUpdates() }
        binding.btnDownloadUpdate.setOnClickListener { downloadAndInstall() }
    }

    private fun checkUpdates() {
        val b = _binding ?: return
        b.btnCheckUpdate.isEnabled = false
        b.btnCheckUpdate.text = "CHECKING…"
        b.btnDownloadUpdate.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val info = AppUpdateChecker.checkLatest()
                if (!isAdded) return@launch
                lastRelease = info
                b.updateStatus.text = info.notes + "\n\n" + info.body.take(400)
                b.btnDownloadUpdate.isEnabled = info.newer && info.apkUrl != null
                if (info.newer && info.apkUrl == null) {
                    Toast.makeText(
                        requireContext(),
                        "Newer release, no APK asset — opening browser",
                        Toast.LENGTH_LONG
                    ).show()
                    AppUpdateChecker.openBrowser(requireContext(), info.htmlUrl)
                } else if (!info.newer) {
                    Toast.makeText(requireContext(), "Up to date (or no newer tag)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Update available: ${info.tag}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    b.updateStatus.text = "Check failed: ${e.message}\nNeed Wi‑Fi with internet (GitHub)."
                    Toast.makeText(requireContext(), "Update check failed", Toast.LENGTH_LONG).show()
                }
            } finally {
                val live = _binding
                if (live != null && isAdded) {
                    live.btnCheckUpdate.isEnabled = true
                    live.btnCheckUpdate.text = "CHECK FOR UPDATES (GITHUB)"
                }
            }
        }
    }

    private fun downloadAndInstall() {
        val rel = lastRelease
        val url = rel?.apkUrl
        if (rel == null || url == null) {
            Toast.makeText(requireContext(), "Check for updates first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AppUpdateChecker.canRequestInstall(requireContext())) {
            AlertDialog.Builder(requireContext())
                .setTitle("Allow installs?")
                .setMessage(
                    "Fire OS must allow this app to install packages (unknown sources). " +
                        "Open settings, enable, then tap DOWNLOAD again."
                )
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(AppUpdateChecker.intentUnknownSources(requireContext()))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val b = _binding ?: return
        b.btnDownloadUpdate.isEnabled = false
        b.btnDownloadUpdate.text = "DOWNLOADING…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val name = rel.apkName ?: "hhf-update.apk"
                val dl = AppUpdateChecker.downloadApk(requireContext(), url, name)
                if (!isAdded) return@launch
                b.updateStatus.text =
                    "Downloaded ${dl.bytes} bytes\n${dl.file.absolutePath}\nOpening installer…"
                AppUpdateChecker.installApk(requireContext(), dl.file)
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    b.updateStatus.text = "Download failed: ${e.message}"
                }
            } finally {
                val live = _binding
                if (live != null && isAdded) {
                    live.btnDownloadUpdate.isEnabled = lastRelease?.apkUrl != null
                    live.btnDownloadUpdate.text = "DOWNLOAD + INSTALL APK"
                }
            }
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
