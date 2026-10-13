package com.jomiro.app.mina_mobil.data

import com.jomiro.app.mina_mobil.db.MinaDatabase
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.time.Clock
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OfflineSyncRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val database: MinaDatabase,
) {
    fun downloaded(ownerId: Long): Boolean = database.clientCacheQueries.getOfflineSnapshot(ownerId).executeAsOneOrNull() != null

    fun connectionMode(ownerId: Long): ConnectionMode =
        database.clientCacheQueries.selectConnectionMode(ownerId).executeAsOneOrNull()?.let(ConnectionMode::fromValue)
            ?: if (downloaded(ownerId)) ConnectionMode.OFFLINE else ConnectionMode.ONLINE

    fun initializeConnectionMode(ownerId: Long): ConnectionMode {
        val stored = database.clientCacheQueries.selectConnectionMode(ownerId).executeAsOneOrNull()
        if (stored != null) return ConnectionMode.fromValue(stored)
        val initialMode = if (downloaded(ownerId)) ConnectionMode.OFFLINE else ConnectionMode.ONLINE
        database.clientCacheQueries.saveConnectionMode(ownerId, initialMode.value)
        return initialMode
    }

    fun isOfflineMode(ownerId: Long): Boolean = connectionMode(ownerId) == ConnectionMode.OFFLINE

    fun offlineSnapshot(ownerId: Long): JsonObject? {
        if (!isOfflineMode(ownerId)) return null
        return snapshot(ownerId) ?: throw OfflineSyncException(
            "El modo Offline está seleccionado, pero no hay datos descargados. Descargue los datos antes de trabajar.",
        )
    }

    fun setConnectionMode(ownerId: Long, mode: ConnectionMode) {
        val storedMode = database.clientCacheQueries.selectConnectionMode(ownerId).executeAsOneOrNull()
        if (mode == connectionMode(ownerId)) {
            if (storedMode == null) database.clientCacheQueries.saveConnectionMode(ownerId, mode.value)
            return
        }
        if (mode == ConnectionMode.ONLINE && pendingCount(ownerId) > 0) {
            throw OfflineSyncException("Hay cambios offline pendientes. Súbalos antes de cambiar al modo Online.")
        }
        database.transaction {
            if (mode == ConnectionMode.ONLINE) {
                database.clientCacheQueries.clearOfflineSnapshot(ownerId)
                database.clientCacheQueries.clearCachedClients(ownerId)
                database.clientCacheQueries.clearOfflineEntities(ownerId)
            }
            database.clientCacheQueries.saveConnectionMode(ownerId, mode.value)
        }
    }

    fun snapshot(ownerId: Long): JsonObject? = database.clientCacheQueries.getOfflineSnapshot(ownerId)
        .executeAsOneOrNull()?.let { raw ->
            runCatching { ApiJson.instance.decodeFromString<JsonObject>(raw).getValue("data").jsonObject }.getOrNull()
        }

    fun localEntities(ownerId: Long): List<OfflineLocalEntity> = database.clientCacheQueries.listOfflineEntities(ownerId)
        .executeAsList().mapNotNull { row ->
            runCatching {
                OfflineLocalEntity(row.local_id, row.entity_type, ApiJson.instance.decodeFromString<JsonObject>(row.payload))
            }.getOrNull()
        }

    fun saveLocalEntity(ownerId: Long, id: Long, type: String, payload: JsonObject) {
        database.clientCacheQueries.saveOfflineEntity(ownerId, id, type, ApiJson.instance.encodeToString(payload))
    }

    fun pendingCount(ownerId: Long): Long = database.clientCacheQueries.countPendingSyncOperations(ownerId).executeAsOne()

    fun enqueueOperation(
        ownerId: Long,
        type: String,
        payload: JsonObject,
        localEntityType: String? = null,
        localEntityId: Long? = null,
        localEntityPayload: ((Long) -> JsonObject)? = null,
    ): Long {
        var localId = 0L
        database.transaction {
            localId = if (localEntityType != null) localEntityId
                ?: database.clientCacheQueries.nextOfflineEntityId(ownerId).executeAsOne() else 0L
            val payloadWithId = if (localEntityType != null) {
                buildJsonObject {
                    payload.forEach { (key, value) -> put(key, value) }
                    put("local_id", kotlinx.serialization.json.JsonPrimitive(localId))
                }
            } else payload
            val timestamp = Clock.System.now().toString()
            val operationKey = "${Random.nextLong().toULong().toString(16)}-${Random.nextLong().toULong().toString(16)}"
            database.clientCacheQueries.enqueueSyncOperation(ownerId, type, operationKey, ApiJson.instance.encodeToString(payloadWithId), timestamp)
            if (localEntityType != null) {
                val entity = localEntityPayload?.invoke(localId) ?: payloadWithId
                database.clientCacheQueries.saveOfflineEntity(ownerId, localId, localEntityType, ApiJson.instance.encodeToString(entity))
            }
        }
        return localId
    }

    suspend fun download(ownerId: Long) {
        val session = sessions.current() ?: throw OfflineSyncException("La sesión expiró. Inicia sesión nuevamente.")
        var page = 1
        var lastPage = 1
        var firstData: JsonObject? = null
        var firstMeta: JsonObject? = null
        val allOrders = mutableListOf<JsonElement>()
        while (page <= lastPage) {
            val response = client.get("${baseUrl.trimEnd('/')}/offline/snapshot") {
                timeout { requestTimeoutMillis = 120_000; socketTimeoutMillis = 120_000 }
                url {
                    parameters.append("page", page.toString())
                    parameters.append("per_page", "25")
                }
                headers {
                    set(HttpHeaders.Authorization, "Bearer ${session.token}")
                    set(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) throw OfflineSyncException(apiMessage(body))
            val pageRoot = runCatching { ApiJson.instance.parseToJsonElement(body).jsonObject }
                .getOrElse { throw OfflineSyncException("El servidor devolvió una descarga local inválida.") }
            val data = pageRoot["data"]?.jsonObject
                ?: throw OfflineSyncException("La descarga no contiene los datos requeridos.")
            if (firstData == null) {
                firstData = data
                firstMeta = pageRoot["meta"]?.jsonObject
            }
            allOrders += data["orders"]?.jsonArray.orEmpty()
            lastPage = pageRoot["meta"]?.jsonObject?.get("orders")?.jsonObject
                ?.get("last_page")?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            page++
        }
        val originalData = firstData ?: throw OfflineSyncException("La descarga no devolvió datos.")
        if (originalData["clients"] !is JsonArray || originalData["employees"] !is JsonArray ||
            originalData["orders"] !is JsonArray || originalData["catalogs"] !is JsonObject
        ) {
            throw OfflineSyncException("La descarga está incompleta. No se reemplazaron los datos locales.")
        }
        val data = buildJsonObject {
            originalData.forEach { (key, value) -> if (key != "orders") put(key, value) }
            put("orders", JsonArray(allOrders))
        }
        val cachedClients = runCatching {
            ApiJson.instance.decodeFromString<OfflineSnapshotEnvelope>(ApiJson.instance.encodeToString(buildJsonObject {
                put("data", data)
            })).data.clients
        }.getOrElse {
            throw OfflineSyncException("La lista local de clientes está incompleta. No se reemplazaron los datos locales.")
        }
        val body = ApiJson.instance.encodeToString(buildJsonObject {
            put("data", data)
            firstMeta?.let { put("meta", it) }
        })
        val timestamp = Clock.System.now().toString()
        database.transaction {
            database.clientCacheQueries.clearCachedClients(ownerId)
            database.clientCacheQueries.clearOfflineEntities(ownerId)
            database.clientCacheQueries.clearPendingSyncOperations(ownerId)
            database.clientCacheQueries.saveOfflineSnapshot(ownerId, body, timestamp)
            cachedClients.forEach { item ->
                database.clientCacheQueries.saveCachedClient(
                    ownerId = ownerId,
                    id = item.id,
                    name = item.name,
                    phone = item.phone,
                    address = item.address,
                    isActive = if (item.isActive) 1L else 0L,
                )
            }
        }
    }

    suspend fun upload(ownerId: Long) {
        val session = sessions.current() ?: throw OfflineSyncException("La sesión expiró. Inicia sesión nuevamente.")
        val operations = database.clientCacheQueries.listPendingSyncOperations(ownerId).executeAsList()
            .map { row ->
                val payload = runCatching { ApiJson.instance.decodeFromString<JsonObject>(row.payload) }.getOrNull()
                PendingOperationDto(
                    localId = payload?.get("local_id")?.jsonPrimitive?.content?.toLongOrNull(),
                    operationKey = row.operation_key,
                    type = row.operation_type,
                    payload = payload ?: JsonObject(emptyMap()),
                )
            }
        val response = client.post("${baseUrl.trimEnd('/')}/offline/upload") {
            timeout { requestTimeoutMillis = 300_000; socketTimeoutMillis = 300_000 }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(UploadOperationsDto(operations)))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw OfflineSyncException(apiMessage(body))
        database.transaction {
            database.clientCacheQueries.clearPendingSyncOperations(ownerId)
            database.clientCacheQueries.clearOfflineSnapshot(ownerId)
            database.clientCacheQueries.clearCachedClients(ownerId)
            database.clientCacheQueries.clearOfflineEntities(ownerId)
        }
    }

    private fun apiMessage(body: String): String = runCatching {
        ApiJson.instance.decodeFromString<ApiError>(body).let { error ->
            error.errors.values.firstOrNull()?.firstOrNull()?.takeIf(String::isNotBlank)
                ?: error.message.takeIf(String::isNotBlank)
        }
    }.getOrNull() ?: "No se pudo completar la sincronización."
}

data class OfflineLocalEntity(val id: Long, val type: String, val payload: JsonObject)

class OfflineSyncException(message: String) : Exception(message)

enum class ConnectionMode(val value: String) {
    ONLINE("online"),
    OFFLINE("offline");

    companion object {
        fun fromValue(value: String): ConnectionMode = entries.firstOrNull { it.value == value } ?: ONLINE
    }
}

@Serializable
private data class UploadOperationsDto(val operations: List<PendingOperationDto>)

@Serializable
private data class PendingOperationDto(
    @SerialName("local_id") val localId: Long?,
    @SerialName("operation_key") val operationKey: String,
    val type: String,
    val payload: JsonObject,
)

@Serializable
private data class OfflineSnapshotEnvelope(val data: OfflineSnapshotClients)

@Serializable
private data class OfflineSnapshotClients(val clients: List<OfflineSnapshotClient> = emptyList())

@Serializable
private data class OfflineSnapshotClient(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
)
