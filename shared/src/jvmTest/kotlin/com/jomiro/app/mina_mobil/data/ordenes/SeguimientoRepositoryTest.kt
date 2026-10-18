package com.jomiro.app.mina_mobil.data.ordenes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.data.*
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.network.ApiJson
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import kotlin.test.*

class SeguimientoRepositoryTest {
    @Test
    fun repairLocationAndWorkSurviveOfflineCorrectionsAndAreSentWithGlobalCost() = runBlocking {
        fixture { f ->
            f.save("repair.create", buildJsonObject { f.repair().forEach { (k,v) -> put(k,v) }; put("repair_location", "externa") })
            val repair = f.repo.reparacionesEditables(f.repo.cargar(15)).single()
            assertEquals("externa", repair.repairLocation)
            f.save("repair.update", buildJsonObject {
                f.repair().forEach { (k,v) -> put(k,v) }
                put("repair_id", repair.id); put("expected_version", 1); put("ended_at", "2026-10-16T14:00")
                put("repair_location", "interna"); put("work_description", "Cambio de rodamientos y alineación del motor.")
            })
            val corrected = f.repo.reparacionesEditables(f.repo.cargar(15)).single()
            assertEquals("interna", corrected.repairLocation)
            assertEquals("Cambio de rodamientos y alineación del motor.", corrected.workDescription)
            // Una operación antigua que no conoce estos campos conserva los datos locales.
            f.save("repair.update", buildJsonObject {
                f.repair().forEach { (k,v) -> put(k,v) }
                put("repair_id", repair.id); put("expected_version", 2); put("ended_at", "2026-10-16T15:00")
            })
            assertEquals(corrected.workDescription, f.repo.reparacionesEditables(f.repo.cargar(15)).single().workDescription)
            f.save("repair_cost.create", buildJsonObject { put("repair_id", repair.id); put("total_cost", 1250.50); put("descripcion", "Cambio de rodamientos") })
            assertEquals(50.0, f.repo.cargar(15).data.percent)
            f.sync.upload(42)
            val operations = ApiJson.instance.parseToJsonElement(f.uploads.single()).jsonObject.getValue("operations").jsonArray
            assertEquals("externa", operations[0].jsonObject.getValue("payload").jsonObject.getValue("repair_location").jsonPrimitive.content)
            assertEquals(1250.50, operations.last().jsonObject.getValue("payload").jsonObject.getValue("total_cost").jsonPrimitive.double)
        }
    }

    @Test
    fun offlineRepairClosureAndCostKeepDownloadedProgressUnchanged() = runBlocking {
        fixture { f ->
            val original = f.repo.cargar(15)
            f.save("repair.create", f.repair())
            val repair = f.repo.reparacionesEditables(f.repo.cargar(15)).single()
            assertTrue(repair.id < 0)
            assertNull(repair.end)
            f.save("repair.update", buildJsonObject {
                f.repair().forEach { (k,v) -> put(k,v) }
                put("repair_id", repair.id); put("expected_version", 1); put("ended_at", "2026-10-16T14:00")
            })
            f.save("repair_cost.create", buildJsonObject { put("repair_id", repair.id); put("cantidad", 1); put("costo_unitario", 500) })
            val pending = f.repo.cargar(15)
            assertEquals(original.data, pending.data, "La barra no se recalcula con cambios sin sincronizar")
            val edited = f.repo.reparacionesEditables(pending).single()
            assertEquals(2, edited.version)
            assertEquals("2026-10-16T14:00", edited.end)
            assertEquals(3, pending.pending.size)
            assertEquals(3L, f.sync.pendingCount(42))
            assertIs<OfflineSyncException>(runCatching { f.sync.download(42) }.exceptionOrNull())
        }
    }

    @Test
    fun deadlineProposalChangesTheFormButNotTheConfirmedTimeline() = runBlocking {
        fixture { f ->
            f.save("equipment.extend", buildJsonObject { put("assignment_id", 8); put("expected_end", "2026-10-18T08:00"); put("ended_at", "2026-10-19T08:00") })
            val data = f.repo.cargar(15)
            assertEquals("2026-10-19T08:00", data.formAssignments.single().end)
            assertEquals("2026-10-18T08:00", data.data.assignments.single().end)
            assertEquals(50.0, data.data.percent)
        }
    }

