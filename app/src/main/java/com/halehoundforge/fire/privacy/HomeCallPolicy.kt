package com.halehoundforge.fire.privacy

import android.content.Context
import java.net.InetAddress
import java.net.URI

/**
 * Edge-case "call home" (sensei PC) policy.
 * Default: denied. If allowed, prefer HTTPS; cleartext only to private RFC1918/localhost.
 */
object HomeCallPolicy {

    sealed class Decision {
        data object Allowed : Decision()
        data class Denied(val reason: String) : Decision()
    }

    fun evaluate(context: Context, rawUrl: String): Decision {
        if (!PrivacySettings.allowHomeCalls(context)) {
            return Decision.Denied("Call-home disabled (ninja default). Enable only for rare sensei sync.")
        }
        val url = rawUrl.trim()
        if (url.isEmpty()) return Decision.Denied("Empty URL")

        val uri = try {
            URI(if ("://" in url) url else "http://$url")
        } catch (_: Exception) {
            return Decision.Denied("Bad URL")
        }

        val host = uri.host ?: return Decision.Denied("No host")
        val scheme = (uri.scheme ?: "http").lowercase()
        val private = isPrivateHost(host)

        if (private) {
            // Lab / USB reverse / softAP path — cleartext OK for local only
            return Decision.Allowed
        }

        // Non-private = true "home over the internet"
        if (PrivacySettings.homeHttpsOnly(context) && scheme != "https") {
            return Decision.Denied("Public/home hosts require HTTPS (TLS). Refusing cleartext.")
        }
        if (scheme != "https" && scheme != "http") {
            return Decision.Denied("Unsupported scheme $scheme")
        }
        return Decision.Allowed
    }

    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim()
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h.endsWith(".local")) return true
        return try {
            val addr = InetAddress.getByName(h)
            addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || isRfc1918(addr.hostAddress)
        } catch (_: Exception) {
            // hostname unresolved — treat as non-private (force HTTPS path)
            false
        }
    }

    private fun isRfc1918(ip: String?): Boolean {
        if (ip == null) return false
        val p = ip.split(".")
        if (p.size != 4) return false
        val a = p[0].toIntOrNull() ?: return false
        val b = p[1].toIntOrNull() ?: return false
        return when (a) {
            10 -> true
            172 -> b in 16..31
            192 -> b == 168
            else -> false
        }
    }
}
