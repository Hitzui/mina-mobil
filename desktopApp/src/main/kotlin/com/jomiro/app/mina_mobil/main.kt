package com.jomiro.app.mina_mobil

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.di.startAppKoin
import com.jomiro.app.mina_mobil.network.createApiClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.context.GlobalContext
import java.io.File
import java.sql.DriverManager

fun main() = application {
    if (GlobalContext.getOrNull() == null) {
        val databaseFile = File(System.getProperty("user.home"), ".mina-mobil/mina-mobile.db")
        databaseFile.parentFile?.mkdirs()
        val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
        val version = DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }
        val driver = JdbcSqliteDriver(jdbcUrl)
        if (version == 0) {
            MinaDatabase.Schema.create(driver)
        } else if (version < MinaDatabase.Schema.version) {
            MinaDatabase.Schema.migrate(driver, version.toLong(), MinaDatabase.Schema.version)
        }
        startAppKoin(
            database = MinaDatabase(driver),
            httpClient = createApiClient(CIO.create()),
        )
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "MINA Móvil",
        icon = painterResource("icon.png"),
    ) {
        App()
    }
}
