package com.jomiro.app.mina_mobil

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.di.startAppKoin
import com.jomiro.app.mina_mobil.network.createApiClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.context.GlobalContext

fun MainViewController() = ComposeUIViewController {
    if (GlobalContext.getOrNull() == null) {
        val driver = NativeSqliteDriver(MinaDatabase.Schema, "mina-mobile.db")
        startAppKoin(
            database = MinaDatabase(driver),
            httpClient = createApiClient(Darwin.create()),
        )
    }
    App()
}
