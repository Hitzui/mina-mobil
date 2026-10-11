package com.jomiro.app.mina_mobil.data.empleados

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

data class Empleado(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val telefono: String?,
    val tipo: String?,
    val fechaIngreso: String?,
    val activo: Boolean,
)

class EmpleadosRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
) {
    suspend fun listar(busqueda: String): List<Empleado> {
        val session = sessions.current()
            ?: throw EmpleadosException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/employees"
        val result = mutableListOf<Empleado>()
        var page = 1
        var lastPage = 1
        do {
            val response = client.get(endpoint) {
                url {
                    parameters.append("page", page.toString())
                    parameters.append("per_page", PAGE_SIZE.toString())
                    busqueda.trim().takeIf(String::isNotEmpty)?.let { parameters.append("q", it) }
                }
                headers {
                    set(HttpHeaders.Authorization, "Bearer ${session.token}")
                    set(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                    .getOrNull()?.takeIf(String::isNotBlank)
                throw EmpleadosException(message ?: "No se pudieron cargar los empleados (HTTP ${response.status.value}).")
            }
            val payload = runCatching { ApiJson.instance.decodeFromString<EmpleadosResponseDto>(body) }
                .getOrElse { throw EmpleadosException("No se pudo interpretar la lista de empleados.") }
            result += payload.data.map { it.toModel() }
            lastPage = payload.meta.lastPage
            page++
        } while (page <= lastPage)
        return result
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

class EmpleadosException(message: String) : Exception(message)

@Serializable
private data class EmpleadosResponseDto(val data: List<EmpleadoDto>, val meta: EmpleadosMetaDto)

@Serializable
private data class EmpleadoDto(
    val id: Long,
    val code: String,
    val name: String,
    val phone: String? = null,
    @SerialName("employee_type") val employeeType: String? = null,
    @SerialName("hire_date") val hireDate: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class EmpleadosMetaDto(
    @SerialName("last_page") val lastPage: Int,
)

private fun EmpleadoDto.toModel() = Empleado(
    id = id,
    codigo = code,
    nombre = name,
    telefono = phone,
    tipo = employeeType,
    fechaIngreso = hireDate,
    activo = isActive,
)
