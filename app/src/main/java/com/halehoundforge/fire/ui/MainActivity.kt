package com.halehoundforge.fire.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.halehoundforge.fire.R
import com.halehoundforge.fire.core.ValhallaStore
import com.halehoundforge.fire.databinding.ActivityMainBinding
import com.halehoundforge.fire.ui.fragments.AboutFragment
import com.halehoundforge.fire.ui.fragments.BleFragment
import com.halehoundforge.fire.ui.fragments.CydFragment
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

        if (savedInstanceState == null) {
            swap(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> swap(HomeFragment())
                R.id.nav_wifi -> swap(WifiFragment())
                R.id.nav_ble -> swap(BleFragment())
                R.id.nav_cyd -> swap(CydFragment())
                R.id.nav_about -> swap(AboutFragment())
                else -> false
            }
            true
        }
    }

    private fun swap(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
