package com.halehoundforge.fire.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.halehoundforge.fire.BuildConfig
import com.halehoundforge.fire.R
import com.halehoundforge.fire.core.ValhallaStore
import com.halehoundforge.fire.databinding.ActivityMainBinding
import com.halehoundforge.fire.ui.fragments.AboutFragment
import com.halehoundforge.fire.ui.fragments.BleFragment
import com.halehoundforge.fire.ui.fragments.CydFragment
import com.halehoundforge.fire.ui.fragments.GuardianFragment
import com.halehoundforge.fire.ui.fragments.HardenFragment
import com.halehoundforge.fire.ui.fragments.HomeFragment
import com.halehoundforge.fire.ui.fragments.TerminalFragment
import com.halehoundforge.fire.ui.fragments.WifiFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ValhallaStore.isAccepted(this)) {
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.findViewById<TextView>(R.id.headerVersion)?.text =
            "v${BuildConfig.VERSION_NAME.substringBefore("-")}"
        binding.root.findViewById<TextView>(R.id.modeBanner)?.text = "◆ NINJA · NO PHONE-HOME"
        binding.root.findViewById<TextView>(R.id.headerTitle)?.text = "HALEHOUND-FIRE"

        if (savedInstanceState == null) {
            open(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> open(HomeFragment())
                R.id.nav_harden -> open(HardenFragment())
                R.id.nav_guard -> open(GuardianFragment())
                R.id.nav_term -> open(TerminalFragment())
                R.id.nav_cyd -> open(CydFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    fun navigateTo(itemId: Int) {
        if (isFinishing || isDestroyed) return
        if (binding.bottomNav.selectedItemId != itemId) {
            binding.bottomNav.selectedItemId = itemId
        } else {
            when (itemId) {
                R.id.nav_home -> open(HomeFragment())
                R.id.nav_harden -> open(HardenFragment())
                R.id.nav_guard -> open(GuardianFragment())
                R.id.nav_term -> open(TerminalFragment())
                R.id.nav_cyd -> open(CydFragment())
            }
        }
    }

    fun openAbout() {
        open(AboutFragment())
    }

    fun openBle() {
        open(BleFragment())
    }

    fun openWifi() {
        open(WifiFragment())
    }

    private fun open(fragment: Fragment) {
        if (isFinishing || isDestroyed) return
        // commitAllowingStateLoss: bottom-nav swaps during/after lifecycle events
        // must not IllegalStateException → silent process death on Fire OS.
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }
}
