package com.weatherlab.wear

/**
 * Signs a request for Weather Lab 0.37.59 and newer, so the token itself never crosses the network. The key is the
 * lowercase hex SHA-256 of the token; the message is "GET\n<path>\n<time>\n<nonce>". The PC refuses a request more
 * than five minutes off its clock, or one whose nonce it has seen. Shared by the phone and watch apps (0.7.33).
 */
object RequestSigner {
    private val random = java.security.SecureRandom()

    fun keyFor(token: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun signature(key: String, path: String, time: String, nonce: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("GET\n$path\n$time\n$nonce".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun nonce(): String = ByteArray(12).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Adds the three headers to a connection for this path. */
    fun sign(conn: java.net.HttpURLConnection, token: String, path: String) {
        val time = (System.currentTimeMillis() / 1000).toString()
        val nonce = nonce()
        conn.setRequestProperty("X-WL-Time", time)
        conn.setRequestProperty("X-WL-Nonce", nonce)
        conn.setRequestProperty("Authorization", "WL-HMAC " + signature(keyFor(token), path, time, nonce))
    }
}
