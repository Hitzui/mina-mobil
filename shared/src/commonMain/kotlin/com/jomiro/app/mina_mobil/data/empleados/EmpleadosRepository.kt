package com.jomiro.app.mina_mobil.data.empleados

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
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
    private val offlineSync: OfflineSyncRepository,
) {
    suspend fun listar(busqueda: String): List<Empleado> {
        val session = sessions.current()
            ?: throw EmpleadosException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.snapshot(session.userId)?.get("employees")?.let { raw ->
            return runCatching { ApiJson.instance.decodeFromString<List<EmpleadoDto>>(raw.toString()).map { it.toModel() } }
                .getOrElse { throw EmpleadosException("La copia local de empleados está dañada. Descargue los datos nuevamente.") }
                .filter { empleado ->
                    busqueda.isBlank() || listOfNotNull(empleado.codigo, empleado.nombre, empleado.telefono)
                        .any { it.contains(busqueda.trim(), ignoreCase = true) }
                }
        }
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
internal data class EmpleadoDto(
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

internal fun EmpleadoDto.toModel() = Empleado(
    id = id,
    codigo = code,
    nombre = name,
    telefono = phone,
    tipo = employeeType,
    fechaIngreso = hireDate,
    activo = isActive,
)
