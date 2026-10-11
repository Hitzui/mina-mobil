package com.jomiro.app.mina_mobil.data.clientes

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ClientesRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val database: MinaDatabase,
) {
    fun listarLocales(ownerId: Long): List<Cliente> = database.clientCacheQueries.selectCachedClients(ownerId).executeAsList().map { row ->
        Cliente(row.id, row.name, row.phone, row.address, row.is_active != 0L)
    }

    fun guardarLocales(ownerId: Long, clientes: List<Cliente>) {
        database.transaction {
            database.clientCacheQueries.clearCachedClients(ownerId)
            clientes.forEach { cliente ->
                database.clientCacheQueries.saveCachedClient(
                    ownerId = ownerId,
                    id = cliente.id,
                    name = cliente.nombre,
                    phone = cliente.telefono,
                    address = cliente.direccion,
                    isActive = if (cliente.activo) 1L else 0L,
                )
            }
        }
    }

    suspend fun listar(pagina: Int): PaginaClientes {
        val session = sessions.current() ?: throw ClientesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/clients"
        val response = client.get(endpoint) {
            url {
                parameters.append("page", pagina.toString())
                parameters.append("per_page", PAGE_SIZE.toString())
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            logClientesApiResponse(
                endpoint = endpoint,
                status = response.status.value,
                contentType = response.headers[HttpHeaders.ContentType],
                body = body,
                reason = "HTTP error",
            )
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw ClientesException(message ?: "No se pudo cargar la lista de clientes.")
        }

        return try {
            parseClientesPage(body)
        } catch (error: ClientesException) {
            val receivedUrl = response.call.request.url.toString()
            val contentType = response.headers[HttpHeaders.ContentType] ?: "ausente"
            val diagnostic = "HTTP ${response.status.value}, Content-Type $contentType, URL $receivedUrl"
            logClientesApiResponse(
                endpoint = receivedUrl,
                status = response.status.value,
                contentType = contentType,
                body = body,
                reason = error.message ?: "Invalid response",
            )
            throw ClientesException("${error.message} ($diagnostic)")
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

private fun logClientesApiResponse(
    endpoint: String,
    status: Int,
    contentType: String?,
    body: String,
    reason: String,
) {
    val normalizedBody = body.removePrefix("\uFEFF").trimStart()
    val parseResult = runCatching { ApiJson.instance.parseToJsonElement(normalizedBody) }
    val parsed = parseResult.getOrNull()
    val structure = when (parsed) {
        is JsonObject -> {
            val data = parsed["data"]
            val dataInfo = when (data) {
                is JsonArray -> "array(count=${data.size}, first_keys=${(data.firstOrNull() as? JsonObject)?.keys?.sorted()})"
                is JsonObject -> "object(keys=${data.keys.sorted()})"
                else -> data?.let { it::class.simpleName } ?: "missing"
            }
            "object(keys=${parsed.keys.sorted()}, data=$dataInfo, meta_keys=${(parsed["meta"] as? JsonObject)?.keys?.sorted()})"
        }

        is JsonArray -> "array(count=${parsed.size})"
        null -> {
            when {
                normalizedBody.startsWith("<") -> "non-JSON HTML response"
                normalizedBody.isBlank() -> "empty response body"
                else -> "non-JSON body"
            }
        }

        else -> "JSON ${parsed::class.simpleName}"
    }
    val parseError = parseResult.exceptionOrNull()?.let { it::class.simpleName }
    val rootType = parsed?.let {
        when (it) {
            is JsonObject -> "object"
            is JsonArray -> "array"
            is JsonPrimitive -> "primitive"
        }
    }

    println(
        "[MINA][Clientes] $reason; url=$endpoint; status=$status; " +
                "contentType=${contentType ?: "missing"}; structure=$structure; " +
                "rootType=${rootType ?: "invalid JSON"}; parseError=${parseError ?: "none"}; " +
                "bodyLength=${body.length}; startsWithBOM=${body.startsWith("\uFEFF")}",
    )
}

class ClientesException(message: String) : Exception(message)

internal fun parseClientesPage(responseBody: String): PaginaClientes {
    val normalizedBody = responseBody.removePrefix("\uFEFF").trimStart()
    val response = runCatching { ApiJson.instance.decodeFromString<ClientesPageDto>(normalizedBody) }
        .getOrElse { error ->
            val reason = when {
                normalizedBody.isBlank() -> "el cuerpo de respuesta está vacío"
                normalizedBody.startsWith("<") -> "el servidor devolvió HTML en lugar de JSON"
                else -> "no coincide con el formato esperado (${error::class.simpleName})"
            }
            throw ClientesException("No se pudo interpretar la respuesta de clientes: $reason.")
        }

    return PaginaClientes(
        clientes = response.data.map { it.toModel() },
        paginaActual = response.meta.currentPage,
        ultimaPagina = response.meta.lastPage,
        total = response.meta.total,
    )
}

@Serializable
internal data class ClientesPageDto(
    val data: List<ClienteDto>,
    val meta: ClientesMetaDto,
)

@Serializable
internal data class ClienteDto(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("is_active") val isActive: Boolean,
) {
    fun toModel() = Cliente(
        id = id,
        nombre = name,
        telefono = phone,
        direccion = address,
        activo = isActive,
    )
}

@Serializable
internal data class ClientesMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
)
