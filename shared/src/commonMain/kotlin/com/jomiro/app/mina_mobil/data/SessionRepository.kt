package com.jomiro.app.mina_mobil.data

import com.jomiro.app.mina_mobil.db.MinaDatabase
import kotlinx.serialization.json.Json

data class UserSession(
    val token: String,
    val expiresAt: String?,
    val userId: Long,
    val name: String,
    val email: String,
    val roles: List<String>,
    val permissions: List<String>,
    val cashRegisterId: Long,
    val cashRegisterName: String,
)

class SessionRepository(database: MinaDatabase) {
    private val queries = database.sessionQueries

    fun current(): UserSession? = queries.selectSession().executeAsOneOrNull()?.let { row ->
        UserSession(
            token = row.token,
            expiresAt = row.expires_at,
            userId = row.user_id,
            name = row.user_name,
            email = row.email,
            roles = Json.decodeFromString(row.roles_json),
            permissions = Json.decodeFromString(row.permissions_json),
            cashRegisterId = row.cash_register_id,
            cashRegisterName = row.cash_register_name,
        )
    }

    fun save(session: UserSession) {
        queries.saveSession(
            token = session.token,
            expiresAt = session.expiresAt,
            userId = session.userId,
            userName = session.name,
            email = session.email,
            rolesJson = Json.encodeToString(session.roles),
            permissionsJson = Json.encodeToString(session.permissions),
            cashRegisterId = session.cashRegisterId,
            cashRegisterName = session.cashRegisterName,
        )
    }

    fun clear() = queries.clearSession()
}
