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
import com.halehoundforge.fire.ui.fragments.HomeFragment
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

        // CYD-style chrome header
        binding.root.findViewById<TextView>(R.id.headerVersion)?.text =
            "v${BuildConfig.VERSION_NAME.substringBefore("-")}"
        binding.root.findViewById<TextView>(R.id.modeBanner)?.text = "◆ BLUE TEAM MODE"
        binding.root.findViewById<TextView>(R.id.headerTitle)?.text = "HALEHOUND-FIRE"

        if (savedInstanceState == null) {
            open(HomeFragment(), R.id.nav_home)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> open(HomeFragment(), item.itemId)
                R.id.nav_guard -> open(GuardianFragment(), item.itemId)
                R.id.nav_wifi -> open(WifiFragment(), item.itemId)
                R.id.nav_ble -> open(BleFragment(), item.itemId)
                R.id.nav_cyd -> open(CydFragment(), item.itemId)
                else -> false
            }
            true
        }
    }

    /** Navigate from arsenal tiles without double-pushing bottom nav selection. */
    fun navigateTo(itemId: Int) {
        if (binding.bottomNav.selectedItemId != itemId) {
            binding.bottomNav.selectedItemId = itemId
        } else {
            when (itemId) {
                R.id.nav_home -> open(HomeFragment(), itemId)
                R.id.nav_guard -> open(GuardianFragment(), itemId)
                R.id.nav_wifi -> open(WifiFragment(), itemId)
                R.id.nav_ble -> open(BleFragment(), itemId)
                R.id.nav_cyd -> open(CydFragment(), itemId)
            }
        }
    }

    fun openAbout() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AboutFragment())
            .commit()
        // Keep arsenal selected visually but show about
    }

    private fun open(fragment: Fragment, @Suppress("UNUSED_PARAMETER") itemId: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
