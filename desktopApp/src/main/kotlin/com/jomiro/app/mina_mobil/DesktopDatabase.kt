package com.jomiro.app.mina_mobil

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.db.MinaDatabase
import java.io.File
import java.sql.DriverManager

/** Opens the Desktop database and safely initializes or advances its schema. */
internal fun openMinaDatabase(databaseFile: File): OpenedMinaDatabase {
    databaseFile.parentFile?.mkdirs()
    val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
    val state = DriverManager.getConnection(jdbcUrl).use { connection ->
        connection.createStatement().use { statement ->
            val version = statement.executeQuery("PRAGMA user_version").use { result ->
                result.next()
                result.getLong(1)
            }
            val hasTables = statement.executeQuery(
                "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%')",
            ).use { result ->
                result.next()
                result.getBoolean(1)
            }
            DatabaseState(version, hasTables)
        }
    }

    val targetVersion = MinaDatabase.Schema.version
    check(state.version <= targetVersion) {
        "La base local (versión ${state.version}) es más reciente que esta versión de MINA Móvil ($targetVersion)."
    }

    val driver = JdbcSqliteDriver(jdbcUrl)
    try {
        when {
            state.version == 0L && !state.hasTables -> MinaDatabase.Schema.create(driver)
            state.version < targetVersion -> MinaDatabase.Schema.migrate(driver, state.version, targetVersion)
        }
        // SQLDelight no actualiza PRAGMA user_version cuando se usa directamente
        // con JdbcSqliteDriver. Guardarla evita recrear el esquema en cada inicio.
        driver.execute(null, "PRAGMA user_version = $targetVersion", 0)
        return OpenedMinaDatabase(MinaDatabase(driver), driver)
    } catch (error: Throwable) {
        driver.close()
        throw error
    }
}

internal data class OpenedMinaDatabase(val database: MinaDatabase, val driver: JdbcSqliteDriver)

private data class DatabaseState(val version: Long, val hasTables: Boolean)
