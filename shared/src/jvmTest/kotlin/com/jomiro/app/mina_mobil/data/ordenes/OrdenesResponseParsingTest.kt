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

    @Test
    fun parsesOrderDetailAndProcessProgress() {
        val response = """
            {
              "data": {
                "id": 42,
                "code": "OT-0042",
                "date": "2026-10-11",
                "client": {"id": 7, "name": "Cliente de prueba"},
                "description": "Reparación de equipo",
                "mineral_weight": "12.50",
                "weight_unit": "g",
                "status": "En proceso",
                "status_code": 2,
                "processes": [
                  {
                    "id": 8,
                    "code": "PR-0008",
                    "stage": "Trituración",
                    "status": "En proceso",
                    "start_date": "2026-10-10T08:00:00.000000Z",
                    "end_date": null,
                    "input_weight": "12.50",
                    "output_weight": 10.0,
                    "observations": "En espera de revisión"
                  }
                ],
                "costs": [
                  {
                    "id": 19,
                    "date": "2026-10-11",
                    "process": null,
                    "category": "Transporte",
                    "description": "Traslado de mineral",
                    "quantity": 2,
                    "currency": "USD",
                    "total": "35.00",
                    "total_nio": "1281.50"
                  }
                ],
                "incomes": [
                  {
                    "id": 24,
                    "date": "2026-10-11",
                    "type": "Venta de mineral",
                    "description": "Entrega parcial",
                    "currency": "NIO",
                    "total": 2500,
                    "total_nio": "2500.00"
                  }
                ]
              }
            }
        """.trimIndent()

        val detail = parseOrderDetail(response)
        val process = detail.procesos.single()
        val cost = detail.costos.single()
        val income = detail.ingresos.single()

        assertEquals(42L, detail.orden.id)
        assertEquals("OT-0042", detail.orden.codigo)
        assertEquals("Cliente de prueba", detail.orden.cliente)
        assertEquals("Trituración", process.etapa)
        assertEquals("En proceso", process.estado)
        assertEquals(12.5, process.pesoEntrada)
        assertEquals(10.0, process.pesoSalida)
        assertEquals("En espera de revisión", process.observaciones)
        assertEquals("Transporte", cost.categoria)
        assertEquals("35.00", cost.total)
        assertEquals("1281.50", cost.totalNio)
        assertEquals("Venta de mineral", income.tipo)
        assertEquals("2500", income.total)
    }
}
