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
    fun parsesTheApiClientListShapeAndMapsAllFields() {
        val response = """
            {
              "data": [
                {"id": 526, "name": "Cliente Uno", "phone": "0", "address": null, "is_active": true},
                {"id": 527, "name": "Cliente Dos", "phone": "5555", "address": "Dirección", "is_active": false}
              ],
              "meta": {"current_page": 1, "last_page": 1, "per_page": 20, "total": 2}
            }
        """.trimIndent()

        val parsed = parseClientesPage(response)

        assertEquals(2, parsed.clientes.size)
        assertEquals("Cliente Uno", parsed.clientes[0].nombre)
        assertEquals("0", parsed.clientes[0].telefono)
        assertEquals(null, parsed.clientes[0].direccion)
        assertEquals(true, parsed.clientes[0].activo)
        assertEquals("Dirección", parsed.clientes[1].direccion)
        assertEquals(false, parsed.clientes[1].activo)
        assertEquals(1, parsed.paginaActual)
        assertEquals(2, parsed.total)
    }

    @Test
    fun parsesApiResponseWithUtf8BomAndNullAddress() {
        val response = "\uFEFF" + """{"data":[{"id":526,"name":"Cliente diagnóstico","phone":"0","address":null,"is_active":true}],"meta":{"current_page":1,"last_page":1,"per_page":20,"total":1}}"""

        val parsed = parseClientesPage(response)

        assertEquals(526L, parsed.clientes.single().id)
        assertEquals("0", parsed.clientes.single().telefono)
        assertEquals(null, parsed.clientes.single().direccion)
        assertEquals(1, parsed.total)
    }
}