    @Test
    fun onlyUploadAndANewDownloadReplaceTheOfflineProgress() = runBlocking {
        fixture { f ->
            f.save("repair.create", f.repair())
            f.sync.upload(42)
            assertEquals(0L, f.sync.pendingCount(42))
            assertIs<OfflineSyncException>(runCatching { f.repo.cargar(15) }.exceptionOrNull())
            f.percent = 100
            f.sync.download(42)
            val fresh = f.repo.cargar(15)
            assertEquals(100.0, fresh.data.percent)
            assertTrue(fresh.pending.isEmpty())
        }
    }

    @Test
    fun uncertainUploadPreservesTheSameOperationAndBlocksDiscardOrDuplicateEntry() = runBlocking {
        fixture { f ->
            f.save("repair.create", f.repair())
            f.status = 503
            runCatching { f.sync.upload(42) }
            assertTrue(f.repo.cargar(15).hasUncertainChanges)
            assertIs<OfflineSyncException>(runCatching { f.repo.quitarPendientes(15) }.exceptionOrNull())
            assertIs<OrdenesException>(runCatching { f.save("repair.create", f.repair()) }.exceptionOrNull())
            f.status = 422; f.response = """{"rolled_back":true,"message":"Conflicto"}"""
            runCatching { f.sync.upload(42) }
            assertTrue(f.repo.cargar(15).hasUncertainChanges)
            f.status = 200; f.response = null
            f.sync.upload(42)
            assertEquals(1, f.uploads.distinct().size)
        }
    }

    @Test
    fun explicitRollbackAllowsDiscardingOnlyTrackingWithoutDeletingOtherWork() = runBlocking {
        fixture { f ->
            f.sync.enqueueOperation(42, "order.update", buildJsonObject { put("order_id", 15) })
            f.save("repair.create", f.repair())
            f.status = 422; f.response = """{"rolled_back":true,"message":"Otra persona corrigió el intervalo"}"""
            runCatching { f.sync.upload(42) }
            assertEquals("rejected", f.repo.pendientes(15).single().status)
            f.repo.quitarPendientes(15)
            assertTrue(f.repo.pendientes(15).isEmpty())
            assertEquals(1L, f.sync.pendingCount(42))
            assertEquals(50.0, f.repo.cargar(15).data.percent)
        }
    }

    @Test
    fun onlineRepairIsPersistedBeforeAutomaticUploadAndKeepsUnconfirmedState() = runBlocking {
        fixture { f ->
            f.sync.setConnectionMode(42, ConnectionMode.ONLINE)
            f.status = 503
            val result = f.save("repair.create", f.repair())
            assertTrue(result.contains("guardado en el teléfono"))
            assertEquals(1L, f.sync.pendingCount(42))
            assertEquals("unknown", f.repo.pendientes(15).single().status)
            f.status = 200
            f.sync.upload(42)
            assertEquals(1, f.uploads.distinct().size)
        }
    }

    @Test
    fun newLocalOrderAndAssignmentSupportRepairsWithoutInventingConfirmedProgress() = runBlocking {
        fixture { f ->
            val order = f.sync.enqueueOperation(42, "order.create", buildJsonObject { put("cliente_id", 1) }, "order") { id -> buildJsonObject { put("id", id); put("code", "LOCAL-OT") } }
            val assignment = f.sync.enqueueOperation(42, "equipment.assign", buildJsonObject { put("order_id", order) }, "equipment") { id -> buildJsonObject {
                put("id", id); put("order_id", order); put("process_id", -2); put("process", "LOCAL-P")
                put("equipment_name", "Molino"); put("equipment_code", "EQ1"); put("start_date", "2026-10-15T08:00"); put("end_date", "2026-10-18T08:00")
            } }
            val state = f.repo.cargar(order)
            assertNull(state.data.percent)
            assertNull(state.data.timeline)
            assertEquals(assignment, state.formAssignments.single().id)
            f.repo.guardar(order, "repair.create", buildJsonObject { f.repair().forEach { (k,v) -> put(k,v) }; put("assignment_id", assignment) }, "Reparación local")
            assertEquals(assignment, f.repo.reparacionesEditables(f.repo.cargar(order)).single().assignmentId)
        }
    }

