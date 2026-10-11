package com.jomiro.app.mina_mobil.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jomiro.app.mina_mobil.db.MinaDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionRepositoryTest {
    @Test
    fun savesAndRestoresTheSessionAndAssignedCashRegister() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MinaDatabase.Schema.create(driver)
        val repository = SessionRepository(MinaDatabase(driver))
        val session = UserSession(
            token = "token-value",
            expiresAt = "2026-11-10T12:00:00Z",
            userId = 42,
            name = "María",
            email = "maria@mina.test",
            roles = listOf("supervisor"),
            permissions = listOf("ordenes_trabajo.view", "cobros.view"),
            cashRegisterId = 7,
            cashRegisterName = "Caja principal",
        )

        repository.save(session)

        assertEquals(session, repository.current())
        repository.clear()
        assertNull(repository.current())
        driver.close()
    }
}
