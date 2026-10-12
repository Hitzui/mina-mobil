package com.jomiro.app.mina_mobil.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.db.MinaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OfflineSyncRepositoryTest {
    @Test
    fun migratesAnExistingDatabaseToOfflineStorage() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "CREATE TABLE cached_client (owner_id INTEGER NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT, address TEXT, is_active INTEGER NOT NULL, PRIMARY KEY (owner_id, id))", 0)
        MinaDatabase.Schema.migrate(driver, 2, 3)
        val database = MinaDatabase(driver)
        database.clientCacheQueries.saveOfflineSnapshot(10, "{\"data\":{}}", "2026-10-11T00:00:00Z")
        assertEquals("{\"data\":{}}", database.clientCacheQueries.getOfflineSnapshot(10).executeAsOne())
        driver.close()
    }

    @Test
    fun queuesOperationsWithTemporaryIdsAndKeepsEntityReferences() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val database = MinaDatabase(driver)
        val client = HttpClient(CIO)
        val repository = OfflineSyncRepository(client, "http://localhost/api/v1", SessionRepository(database), database)

        assertFalse(repository.downloaded(42))
        val orderId = repository.enqueueOperation(
            ownerId = 42,
            type = "order.create",
            payload = buildJsonObject { put("cliente_id", 5) },
            localEntityType = "order",
        ) { id -> buildJsonObject { put("id", id); put("code", "LOCAL-${-id}") } }
        val processId = repository.enqueueOperation(
            ownerId = 42,
            type = "process.create",
            payload = buildJsonObject { put("order_id", orderId); put("etapa_id", 3) },
            localEntityType = "process",
        ) { id -> buildJsonObject { put("id", id); put("order_id", orderId) } }

        assertEquals(-1L, orderId)
        assertEquals(-2L, processId)
        assertEquals(2, repository.pendingCount(42))
        assertEquals(listOf(-2L, -1L), repository.localEntities(42).map { it.id })
        assertEquals(setOf("order", "process"), repository.localEntities(42).map { it.type }.toSet())

        client.close()
        driver.close()
    }
}
