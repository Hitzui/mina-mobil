package com.jomiro.app.mina_mobil.data.ordenes

import kotlin.test.Test
import kotlin.test.assertEquals

class OrdenesResponseParsingTest {
    @Test
    fun parsesPaginatedWorkOrdersWithNestedClientAndStringWeight() {
        val response = """
            {
              "data": [
                {
                  "id": 42,
                  "code": "OT-0042",
                  "date": "2026-10-11",
                  "client": {"id": 7, "name": "Cliente de prueba"},
                  "description": "Reparación de equipo",
                  "mineral_weight": "12.50",
                  "weight_unit": "g",
                  "status": "En proceso",
                  "status_code": 2
                }
              ],
              "meta": {"current_page": 1, "last_page": 3, "per_page": 30, "total": 65}
            }
        """.trimIndent()

        val page = parseOrdersPage(response)
        val order = page.ordenes.single()

        assertEquals(42L, order.id)
        assertEquals("OT-0042", order.codigo)
        assertEquals("Cliente de prueba", order.cliente)
        assertEquals(12.5, order.pesoMineral)
        assertEquals("g", order.unidadPeso)
        assertEquals("En proceso", order.estado)
        assertEquals(2, order.codigoEstado)
        assertEquals(3, page.ultimaPagina)
        assertEquals(65, page.total)
    }

    @Test
    fun parsesNumericWeightAndNullOptionalValues() {
        val response = """
            {"data":[{"id":1,"code":"OT-0001","date":null,"client":null,"description":null,"mineral_weight":0,"weight_unit":null,"status":"Pendiente","status_code":1}],"meta":{"current_page":1,"last_page":1,"per_page":30,"total":1}}
        """.trimIndent()

        val order = parseOrdersPage(response).ordenes.single()

        assertEquals(null, order.cliente)
        assertEquals(null, order.fecha)
        assertEquals(0.0, order.pesoMineral)
        assertEquals(null, order.unidadPeso)
    }
}
