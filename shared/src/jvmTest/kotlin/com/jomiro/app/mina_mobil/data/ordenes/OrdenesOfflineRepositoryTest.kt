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

class OrdenesOfflineRepositoryTest {
    @Test
    fun createsOrderAndProcessLocallyAndKeepsTheirTemporaryRelationship() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val database = MinaDatabase(driver)
        val sessions = SessionRepository(database)
        sessions.save(
            UserSession("offline-token", null, 42, "Usuario", "offline@test.local", emptyList(), emptyList(), 1, "Caja"),
        )
        val body = """{"data":{"clients":[{"id":2,"name":"Cliente local","phone":null,"address":null,"is_active":true}],"employees":[],"orders":[],"catalogs":{"units":[{"id":3,"name":"Tonelada"}],"stages":[{"id":4,"name":"Trituración","order":1}],"employees":[],"payment_types":[],"equipment":[],"employee_rates":[],"exchange_rates":[],"cost_categories":[{"id":6,"name":"Energía"}],"currencies":[{"id":7,"code":"NIO","name":"Córdoba","is_base_currency":true}]}}}"""
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
        assertEquals(5, sync.pendingCount(42))

        client.close()
        driver.close()
    }
}
