package com.weatherlab.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class SignerTest {
    @Test
    fun signatureMatchesThePc() {
        // The same vector is checked in Weather Lab's tests/test_api_signed.py and the phone app's tests.
        assertEquals(
            "f0ee0c626b7f8daaf4975dc0ca319909551b52d04209421a3ad845d08d49f06d",
            RequestSigner.signature(RequestSigner.keyFor("test-token"), "/api/v1/status", "1700000000", "abcdef0123456789"),
        )
    }
}
