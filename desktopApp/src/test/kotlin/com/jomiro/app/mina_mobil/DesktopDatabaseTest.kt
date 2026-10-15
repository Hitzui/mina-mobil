package com.jomiro.app.mina_mobil

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.db.MinaDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDatabaseTest {
    @Test
    fun opensAndReopensDatabaseWithCurrentSchemaVersion() {
        val databaseFile = Files.createTempFile("mina-desktop-", ".db").toFile().apply { delete() }

        val first = openMinaDatabase(databaseFile)
        first.database.clientCacheQueries.saveCachedClient(7, 3, "Cliente guardado", null, null, 1L)
        first.driver.close()

        val reopened = openMinaDatabase(databaseFile)
        assertEquals("Cliente guardado", reopened.database.clientCacheQueries.selectCachedClients(7).executeAsOne().name)
        reopened.driver.close()
        databaseFile.delete()
    }

    @Test
    fun migratesExistingUnversionedDatabaseWithoutRecreatingItsTables() {
        val databaseFile = Files.createTempFile("mina-desktop-legacy-", ".db").toFile()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        MinaDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO cached_client (owner_id, id, name, is_active) VALUES (9, 2, 'Cliente previo', 1)", 0)
        driver.close()

        val migrated = openMinaDatabase(databaseFile)
        assertEquals("Cliente previo", migrated.database.clientCacheQueries.selectCachedClients(9).executeAsOne().name)
        migrated.database.clientCacheQueries.saveConnectionMode(9, "offline")
        assertEquals("offline", migrated.database.clientCacheQueries.selectConnectionMode(9).executeAsOne())
        migrated.driver.close()
        databaseFile.delete()
    }

    @Test
    fun upgradesAnUnversionedDatabaseThatOnlyHasTheLegacyClientTable() {
        val databaseFile = Files.createTempFile("mina-desktop-cache-", ".db").toFile()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        driver.execute(
            null,
            "CREATE TABLE cached_client (owner_id INTEGER NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT, address TEXT, is_active INTEGER NOT NULL, PRIMARY KEY (owner_id, id))",
            0,
        )
        driver.execute(null, "INSERT INTO cached_client (owner_id, id, name, is_active) VALUES (10, 4, 'Cache antiguo', 1)", 0)
        driver.close()

        val migrated = openMinaDatabase(databaseFile)
        assertEquals("Cache antiguo", migrated.database.clientCacheQueries.selectCachedClients(10).executeAsOne().name)
        migrated.database.clientCacheQueries.saveOfflineSnapshot(10, "{}", "2026-10-13T00:00:00Z")
        assertEquals("{}", migrated.database.clientCacheQueries.getOfflineSnapshot(10).executeAsOne())
        migrated.driver.close()
        databaseFile.delete()
    }
}
