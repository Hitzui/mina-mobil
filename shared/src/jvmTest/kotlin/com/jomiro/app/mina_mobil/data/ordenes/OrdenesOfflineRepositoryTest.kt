package com.jomiro.app.mina_mobil.data.ordenes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.data.UserSession
import com.jomiro.app.mina_mobil.db.MinaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class OrdenesOfflineRepositoryTest {
    @Test
    fun equipmentUsesEffectiveHoursAndKeepsFuelEstimateOutOfRecordedCosts() = kotlinx.coroutines.runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val database = MinaDatabase(driver)
        val sessions = SessionRepository(database)
        sessions.save(UserSession("offline-token", null, 42, "Usuario", "offline@test.local", emptyList(), emptyList(), 1, "Caja"))
        database.clientCacheQueries.saveOfflineSnapshot(42, """{"data":{"clients":[],"employees":[],"orders":[],"catalogs":{"equipment":[{"id":9,"code":"EQ-9","name":"Planta","acquisition_value":36000,"residual_value":0,"useful_life_months":1,"remaining_value":36000,"depreciation_enabled":true,"cost_method":"depreciacion","power_kw":0,"resources":[{"producto_id":8,"nombre":"Diésel","unidad":"litros","cantidad_por_hora":2,"costo_unitario_nio":45}]}],"equipment_assignments":[{"equipment_id":9,"start_date":"2026-10-01T07:00","end_date":"2026-10-01T09:00"}]}}}""", "2026-10-01T00:00:00Z")
        val client = HttpClient(CIO)
        try {
            val sync = OfflineSyncRepository(client, "http://localhost/api/v1", sessions, database)
            val repository = OrdenesRepository(client, "http://localhost/api/v1", sessions, sync)
            val pending = repository.depreciacionEquipo(1, 1, 9, "2026-10-01T09:00", "2026-10-01T17:00")
            assertEquals(0.0, pending.costoTotalNio)
            val estimate = repository.depreciacionEquipo(1, 1, 9, "2026-10-01T09:00", "2026-10-01T17:00", 5.0)
            assertEquals(250.0, estimate.costoTotalNio)
            assertEquals(700.0, estimate.costoConInsumosEstimados)
            assertNull(estimate.solapamiento) // Dos usos contiguos el mismo día son válidos.
            val overlap = repository.depreciacionEquipo(1, 1, 9, "2026-10-01T08:59", "2026-10-01T17:00", 5.0)
            assertTrue(overlap.solapamiento != null)
            val excessive = runCatching { repository.depreciacionEquipo(1, 1, 9, "2026-10-01T09:00", "2026-10-01T17:00", 9.0) }
            assertTrue(excessive.exceptionOrNull() is OrdenesException)
        } finally {
            client.close()
            driver.close()
        }
    }

    @Test
    fun createsOrderAndProcessLocallyAndKeepsTheirTemporaryRelationship() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val database = MinaDatabase(driver)
        val sessions = SessionRepository(database)
        sessions.save(
            UserSession("offline-token", null, 42, "Usuario", "offline@test.local", emptyList(), emptyList(), 1, "Caja"),
        )
        val body = """{"data":{"clients":[{"id":2,"name":"Cliente local","phone":null,"address":null,"is_active":true}],"employees":[],"orders":[],"catalogs":{"units":[{"id":3,"name":"Tonelada"}],"stages":[{"id":4,"name":"Trituración","order":1}],"employees":[],"payment_types":[],"equipment":[],"employee_rates":[],"exchange_rates":[],"cost_categories":[{"id":6,"name":"Energía"}],"currencies":[{"id":7,"code":"NIO","name":"Córdoba","is_base_currency":true}],"products":[{"id":8,"code":"MAT-000008","name":"Cemento","unit":"kg","stock":12,"average_cost":3.5}]}}}"""
        database.clientCacheQueries.saveOfflineSnapshot(42, body, "2026-10-11T00:00:00Z")
        val client = HttpClient(CIO)
        val sync = OfflineSyncRepository(client, "http://localhost/api/v1", sessions, database)
        val repository = OrdenesRepository(client, "http://localhost/api/v1", sessions, sync)

        val options = kotlinx.coroutines.runBlocking { repository.opcionesFormulario() }
        assertEquals("Cliente local", options.clientes.single().nombre)
        assertEquals("Tonelada", options.unidades.single().nombre)

        val order = kotlinx.coroutines.runBlocking { repository.crear(2, "2026-10-10", 10.0, 3, "Orden sin señal") }
        assertTrue(order.id < 0)
        assertEquals("LOCAL-1", order.codigo)
        assertEquals(1, order.codigoEstado)
        val orderPage = kotlinx.coroutines.runBlocking { repository.listar(1) }
        assertEquals(order.id, orderPage.ordenes.single().id)

        val stages = kotlinx.coroutines.runBlocking { repository.opcionesProceso(order.id) }
        assertEquals("Trituración", stages.single().nombre)
        kotlinx.coroutines.runBlocking {
            repository.crearProceso(order.id, stages.single().id, "2026-10-11", null, 8.0, null, "Proceso local")
        }
        val detail = kotlinx.coroutines.runBlocking { repository.detalle(order.id) }
        assertEquals(order.id, detail.orden.id)
        assertEquals(1, detail.procesos.size)
        assertTrue(detail.procesos.single().id < 0)
        assertEquals("Proceso local", detail.procesos.single().observaciones)
        val process = detail.procesos.single()
        val costOptions = kotlinx.coroutines.runBlocking { repository.opcionesCostosProceso(order.id, process.id) }
        assertEquals("Energía", costOptions.categorias.single().nombre)
        assertEquals("NIO", costOptions.monedas.single().codigo)
        val cost = kotlinx.coroutines.runBlocking {
            repository.guardarCostoProceso(order.id, process.id, null, 6, "2026-10-11", "Energía del molino", 2.0, 12.5, 7, null)
        }
        assertTrue(cost.id < 0)
        assertEquals(25.0, cost.total)
        assertEquals(cost.id, kotlinx.coroutines.runBlocking { repository.listarCostosProceso(order.id, process.id).single().id })
        kotlinx.coroutines.runBlocking {
            repository.guardarCostoProceso(order.id, process.id, cost.id, 6, "2026-10-11", "Energía corregida", 3.0, 12.5, 7, null)
        }
        assertEquals("Energía corregida", kotlinx.coroutines.runBlocking { repository.listarCostosProceso(order.id, process.id).single().descripcion })
        kotlinx.coroutines.runBlocking { repository.eliminarCostoProceso(order.id, process.id, cost.id) }
        assertEquals(emptyList(), kotlinx.coroutines.runBlocking { repository.listarCostosProceso(order.id, process.id) })
        val materialOptions = kotlinx.coroutines.runBlocking { repository.opcionesMaterialProceso(order.id, process.id) }
        assertEquals("Cemento", materialOptions.productos.single().nombre)
        assertEquals(12.0, materialOptions.productos.single().existencia)
        val material = kotlinx.coroutines.runBlocking {
            repository.registrarMaterialProceso(order.id, process.id, 8, "2026-10-11", 4.0, "Mezcla")
        }
        assertTrue(material.id < 0)
        assertEquals(14.0, material.totalNio)
        assertEquals(material.id, kotlinx.coroutines.runBlocking { repository.listarMaterialesProceso(order.id, process.id).single().id })
        val afterConsumption = kotlinx.coroutines.runBlocking { repository.opcionesMaterialProceso(order.id, process.id) }
        assertEquals(8.0, afterConsumption.productos.single().existencia)
        val overdraw = runCatching {
            kotlinx.coroutines.runBlocking {
                repository.registrarMaterialProceso(order.id, process.id, 8, "2026-10-11", 9.0, null)
            }
        }.exceptionOrNull()
        assertTrue(overdraw?.message?.contains("No hay suficiente material") == true)
        kotlinx.coroutines.runBlocking { repository.eliminarMaterialProceso(order.id, process.id, material) }
        assertEquals(emptyList(), kotlinx.coroutines.runBlocking { repository.listarMaterialesProceso(order.id, process.id) })
        val afterDelete = kotlinx.coroutines.runBlocking { repository.opcionesMaterialProceso(order.id, process.id) }
        assertEquals(12.0, afterDelete.productos.single().existencia)
        assertEquals(7, sync.pendingCount(42))

        client.close()
        driver.close()
    }
}
