package com.halehoundforge.fire.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.halehoundforge.fire.databinding.ActivityCydControlBinding
import com.halehoundforge.fire.debug.Breadcrumbs
import com.halehoundforge.fire.privacy.HomeCallPolicy

/**
 * In-app WebView shell for official CYD web UI (full arsenal lives on CYD firmware).
 * Fire only hosts the browser surface — no TX modules on tablet.
 */
class CydControlActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCydControlBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCydControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
            .ifBlank { "http://192.168.4.1" }
        val base = if (url.startsWith("http")) url else "http://$url"

        val host = try {
            java.net.URL(base).host ?: ""
        } catch (_: Exception) {
            ""
        }
        // SoftAP/LAN always OK. Public hosts only if call-home policy allows.
        if (!HomeCallPolicy.isPrivateHost(host)) {
            when (val d = HomeCallPolicy.evaluate(this, base)) {
                is HomeCallPolicy.Decision.Denied -> {
                    Toast.makeText(this, "Blocked: ${d.reason}", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                else -> {}
            }
        }

        binding.controlTitle.text = "CYD CONTROL · $base"
        binding.controlHint.text =
            "Arsenal UI is served by CYD firmware. Fire = remote panel. Close when done."

        val w = binding.web
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        Breadcrumbs.net("cyd_control open $base")
        w.loadUrl(base)

        binding.btnReload.setOnClickListener { w.reload() }
        binding.btnClose.setOnClickListener { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.web.canGoBack()) binding.web.goBack()
        else super.onBackPressed()
    }

    companion object {
        const val EXTRA_URL = "cyd_url"
    }
}
