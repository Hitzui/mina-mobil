package com.jomiro.app.mina_mobil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.di.startAppKoin
import com.jomiro.app.mina_mobil.network.createApiClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.context.stopKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            val driver = AndroidSqliteDriver(MinaDatabase.Schema, applicationContext, "mina-mobile.db")
            startAppKoin(
                database = MinaDatabase(driver),
                httpClient = createApiClient(CIO.create()),
            )
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        if (isFinishing) stopKoin()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
