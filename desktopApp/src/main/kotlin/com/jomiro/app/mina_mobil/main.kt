package com.jomiro.app.mina_mobil

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource
import com.jomiro.app.mina_mobil.di.startAppKoin
import com.jomiro.app.mina_mobil.network.createApiClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.context.GlobalContext
import java.io.File

fun main() = application {
    if (GlobalContext.getOrNull() == null) {
        val databaseFile = File(System.getProperty("user.home"), ".mina-mobil/mina-mobile.db")
        startAppKoin(
            database = openMinaDatabase(databaseFile).database,
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
