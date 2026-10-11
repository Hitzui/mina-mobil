package com.jomiro.app.mina_mobil.di

import com.jomiro.app.mina_mobil.data.AuthRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.data.clientes.ClientesRepository
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.login.LoginViewModel
import com.jomiro.app.mina_mobil.network.resolvedApiBaseUrl
import com.jomiro.app.mina_mobil.screen.clientes.listado.ClientesListadoViewModel
import com.jomiro.app.mina_mobil.screen.ordenes.listado.OrdenesTrabajoViewModel
import io.ktor.client.HttpClient
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun startAppKoin(database: MinaDatabase, httpClient: HttpClient): KoinApplication =
    startKoin {
        modules(
            module {
                single { database }
                single { httpClient }
                single { resolvedApiBaseUrl() }
                single { SessionRepository(get()) }
                single { AuthRepository(get(), get(), get()) }
                single { ClientesRepository(get(), get(), get(), get()) }
                single { OrdenesRepository(get(), get(), get()) }
                viewModel { LoginViewModel(get()) }
                viewModel { ClientesListadoViewModel(get()) }
                viewModel { OrdenesTrabajoViewModel(get(), get()) }
            },
        )
    }
