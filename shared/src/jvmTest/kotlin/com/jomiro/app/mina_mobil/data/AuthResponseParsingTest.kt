package com.jomiro.app.mina_mobil.data

import com.jomiro.app.mina_mobil.network.ApiJson
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthResponseParsingTest {
    @Test
    fun parsesTheLoginResponseIncludingBackendFieldsNotNeededByTheApp() {
        val response = """
            {
              "data": {
                "token": "4|secret-token",
                "token_type": "Bearer",
                "expires_at": "2026-11-10T12:00:00.000000Z",
                "user": {
                  "id": 4,
                  "name": "Admin",
                  "email": "admin@mina.local",
                  "roles": ["admin"],
                  "permissions": ["ordenes_trabajo.view"],
                  "cash_register": {"id": 2, "name": "Caja principal"}
                }
              }
            }
        """.trimIndent()

        val parsed = ApiJson.instance.decodeFromString<LoginResponse>(response)

        assertEquals("4|secret-token", parsed.data?.token)
        assertEquals("Caja principal", parsed.data?.user?.cashRegister?.name)
        assertEquals(listOf("admin"), parsed.data?.user?.roles)
    }
}
