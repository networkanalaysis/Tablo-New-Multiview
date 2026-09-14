package com.tablotv.app

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TabloSigner {
    private const val HASH_KEY = "6l8jU5N43cEilqItmT3U2M2PFM3qPziilXqau9ys"
    private const val DEVICE_KEY = "ljpg6ZkwShVv8aI12E2LP55Ep8vq1uYDPvX0DdTB"

    fun deviceDate(): String = java.text.SimpleDateFormat(
        "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
        java.util.Locale.US
    ).apply {
        timeZone = java.util.TimeZone.getTimeZone("GMT")
    }.format(System.currentTimeMillis())

    fun makeDeviceAuth(method: String, path: String, body: String = ""): Pair<String, String> {
        val date = deviceDate()
        val md5Body = if (body.isNotBlank()) {
            MessageDigest.getInstance("MD5").digest(body.toByteArray())
                .joinToString(separator = "") { "%02x".format(it) }
        } else {
            ""
        }
        val payload = "$method\n$path\n$md5Body\n$date"
        val signature = hmacMd5(HASH_KEY, payload)
        return "tablo:$DEVICE_KEY:$signature" to date
    }

    fun hmacMd5(key: String, payload: String): String {
        val mac = Mac.getInstance("HmacMD5")
        val secret = SecretKeySpec(key.toByteArray(), "HmacMD5")
        mac.init(secret)
        val digest = mac.doFinal(payload.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
