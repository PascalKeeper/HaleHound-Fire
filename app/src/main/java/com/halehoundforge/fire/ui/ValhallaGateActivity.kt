package com.halehoundforge.fire.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.halehoundforge.fire.core.DeviceProfile
import com.halehoundforge.fire.core.ValhallaStore
import com.halehoundforge.fire.databinding.ActivityValhallaBinding

class ValhallaGateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityValhallaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ValhallaStore.isAccepted(this)) {
            goMain()
            return
        }

        binding = ActivityValhallaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.deviceBanner.text = DeviceProfile.hostBanner(this)

        binding.btnAccept.setOnClickListener {
            ValhallaStore.accept(this)
            goMain()
        }
        binding.btnDecline.setOnClickListener {
            finishAffinity()
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
