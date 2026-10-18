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

class CobrosRepositoryTest {
    @Test
    fun offlineAbonosReserveTheLocalBalanceAndCannotErasePendingPaymentsByDownloading() = runBlocking {
        fixture { f ->
            val state = f.repo.cargar(15)
            assertEquals(6000.0, state.incomes.single().available)
            assertTrue(f.pay(2000.0).contains("pendiente de sincronizar"))
            val saved = f.repo.cargar(15)
            assertEquals(0.0, saved.incomes.single().confirmed)
            assertEquals(2000.0, saved.incomes.single().pending)
            assertEquals(4000.0, saved.incomes.single().available)
            assertEquals(1L, f.sync.pendingCount(42))
            assertIs<OrdenesException>(runCatching { f.pay(4000.01) }.exceptionOrNull())
            assertIs<OfflineSyncException>(runCatching { f.sync.download(42) }.exceptionOrNull())
            assertEquals(1L, f.sync.pendingCount(42))
            f.pay(4000.0)
            assertEquals(0.0, f.repo.cargar(15).incomes.single().available)
            assertEquals(0, f.uploads.size)
        }
    }

    @Test
    fun finalizedLocalOrderKeepsDownloadedConfirmedPaymentsAndUsesNegativePaymentIds() = runBlocking {
        fixture(confirmed = 2000.0) { f ->
            f.sync.saveLocalEntity(42, 15, "order", ApiJson.instance.parseToJsonElement("""{"id":15,"code":"OT-15","client":{"id":4,"name":"Cliente"},"status_code":3}""").jsonObject)
            assertEquals(4000.0, f.repo.cargar(15).incomes.single().available)
            f.pay(1000.0)
            val pending = f.repo.pendientes(15).single()
            assertTrue(pending.id < 0, "Una OT local positiva no debe producir un ID temporal positivo")
            assertEquals(3000.0, f.repo.cargar(15).incomes.single().available)
        }
    }

    @Test
    fun newOrderAndIncomeCanBeCollectedOfflineBeforeEitherHasAServerId() = runBlocking {
        fixture { f ->
            val orderId = f.sync.enqueueOperation(42, "order.create", buildJsonObject { put("cliente_id", 4) }, "order") { id ->
                ApiJson.instance.parseToJsonElement("""{"id":$id,"code":"LOCAL","client":{"id":4,"name":"Cliente"},"status_code":1}""").jsonObject
            }
            val incomeId = f.sync.enqueueOperation(42, "income.create", buildJsonObject { put("order_id", orderId) }, "income") { id -> buildJsonObject {
                put("id", id); put("order_id", orderId); put("description", "Servicio local")
                put("currency_id", 1); put("currency", "NIO"); put("total", 6000)
            } }
            val state = f.repo.cargar(orderId)
            f.repo.guardar(state, "2026-10-15", 1, 1, mapOf(incomeId to 2000.0), "", "")
            val payload = f.repo.pendientes(orderId).single().payload
            assertEquals(orderId, payload.getValue("order_id").jsonPrimitive.long)
            assertEquals(incomeId, payload.getValue("lines").jsonArray.single().jsonObject.getValue("income_id").jsonPrimitive.long)
            assertEquals(3L, f.sync.pendingCount(42))
            assertEquals(4000.0, f.repo.cargar(orderId).incomes.single().available)
        }
    }

    @Test
    fun unknownDeliveryKeepsTheSameKeyAndCannotBeDiscardedOrReentered() = runBlocking {
        fixture { f ->
            f.pay(2000.0)
            f.responseStatus = 503
            assertIs<OfflineSyncException>(runCatching { f.sync.upload(42) }.exceptionOrNull())
            val payment = f.repo.pendientes(15).single()
            assertEquals("unknown", payment.syncStatus)
            assertIs<OfflineSyncException>(runCatching { f.repo.quitar(payment.id) }.exceptionOrNull())
            assertIs<OrdenesException>(runCatching { f.pay(2000.0) }.exceptionOrNull())
            // Un rechazo posterior no prueba que el primer envío no llegara.
            f.responseStatus = 422; f.responseBody = """{"message":"Sin permiso actual","rolled_back":true}"""
            runCatching { f.sync.upload(42) }
            assertEquals("unknown", f.repo.pendientes(15).single().syncStatus)
            f.responseStatus = 200; f.responseBody = """{"data":{"uploaded":1}}"""
            f.sync.upload(42)
            assertEquals(3, f.uploads.size)
            assertEquals(1, f.uploads.distinct().size, "Los reintentos deben enviar exactamente la misma operación y clave")
            assertEquals(0L, f.sync.pendingCount(42))
            assertTrue(f.repo.pendientes(15).isEmpty())
        }
    }

    @Test
    fun explicitRollbackAllowsCorrectingThePendingWithoutLosingOtherOperations() = runBlocking {
        fixture { f ->
            f.sync.enqueueOperation(42, "order.update", buildJsonObject { put("order_id", 15) })
            f.pay(2000.0)
            f.responseStatus = 422; f.responseBody = """{"message":"El saldo cambió","rolled_back":true}"""
            runCatching { f.sync.upload(42) }
            val payment = f.repo.pendientes(15).single()
            assertEquals("rejected", payment.syncStatus)
            f.repo.quitar(payment.id)
            assertEquals(1L, f.sync.pendingCount(42))
            assertEquals(6000.0, f.repo.cargar(15).incomes.single().available)
            f.pay(1000.0)
            assertEquals(2L, f.sync.pendingCount(42))
        }
    }

