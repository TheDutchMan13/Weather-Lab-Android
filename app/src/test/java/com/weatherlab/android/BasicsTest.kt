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
    fun signatureMatchesThePc() {
        // The same vector is checked in Weather Lab's tests/test_api_signed.py.
        val key = RequestSigner.keyFor("test-token")
        assertEquals(
            "f0ee0c626b7f8daaf4975dc0ca319909551b52d04209421a3ad845d08d49f06d",
            RequestSigner.signature(key, "/api/v1/status", "1700000000", "abcdef0123456789"),
        )
        assertEquals(24, RequestSigner.nonce().length)
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

    @Test
    fun welcomeSkyFollowsTheHour() {
        assertEquals(DayPeriod.NIGHT, dayPeriod(4))
        assertEquals(DayPeriod.MORNING, dayPeriod(5))
        assertEquals(DayPeriod.MORNING, dayPeriod(11))
        assertEquals(DayPeriod.AFTERNOON, dayPeriod(12))
        assertEquals(DayPeriod.AFTERNOON, dayPeriod(16))
        assertEquals(DayPeriod.EVENING, dayPeriod(17))
        assertEquals(DayPeriod.EVENING, dayPeriod(20))
        assertEquals(DayPeriod.NIGHT, dayPeriod(21))
        assertEquals("Good morning.", dayPeriod(8).greeting)
    }
}
