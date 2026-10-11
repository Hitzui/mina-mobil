package com.jomiro.app.mina_mobil.data.clientes

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

class ClientesRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
) {
    suspend fun listar(pagina: Int): PaginaClientes {
        val session = sessions.current() ?: throw ClientesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/clients"
        val response = client.get {
            url(endpoint) {
                parameters.append("page", pagina.toString())
                parameters.append("per_page", PAGE_SIZE.toString())
            }
            header("Authorization", "Bearer ${session.token}")
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
            logClientesApiResponse(
                endpoint = endpoint,
                status = response.status.value,
                contentType = response.headers[HttpHeaders.ContentType],
                body = body,
                reason = error.message ?: "Invalid response",
            )
            throw error
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
    val parsed = runCatching { ApiJson.instance.parseToJsonElement(body) }.getOrNull()
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
            val trimmed = body.trimStart()
            when {
                trimmed.startsWith("<") -> "non-JSON HTML response"
                body.isBlank() -> "empty response body"
                else -> "non-JSON body"
            }
        }

        else -> "JSON ${parsed::class.simpleName}"
    }
    val preview = if (parsed == null && !body.trimStart().startsWith("<")) {
        body.take(120).replace('\n', ' ').replace('\r', ' ')
    } else {
        null
    }

    println(
        "[MINA][Clientes] $reason; url=$endpoint; status=$status; " +
                "contentType=${contentType ?: "missing"}; structure=$structure" +
                (preview?.let { "; bodyPrefix=$it" } ?: ""),
    )
}

class ClientesException(message: String) : Exception(message)

internal fun parseClientesPage(responseBody: String): PaginaClientes {
    val root = runCatching { ApiJson.instance.parseToJsonElement(responseBody).jsonObject }
        .getOrElse { throw ClientesException("La API de clientes no devolvió un objeto JSON válido.") }
    val data = (root["data"] as? JsonArray)
        ?: ((root["data"] as? JsonObject)?.get("data") as? JsonArray)
        ?: throw ClientesException("La respuesta de clientes no contiene una lista de datos.")
    val clients = data.mapNotNull { element ->
        val fields = element as? JsonObject ?: return@mapNotNull null
        val id = fields.long("id", "cliente_id") ?: return@mapNotNull null
        val name = fields.string("name", "nombre") ?: return@mapNotNull null

        Cliente(
            id = id,
            nombre = name,
            telefono = fields.string("phone", "telefono"),
            direccion = fields.string("address", "direccion"),
            activo = fields.boolean("is_active", "estado", "active") ?: true,
        )
    }
    val meta = root["meta"] as? JsonObject ?: root
    val currentPage = meta.long("current_page", "currentPage")?.toInt() ?: 1
    val lastPage = meta.long("last_page", "lastPage")?.toInt() ?: currentPage
    val total = meta.long("total")?.toInt() ?: clients.size

    return PaginaClientes(
        clientes = clients,
        paginaActual = currentPage,
        ultimaPagina = lastPage,
        total = total,
    )
}

private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    this[key].asStringOrNull()
}

private fun JsonObject.long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
    (this[key] as? JsonPrimitive)?.longOrNull
        ?: this[key].asStringOrNull()?.toLongOrNull()
}

private fun JsonObject.boolean(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
    val element = this[key] ?: return@firstNotNullOfOrNull null
    (element as? JsonPrimitive)?.booleanOrNull
        ?: element.asStringOrNull()?.let { value ->
            when (value.lowercase()) {
                "1", "true", "yes", "si", "sí" -> true
                "0", "false", "no" -> false
                else -> null
            }
        }
}

private fun JsonElement?.asStringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull
    else -> null
}