    @Test
    fun overlappingLocalRepairIsRejectedBeforeItCanBlockTheUploadBatch() = runBlocking {
        fixture { f ->
            f.save("repair.create", f.repair())
            val error = runCatching { f.save("repair.create", f.repair()) }.exceptionOrNull()
            assertIs<OrdenesException>(error)
            assertTrue(error.message.orEmpty().contains("solapa"))
            assertEquals(1L, f.sync.pendingCount(42))
        }
    }

    @Test
    fun oldSnapshotAndInvalidIntervalGiveAnActionableErrorWithoutEnqueuing() = runBlocking {
        fixture { f ->
            assertIs<OrdenesException>(runCatching { f.save("repair.create", buildJsonObject { f.repair().forEach { (k,v) -> put(k,v) }; put("ended_at", "2026-10-15T08:00") }) }.exceptionOrNull())
            assertEquals(0L, f.sync.pendingCount(42))
            f.db.clientCacheQueries.saveOfflineSnapshot(42, """{"data":{"clients":[],"orders":[{"id":15}],"catalogs":{}}}""", "2026-10-15")
            assertTrue(runCatching { f.repo.cargar(15) }.exceptionOrNull()?.message.orEmpty().contains("descargue"))
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { block(f) } finally { f.client.close(); f.server.stop(0); f.driver.close() }
    }

    private class Fixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db: MinaDatabase
        val client = HttpClient(CIO)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val sessions: SessionRepository
        val sync: OfflineSyncRepository
        val repo: SeguimientoRepository
        var status = 200
        var response: String? = null
        var percent = 50
        val uploads = mutableListOf<String>()
        fun tracking() = """{"order_id":15,"order_code":"OT-15","generated_at":"2026-10-17T08:00:00Z","percent":$percent,"total_processes":2,"completed_processes":1,"can_register":true,"can_edit":true,"can_cost":true,"assignments":[{"id":8,"process_id":4,"process":"P1","equipment":"Molino","code":"EQ1","start":"2026-10-15T08:00","end":"2026-10-18T08:00","elapsed_percent":60}],"repairs":[]}"""
        fun snapshot() = """{"data":{"clients":[],"employees":[],"orders":[{"id":15,"tracking":${tracking()}}],"catalogs":{}}}"""
        init {
            MinaDatabase.Schema.create(driver); db = MinaDatabase(driver)
            sessions = SessionRepository(db)
            sessions.save(UserSession("token", null, 42, "Usuario", "test@local", listOf("admin"), listOf("proceso_equipo.edit"), 1, "Caja"))
            db.clientCacheQueries.saveOfflineSnapshot(42, snapshot(), "2026-10-17T08:00:00Z")
            server.createContext("/api/v1") { exchange ->
                val payload: String
                val code: Int
                when {
                    exchange.requestURI.path.endsWith("/offline/upload") -> {
                        val request = exchange.requestBody.bufferedReader().readText(); uploads += request
                        val count = ApiJson.instance.parseToJsonElement(request).jsonObject.getValue("operations").jsonArray.size
                        code = status; payload = response ?: """{"data":{"uploaded":$count}}"""
                    }
                    exchange.requestURI.path.endsWith("/offline/snapshot") -> { code = 200; payload = snapshot() }
                    else -> { code = 200; payload = """{"data":${tracking()}}""" }
                }
                val bytes = payload.toByteArray(); exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val base = "http://127.0.0.1:${server.address.port}/api/v1"
            sync = OfflineSyncRepository(client, base, sessions, db)
            sync.initializeConnectionMode(42)
            repo = SeguimientoRepository(client, base, sessions, sync)
        }
        fun repair() = buildJsonObject { put("assignment_id", 8); put("started_at", "2026-10-16T08:00"); put("reason", "Rodamiento dañado") }
        suspend fun save(type: String, body: JsonObject) = repo.guardar(15, type, body, "Cambio de prueba")
    }
}
