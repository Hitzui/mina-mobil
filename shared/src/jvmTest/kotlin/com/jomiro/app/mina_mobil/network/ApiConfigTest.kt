package com.jomiro.app.mina_mobil.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiConfigTest {
    @Test
    fun localApiAddressIsAdaptedForAndroidEmulatorFromTheSharedSetting() {
        assertEquals(
            API_BASE_URL.replaceFirst("http://localhost", "http://10.0.2.2"),
            resolveApiBaseUrl("Android 35"),
        )
        assertEquals(API_BASE_URL, resolveApiBaseUrl("Java 21"))
    }
}
