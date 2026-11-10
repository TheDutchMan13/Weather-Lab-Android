package com.weatherlab.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain-JVM checks that run on every GitHub build (0.7.32). */
class BasicsTest {
    @Test
    fun addressIsNormalised() {
        assertEquals("http://192.168.1.50:8600", WeatherLabApi.normalise("192.168.1.50"))
        assertEquals("http://192.168.1.50:8600", WeatherLabApi.normalise(" http://192.168.1.50:8600/ "))
        assertEquals("http://192.168.1.50:9000", WeatherLabApi.normalise("192.168.1.50:9000/api/v1/status"))
        assertEquals("http://pc.tail1234.ts.net:8600", WeatherLabApi.normalise("pc.tail1234.ts.net"))
    }

    @Test
    fun refreshBacksOffAndCaps() {
        assertEquals(60_000L, refreshWait(0, onScreen = true))
        assertEquals(300_000L, refreshWait(0, onScreen = false))
        assertEquals(120_000L, refreshWait(1, onScreen = true))
        assertEquals(480_000L, refreshWait(3, onScreen = true))
        assertEquals(600_000L, refreshWait(9, onScreen = true))
        assertEquals(600_000L, refreshWait(9, onScreen = false))
    }

    @Test
    fun networkProblemsArePlain() {
        val base = "http://192.168.1.50:8600"
        val cases = listOf(
            java.net.SocketTimeoutException(), java.net.UnknownHostException("x"), java.net.NoRouteToHostException(),
            java.net.ConnectException(), java.io.IOException("boom"),
        )
        for (e in cases) {
            val text = WeatherLabApi.networkProblem(e, base)
            assertTrue(text, text.contains("192.168.1.50:8600"))
            assertFalse(text, text.contains("Exception"))
        }
        assertTrue(WeatherLabApi.networkProblem(java.net.SocketTimeoutException(), base).contains("didn't answer in time"))
    }
}
