package com.jomiro.app.mina_mobil

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.di.startAppKoin
import com.jomiro.app.mina_mobil.network.createApiClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.context.GlobalContext
import java.io.File

fun main() = application {
    if (GlobalContext.getOrNull() == null) {
        val databaseFile = File(System.getProperty("user.home"), ".mina-mobil/mina-mobile.db")
        databaseFile.parentFile?.mkdirs()
        val newDatabase = !databaseFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        if (newDatabase) MinaDatabase.Schema.create(driver)
        startAppKoin(
            database = MinaDatabase(driver),
            httpClient = createApiClient(CIO.create()),
        )
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "MINA Móvil",
    ) {
        App()
    }
}
