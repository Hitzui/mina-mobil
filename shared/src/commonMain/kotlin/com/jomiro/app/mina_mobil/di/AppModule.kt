package com.jomiro.app.mina_mobil.di

import com.jomiro.app.mina_mobil.data.AuthRepository
import com.jomiro.app.mina_mobil.data.InicioRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import com.jomiro.app.mina_mobil.data.clientes.ClientesRepository
import com.jomiro.app.mina_mobil.data.empleados.EmpleadosRepository
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.login.LoginViewModel
import com.jomiro.app.mina_mobil.network.resolvedApiBaseUrl
import com.jomiro.app.mina_mobil.screen.clientes.listado.ClientesListadoViewModel
import com.jomiro.app.mina_mobil.screen.home.InicioViewModel
import com.jomiro.app.mina_mobil.screen.empleados.listado.EmpleadosViewModel
import com.jomiro.app.mina_mobil.screen.ordenes.listado.OrdenesTrabajoViewModel
import com.jomiro.app.mina_mobil.screen.ordenes.detalle.OrdenTrabajoDetalleViewModel
import com.jomiro.app.mina_mobil.screen.sync.OfflineSyncViewModel
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
                single { OfflineSyncRepository(get(), get(), get(), get()) }
                single { AuthRepository(get(), get(), get()) }
                single { InicioRepository(get(), get(), get(), get()) }
                single { ClientesRepository(get(), get(), get(), get(), get()) }
                single { EmpleadosRepository(get(), get(), get(), get()) }
                single { OrdenesRepository(get(), get(), get(), get()) }
                single { com.jomiro.app.mina_mobil.data.ordenes.SeguimientoRepository(get(), get(), get(), get()) }
                single { com.jomiro.app.mina_mobil.data.ordenes.CobrosRepository(get(), get(), get(), get()) }
                viewModel { LoginViewModel(get()) }
                viewModel { InicioViewModel(get()) }
                viewModel { ClientesListadoViewModel(get()) }
                viewModel { EmpleadosViewModel(get()) }
                viewModel { OrdenesTrabajoViewModel(get(), get()) }
                viewModel { OrdenTrabajoDetalleViewModel(get()) }
                viewModel { OfflineSyncViewModel(get()) }
            },
        )
    }
