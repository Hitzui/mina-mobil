package com.jomiro.app.mina_mobil.data.ordenes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.data.UserSession
import com.jomiro.app.mina_mobil.db.MinaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LamaRepositoryTest {
    @Test
    fun downloadedLotsKeepHistoricalOwnershipAndDisableEveryMutationOffline() = runBlocking {
        withSnapshot("""{"id":15,"code":"OT-LAMA-000003","status":"Pendiente","status_code":1,"work_type":"tratamiento_lama","lama":{"order_id":15,"order_code":"OT-LAMA-000003","is_treatment":true,"can_register":true,"can_start":true,"can_edit":true,"can_recover":true,"can_value":true,"lots":[{"id":3,"code":"LAMA-000003","source_id":12,"source_code":"OT-2026-0012","date":"2026-10-15","estimated_quantity":2.5,"unit":"Toneladas","location":"Depósito 2","holder":"empresa","company_percentage":70,"client_percentage":30,"treatment_id":15,"treatment_code":"OT-LAMA-000003","status":"Tratamiento Pendiente","recovered_grams":10,"company_grams":7,"recoveries":[{"id":8,"date":"2026-10-16","grams":10,"purity":0.8,"valued":true,"needs_valuation":true}],"result":{"company_gold_value_nio":0,"treatment_cost_nio":4000,"complete":false,"reference_result_nio":null}}]}}""") { repository, sync ->
            val lama = repository.lama(15)
            assertTrue(lama.offline)
            assertTrue(lama.isTreatment)
            assertFalse(lama.canRegister)
            assertFalse(lama.canStart)
            assertFalse(lama.canEdit)
            assertFalse(lama.canRecover)
            assertFalse(lama.canValue)
            val lot = lama.lots.single()
            assertEquals(70.0, lot.companyPercentage)
            assertEquals(2.5, lot.quantity)
            assertEquals(12L, lot.sourceId)
            assertTrue(lot.recoveries.single().needsValuation)
            assertNull(lot.result?.referenceResult)
            assertEquals("tratamiento_lama", repository.detalle(15).orden.tipoTrabajo)
            assertEquals("tratamiento_lama", repository.listar(1).ordenes.single().tipoTrabajo)
            for (method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete)) {
                val failure = runCatching { repository.lama(15, "lotes", method) }.exceptionOrNull()
                assertIs<OrdenesException>(failure)
                assertTrue(failure.message.orEmpty().contains("requieren Online"))
            }
            assertEquals(0L, sync.pendingCount(42))
        }
    }

    @Test
    fun oldDownloadsExplainWhyLamaNeedsADownloadRefresh() = runBlocking {
        withSnapshot("""{"id":15,"code":"OT-15","status":"Pendiente","status_code":1}""") { repository, sync ->
            assertEquals("servicio", repository.detalle(15).orden.tipoTrabajo)
            val failure = runCatching { repository.lama(15) }.exceptionOrNull()
            assertIs<OrdenesException>(failure)
            assertTrue(failure.message.orEmpty().contains("Actualice su descarga"))
            assertEquals(0L, sync.pendingCount(42))
        }
    }

    @Test
    fun agreementChangesKeepTheLamaConditionsInTheOfflineQueueAndLocalDetail() = runBlocking {
        withSnapshot("""{"id":15,"code":"OT-15","status":"Pendiente","status_code":1}""") { repository, sync ->
            repository.guardarAcuerdo(15, null, 2, null, null, 6000.0, 1, "Servicio con lama", true, "empresa", 70.0)
            val agreement = repository.detalle(15).acuerdos.single()
            assertEquals("empresa", agreement.lamaDestino)
            assertEquals(70.0, agreement.lamaPorcentajeEmpresa?.toDouble())
            assertEquals(6000.0, agreement.tarifaServicio?.toDouble())
            assertEquals(1L, sync.pendingCount(42))
        }
    }

    private suspend fun withSnapshot(order: String, block: suspend (OrdenesRepository, OfflineSyncRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val database = MinaDatabase(driver)
        val sessions = SessionRepository(database)
        sessions.save(UserSession("offline-token", null, 42, "Usuario", "lama@test.local", emptyList(), emptyList(), 1, "Caja"))
        database.clientCacheQueries.saveOfflineSnapshot(42, """{"data":{"clients":[],"orders":[$order],"catalogs":{"participation_types":[{"id":2,"name":"Servicio"}],"agreement_currencies":[{"id":1,"code":"NIO","name":"Córdoba","is_base_currency":true}]}}}""", "2026-10-15T00:00:00Z")
        val client = HttpClient(CIO)
        try {
            val sync = OfflineSyncRepository(client, "http://localhost/api/v1", sessions, database)
            block(OrdenesRepository(client, "http://localhost/api/v1", sessions, sync), sync)
        } finally {
            client.close()
            driver.close()
        }
    }
}
