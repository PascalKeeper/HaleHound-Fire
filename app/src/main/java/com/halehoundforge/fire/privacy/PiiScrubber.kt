package com.halehoundforge.fire.privacy

/**
 * Hide personal markers from reports / clipboard / shared logs.
 */
object PiiScrubber {

    private val MAC = Regex("""\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b""")
    private val IPV4 = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b""")
    private val SERIALISH = Regex(
        """(?i)\b(serial|serialno|ro\.serialno|GCC[0-9A-Z]+|[A-Z0-9]{10,})\b"""
    )
    private val EMAIL = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")

    fun scrub(text: String): String {
        var t = text
        t = MAC.replace(t) { m ->
            val parts = m.value.split(":", "-")
            if (parts.size == 6) "xx:xx:xx:xx:${parts[4]}:${parts[5]}" else "xx:xx:xx:xx:xx:xx"
        }
        t = IPV4.replace(t) { m ->
            val p = m.value.split(".")
            if (p.size == 4) "${p[0]}.${p[1]}.x.x" else "x.x.x.x"
        }
        t = EMAIL.replace(t, "[redacted-email]")
        // common SSID lines — leave SSID visible (ops need it) but strip long device serials
        t = Regex("""(?i)(serial\s*[:=]\s*)\S+""").replace(t, "$1[redacted]")
        t = Regex("""(?i)(ro\.serialno=)\S+""").replace(t, "$1[redacted]")
        return t
    }

    fun maybeScrub(context: android.content.Context, text: String): String =
        if (PrivacySettings.scrubPii(context)) scrub(text) else text
}
