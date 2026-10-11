package com.jomiro.app.mina_mobil.data.clientes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.db.MinaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientesLocalCacheTest {
    @Test
    fun storesClientsLocallyAndKeepsEachUserCacheSeparate() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val database = MinaDatabase(driver)
        val client = HttpClient(CIO)
        val repository = ClientesRepository(client, "http://localhost/api/v1", SessionRepository(database), database)
        val cliente = Cliente(7, "Cliente local", "555-0100", "Dirección", true)

        repository.guardarLocales(42, listOf(cliente))

        assertEquals(listOf(cliente), repository.listarLocales(42))
        assertEquals(emptyList(), repository.listarLocales(99))

        client.close()
        driver.close()
    }
}
