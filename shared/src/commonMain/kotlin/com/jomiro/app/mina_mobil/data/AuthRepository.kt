package com.jomiro.app.mina_mobil.data

import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AuthRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
) {
    suspend fun login(email: String, password: String): UserSession {
        val response = client.post("${baseUrl.trimEnd('/')}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email.trim(), password = password, deviceName = deviceName()))
        }
        val responseText = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(responseText).message }.getOrNull()
            throw LoginException(message ?: "No fue posible iniciar sesión. Verifica tus datos y vuelve a intentar.")
        }

        val payload = runCatching { ApiJson.instance.decodeFromString<LoginResponse>(responseText) }
            .getOrElse { throw LoginException("El servidor devolvió una respuesta que no se pudo interpretar.") }
        val data = payload.data ?: throw LoginException("La respuesta del servidor está incompleta.")
        val user = data.user
        val cashRegister = user.cashRegister
            ?: throw LoginException("Tu usuario no tiene una caja asignada. Contacta a administración.")
        if (data.token.isBlank()) throw LoginException("El servidor no devolvió un token de sesión.")

        return UserSession(
            token = data.token,
            expiresAt = data.expiresAt,
            userId = user.id,
            name = user.name,
            email = user.email,
            roles = user.roles,
            permissions = user.permissions,
            cashRegisterId = cashRegister.id,
            cashRegisterName = cashRegister.name,
        ).also(sessions::save)
    }

    fun currentSession(): UserSession? = sessions.current()

    suspend fun logout() {
        val session = sessions.current()
        sessions.clear()
        if (session != null) {
            runCatching {
                client.delete("${baseUrl.trimEnd('/')}/auth/logout") {
                    header("Authorization", "Bearer ${session.token}")
                }
            }
        }
    }

    private fun deviceName(): String = "MINA móvil"
}

class LoginException(message: String) : Exception(message)

@Serializable
private data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
internal data class LoginResponse(val data: LoginData? = null)

@Serializable
internal data class LoginData(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val user: ApiUser,
)

@Serializable
internal data class ApiUser(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    @SerialName("cash_register") val cashRegister: ApiCashRegister? = null,
)

@Serializable
internal data class ApiCashRegister(val id: Long, val name: String)

@Serializable
internal data class ApiError(val message: String = "")
