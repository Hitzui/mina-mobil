package com.jomiro.app.mina_mobil.data.clientes

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientesResponseParsingTest {
    @Test
    fun parsesThePaginatedClientListResponse() {
        val response = """
            {
              "data": [
                {
                  "id": 12,
                  "name": "Cliente Ejemplo",
                  "phone": null,
                  "address": null,
                  "is_active": true
                }
              ],
              "meta": {
                "current_page": 1,
                "last_page": 3,
                "per_page": 30,
                "total": 65
              }
            }
        """.trimIndent()

        val parsed = parseClientesPage(response)

        assertEquals("Cliente Ejemplo", parsed.clientes.single().nombre)
        assertEquals(null, parsed.clientes.single().telefono)
        assertEquals(true, parsed.clientes.single().activo)
        assertEquals(3, parsed.ultimaPagina)
        assertEquals(65, parsed.total)
    }

    @Test
    fun acceptsTheDatabaseFieldNamesWhenAnOlderApiResponseUsesThem() {
        val response = """
            {
              "data": [{"id": 5, "nombre": "Cliente antiguo", "telefono": "5555", "direccion": null, "estado": 0}],
              "meta": {"current_page": 2, "last_page": 4, "total": 90}
            }
        """.trimIndent()

        val parsed = parseClientesPage(response)

        assertEquals("Cliente antiguo", parsed.clientes.single().nombre)
        assertEquals(false, parsed.clientes.single().activo)
        assertEquals(2, parsed.paginaActual)
    }
}