    @Test
    fun onlinePaymentIsStoredBeforeSendingAndSurvivesAnUnconfirmedResponse() = runBlocking {
        fixture { f ->
            f.sync.setConnectionMode(42, ConnectionMode.ONLINE)
            f.responseStatus = 503
            val message = f.pay(2000.0)
            assertTrue(message.contains("no lo registre de nuevo"))
            assertEquals(1L, f.sync.pendingCount(42))
            assertEquals("unknown", f.repo.pendientes(15).single().syncStatus)
            f.responseStatus = 200; f.responseBody = """{"data":{"uploaded":1}}"""
            f.sync.upload(42)
            assertEquals(1, f.uploads.distinct().size)
            assertEquals(0L, f.sync.pendingCount(42))
        }
    }

    @Test
    fun anIncompleteSuccessfulHttpResponseDoesNotDiscardThePayment() = runBlocking {
        fixture { f ->
            f.pay(2000.0)
            f.responseBody = """{"data":{"uploaded":0}}"""
            assertIs<OfflineSyncException>(runCatching { f.sync.upload(42) }.exceptionOrNull())
            assertEquals(1L, f.sync.pendingCount(42))
            assertEquals("unknown", f.repo.pendientes(15).single().syncStatus)
            f.responseBody = """{"data":{"uploaded":1}}"""
            f.sync.upload(42)
            assertEquals(1, f.uploads.distinct().size)
            assertEquals(0L, f.sync.pendingCount(42))
        }
    }

    @Test
    fun mixedCurrencyAndInvalidAmountsCannotEnterTheMobileQueue() = runBlocking {
        fixture(mixed = true) { f ->
            assertIs<OrdenesException>(runCatching { f.pay(2000.0) }.exceptionOrNull())
            assertEquals(0L, f.sync.pendingCount(42))
        }
        fixture { f ->
            for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 2.001, 6000.01)) {
                assertIs<OrdenesException>(runCatching { f.pay(amount) }.exceptionOrNull())
            }
            assertEquals(0L, f.sync.pendingCount(42))
            f.database.clientCacheQueries.saveOfflineSnapshot(42, """{"data":{"orders":[{"id":15}],"catalogs":{}}}""", "2026-10-15")
            assertTrue(runCatching { f.repo.cargar(15) }.exceptionOrNull()?.message.orEmpty().contains("Actualice su descarga"))
        }
    }

    private suspend fun fixture(confirmed: Double = 0.0, mixed: Boolean = false, block: suspend (Fixture) -> Unit) {
        val f = Fixture(confirmed, mixed)
        try { block(f) } finally { f.close() }
    }

    private class Fixture(confirmed: Double, mixed: Boolean) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database: MinaDatabase
        val client = HttpClient(CIO)
        val sync: OfflineSyncRepository
        val repo: CobrosRepository
        val uploads = mutableListOf<String>()
        var responseStatus = 200
        var responseBody = """{"data":{"uploaded":1}}"""
        val settings = """"cash_register":{"id":1,"name":"Caja"},"can_create":true,"payment_methods":[{"id":1,"name":"Efectivo"}],"currencies":[{"id":1,"name":"NIO"}]"""
        val collections = """{"order_id":15,"order_code":"OT-15","client_id":4,"client_name":"Cliente","status_code":3,$settings,"incomes":[{"id":20,"description":"Molienda","currency_id":1,"currency":"NIO","total":6000,"confirmed":$confirmed,"mixed_currency":$mixed}]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            MinaDatabase.Schema.create(driver)
            database = MinaDatabase(driver)
            val sessions = SessionRepository(database)
            sessions.save(UserSession("token", null, 42, "Usuario", "cobro@test.local", emptyList(), listOf("cobros.view", "cobros.create", "ordenes_trabajo.view"), 1, "Caja"))
            database.clientCacheQueries.saveOfflineSnapshot(42, """{"data":{"clients":[],"orders":[{"id":15,"code":"OT-15","client":{"id":4,"name":"Cliente"},"status_code":3,"collections":$collections}],"catalogs":{"collection_settings":{$settings}}}}""", "2026-10-15T00:00:00Z")
            server.createContext("/api/v1/offline/upload") { exchange ->
                uploads += exchange.requestBody.bufferedReader().readText()
                val bytes = responseBody.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.createContext("/api/v1/orders/15/collections") { exchange ->
                val bytes = """{"data":$collections}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val url = "http://127.0.0.1:${server.address.port}/api/v1"
            sync = OfflineSyncRepository(client, url, sessions, database)
            repo = CobrosRepository(client, url, sessions, sync)
        }

        suspend fun pay(amount: Double) = repo.guardar(repo.cargar(15), "2026-10-15", 1, 1, mapOf(20L to amount), "Recibo", "Abono")
        fun close() { client.close(); server.stop(0); driver.close() }
    }
}
