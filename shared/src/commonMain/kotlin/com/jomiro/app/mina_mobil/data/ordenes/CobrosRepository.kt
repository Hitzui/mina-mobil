package com.jomiro.app.mina_mobil.data.ordenes

import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import kotlin.math.round

@Serializable
data class CobroOpcion(val id: Long, val name: String)

@Serializable
data class IngresoCobrable(
    val id: Long,
    val description: String,
    @SerialName("currency_id") val currencyId: Long,
    val currency: String? = null,
    val total: Double,
    val confirmed: Double = 0.0,
    @SerialName("mixed_currency") val mixedCurrency: Boolean = false,
    val pending: Double = 0.0,
) {
    val available: Double get() = round((total - confirmed - pending).coerceAtLeast(0.0) * 100) / 100
}

@Serializable
data class CobroHistorial(
    val id: Long, val code: String, val date: String? = null,
    val currency: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val amount: Double, val status: String, val reference: String? = null,
)

@Serializable
data class CobrosOrden(
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("order_code") val orderCode: String = "",
    @SerialName("client_id") val clientId: Long? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("status_code") val statusCode: Int = 1,
    @SerialName("cash_register") val cashRegister: CobroOpcion? = null,
    @SerialName("can_create") val canCreate: Boolean = false,
    @SerialName("payment_methods") val paymentMethods: List<CobroOpcion> = emptyList(),
    val currencies: List<CobroOpcion> = emptyList(),
    val incomes: List<IngresoCobrable> = emptyList(),
    val history: List<CobroHistorial> = emptyList(),
    val offline: Boolean = false,
    val pendingPayments: List<CobroPendiente> = emptyList(),
)

@Serializable
data class CobroPendiente(
    val id: Long, val payload: JsonObject, val currency: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("sync_status") val syncStatus: String = "never",
) {
    val amount: Double get() = payload.getValue("lines").jsonArray.sumOf { it.jsonObject.getValue("amount").jsonPrimitive.double }
}

@Serializable
private data class CobrosResponse(val data: CobrosOrden)

class CobrosRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val sync: OfflineSyncRepository,
) {
    fun pendientes(orderId: Long): List<CobroPendiente> {
        val session = sessions.current() ?: return emptyList()
        return sync.localEntities(session.userId).filter { it.type == "collection" }
            .filter { it.payload["order_id"]?.jsonPrimitive?.longOrNull == orderId }
            .map { ApiJson.instance.decodeFromString<CobroPendiente>(it.payload.toString()) }
    }

    suspend fun cargar(orderId: Long): CobrosOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró.")
        if (session.permissions.none { it == "cobros.view" || it == "cobros.create" }) throw OrdenesException("Necesita permiso para consultar o registrar cobros.")
        val offline = sync.isOfflineMode(session.userId)
        val state = if (offline) {
            val snapshot = sync.offlineSnapshot(session.userId)!!
            val downloadedOrder = snapshot["orders"]?.jsonArray?.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.longOrNull == orderId }?.jsonObject
            val order = sync.localEntities(session.userId).firstOrNull { it.type == "order" && it.id == orderId }?.payload
                ?: downloadedOrder
                ?: throw OrdenesException("La orden no está descargada en este teléfono.")
            val settings = snapshot["catalogs"]?.jsonObject?.get("collection_settings")?.takeUnless { it is JsonNull }
                ?: throw OrdenesException("La descarga no incluye cobros. Actualice su descarga cuando tenga conexión.")
            val downloaded = downloadedOrder?.get("collections")?.takeUnless { it is JsonNull }
                ?: if (orderId < 0) settings else throw OrdenesException("La descarga de esta OT no incluye sus cobros. Actualice su descarga cuando tenga conexión.")
            var local = ApiJson.instance.decodeFromString<CobrosOrden>(downloaded.toString())
            val clientInfo = order["client"]?.jsonObject
            local = local.copy(orderId = orderId, orderCode = order["code"]?.jsonPrimitive?.content.orEmpty(),
                clientId = clientInfo?.get("id")?.jsonPrimitive?.longOrNull,
                clientName = clientInfo?.get("name")?.jsonPrimitive?.contentOrNull,
                statusCode = order["status_code"]?.jsonPrimitive?.intOrNull ?: local.statusCode)
            val entities = sync.localEntities(session.userId)
            val deleted = entities.filter { it.type == "income_deleted" }.map { it.id }.toSet()
            val incomes = local.incomes.associateBy { it.id }.toMutableMap()
            entities.filter { it.type == "income" && it.payload["order_id"]?.jsonPrimitive?.longOrNull == orderId }.forEach { entity ->
                val p = entity.payload
                incomes[entity.id] = IngresoCobrable(entity.id, p["description"]?.jsonPrimitive?.contentOrNull ?: "Ingreso",
                    p.getValue("currency_id").jsonPrimitive.long, p["currency"]?.jsonPrimitive?.contentOrNull,
                    p.getValue("total").jsonPrimitive.double, incomes[entity.id]?.confirmed ?: 0.0,
                    incomes[entity.id]?.mixedCurrency ?: false)
            }
            local.copy(incomes = incomes.values.filter { it.id !in deleted }, offline = true)
        } else {
            val response = client.get("${baseUrl.trimEnd('/')}/orders/$orderId/collections") {
                headers { set(HttpHeaders.Authorization, "Bearer ${session.token}"); set(HttpHeaders.Accept, "application/json") }
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) throw OrdenesException(runCatching { ApiJson.instance.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull() ?: "No se pudieron consultar los cobros.")
            ApiJson.instance.decodeFromString<CobrosResponse>(body).data
        }
        val pending = pendientes(orderId)
        val reserved = pending.flatMap { it.payload.getValue("lines").jsonArray }.groupBy { it.jsonObject.getValue("income_id").jsonPrimitive.long }
        return state.copy(canCreate = state.canCreate && "cobros.create" in session.permissions,
            pendingPayments = pending, incomes = state.incomes.map { income -> income.copy(pending = reserved[income.id].orEmpty().sumOf { it.jsonObject.getValue("amount").jsonPrimitive.double }) })
    }

    suspend fun guardar(state: CobrosOrden, date: String, currencyId: Long, methodId: Long, lines: Map<Long, Double>, reference: String, observations: String): String {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró.")
        val current = cargar(state.orderId)
        if (!current.canCreate || current.statusCode == 0 || current.clientId == null || current.cashRegister == null) throw OrdenesException("Necesita una OT con cliente, una caja activa y permiso para registrar cobros.")
        if (current.pendingPayments.any { it.syncStatus == "unknown" }) throw OrdenesException("Hay un envío sin respuesta confirmada. Reintente subirlo antes de registrar otro pago.")
        if (lines.isEmpty() || lines.any { (id, amount) ->
            val income = current.incomes.firstOrNull { it.id == id }
            income == null || income.mixedCurrency || income.currencyId != currencyId || !amount.isFinite() || amount < 0.01 || amount > income.available + 0.005 || kotlin.math.abs(amount * 100 - round(amount * 100)) > 0.0001
        }) throw OrdenesException("Revise los importes: misma moneda del ingreso, máximo dos decimales y sin superar el saldo disponible.")
        val currency = current.currencies.firstOrNull { it.id == currencyId } ?: throw OrdenesException("Seleccione una moneda activa.")
        val method = current.paymentMethods.firstOrNull { it.id == methodId } ?: throw OrdenesException("Seleccione un método de pago activo.")
        val payload = buildJsonObject {
            put("order_id", current.orderId); put("client_id", current.clientId); put("cash_register_id", current.cashRegister.id)
            put("date", date); put("currency_id", currencyId); put("payment_method_id", methodId)
            put("reference", reference.trim()); put("observations", observations.trim())
            put("lines", JsonArray(lines.map { (id, amount) -> buildJsonObject { put("income_id", id); put("amount", amount) } }))
        }
        sync.enqueueOperation(session.userId, "collection.create", payload, "collection") { id -> buildJsonObject {
            put("id", id); put("order_id", current.orderId); put("payload", payload)
            put("currency", currency.name); put("payment_method", method.name); put("sync_status", "never")
        } }
        if (current.offline) return "Pago guardado en el teléfono, pendiente de sincronizar. Todavía no hay entrada confirmada en caja."
        return runCatching { sync.upload(session.userId) }
            .fold({ "Cobro confirmado y entrada de caja registrada." }, { "El pago quedó guardado pendiente de sincronizar. ${it.message} Reintente subir el mismo pendiente; no lo registre de nuevo." })
    }

    fun quitar(id: Long) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró.")
        sync.removePendingCollection(session.userId, id)
    }
}
