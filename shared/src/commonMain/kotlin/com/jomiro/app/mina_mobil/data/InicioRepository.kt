package com.jomiro.app.mina_mobil.data

import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PeriodoInicio(val key: String, val label: String) {
    MES("mes", "Este mes"),
    ANIO("anio", "Este año"),
    TODO("todo", "Todo"),
}

data class OrdenInicio(
    val id: Long,
    val codigo: String,
    val cliente: String,
    val estado: String,
    val procesosTerminados: Int,
    val procesosTotal: Int,
)

data class ProcesoAtrasadoInicio(
    val codigo: String,
    val etapa: String,
    val orden: String,
    val dias: Int?,
    val ordenId: Long? = null,
)

data class PanelInicio(
    val periodo: PeriodoInicio,
    val offline: Boolean,
    val fechaDescarga: String? = null,
    val pendingOperations: Long = 0,
    val ordenesAbiertas: Int = 0,
    val procesosEnCurso: Int = 0,
    val procesosAtrasados: List<ProcesoAtrasadoInicio> = emptyList(),
    val ordenes: List<OrdenInicio> = emptyList(),
    val costosNio: Double = 0.0,
    val costosSinEquivalente: Int = 0,
    val ingresosNio: Double = 0.0,
    val ingresosSinEquivalente: Int = 0,
    val ordenesConGastosGeneralesPendientes: Int = 0,
    val gastosGeneralesPendientesNio: Double = 0.0,
)

class InicioRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val offlineSync: OfflineSyncRepository,
) {
    suspend fun cargar(ownerId: Long, periodo: PeriodoInicio): PanelInicio {
        val session = sessions.current()
            ?: throw InicioException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(ownerId)) {
            val snapshot = offlineSync.offlineSnapshot(ownerId)
                ?: throw InicioException("No hay datos descargados. Conéctese y descargue los datos para consultar Inicio Offline.")
            return panelDesdeDescarga(
                snapshot = snapshot,
                fechaDescarga = offlineSync.snapshotDownloadedAt(ownerId),
                pendingOperations = offlineSync.pendingCount(ownerId),
            )
        }

        val response = client.get("${baseUrl.trimEnd('/')}/app/dashboard?periodo=${periodo.key}") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw InicioException(message ?: "No se pudo cargar el panel del taller.")
        }
        val data = runCatching { ApiJson.instance.decodeFromString<JsonObject>(body).getValue("data").jsonObject }
            .getOrElse { throw InicioException("No se pudo interpretar el panel del taller.") }
        return panelDesdeApi(data, periodo)
    }
}

class InicioException(message: String) : Exception(message)

internal fun panelDesdeApi(data: JsonObject, periodo: PeriodoInicio): PanelInicio {
    val money = data.obj("money")
    val orderStatuses = data.array("orders_by_status")
    val processStatuses = data.array("processes_by_status")
    val openOrders = data.array("open_orders").map(::orderFromJson)
    val activeProcesses = processStatuses.sumOf { status ->
        val row = status.jsonObject
        val label = row.stringValue("texto").orEmpty().lowercase()
        if (label == "en proceso") row.intValue("cuantas") ?: 0 else 0
    }
    return PanelInicio(
        periodo = periodo,
        offline = false,
        ordenesAbiertas = orderStatuses.filter { it.jsonObject.booleanValue("abierta") == true }
            .sumOf { it.jsonObject.intValue("cuantas") ?: 0 },
        procesosEnCurso = activeProcesses,
        procesosAtrasados = data.array("overdue_processes").take(4).map(::overdueFromJson),
        ordenes = openOrders,
        costosNio = money.doubleValue("costo_total") ?: 0.0,
        costosSinEquivalente = money.intValue("costo_sin_equivalente") ?: 0,
        ingresosNio = money.doubleValue("ingresos") ?: 0.0,
        ingresosSinEquivalente = money.intValue("ingresos_sin_equivalente") ?: 0,
    )
}

internal fun panelDesdeDescarga(
    snapshot: JsonObject,
    fechaDescarga: String?,
    pendingOperations: Long,
): PanelInicio {
    val orders = snapshot.array("orders").map { it.jsonObject }
    val orderCards = orders.map(::offlineOrderFromJson)
    val openOrders = orderCards.filter { it.estado.lowercase() in setOf("pendiente", "en proceso", "en progreso") }
    var runningProcesses = 0
    val overdue = mutableListOf<ProcesoAtrasadoInicio>()
    var directCosts = 0.0
    var generalCosts = 0.0
    var generalMissing = 0
    var directMissing = 0
    var income = 0.0
    var incomeMissing = 0
    var generalPending = 0.0
    var ordersPending = 0

    orders.forEach { order ->
        val orderCode = order.stringValue("code", "codigo").orEmpty()
        val processes = order.array("processes")
        val processCostAmounts = mutableMapOf<String, Double>()
        var orderDirectMissing = 0

        processes.forEach { processElement ->
            val process = processElement.jsonObject
            val processCode = process.stringValue("code", "codigo").orEmpty()
            val status = process.stringValue("status", "estado").orEmpty()
            val start = process.stringValue("start_date", "fecha_inicio")
            val end = process.stringValue("end_date", "fecha_fin")
            if (status.contains("proceso", ignoreCase = true) || status.contains("curso", ignoreCase = true)) {
                runningProcesses++
                if (start != null && end == null) {
                    overdue += ProcesoAtrasadoInicio(
                        codigo = processCode,
                        etapa = process.stringValue("stage", "etapa").orEmpty(),
                        orden = orderCode,
                        dias = null,
                        ordenId = order.longValue("id"),
                    )
                }
            }

            var processCost = 0.0
            process.array("costs").forEach { cost ->
                val amount = cost.jsonObject.objValue("total_nio", "totalNio")?.toDoubleOrNull()
                if (amount == null) orderDirectMissing++ else processCost += amount
            }
            process.array("materials").forEach { material ->
                val amount = material.jsonObject.objValue("total_nio", "totalNio")?.toDoubleOrNull()
                if (amount == null) orderDirectMissing++ else processCost += amount
            }
            processCostAmounts[processCode] = processCost
        }

        order.array("employee_work").forEach { work ->
            val row = work.jsonObject
            val amount = row.objValue("total_nio", "totalNio")?.toDoubleOrNull()
            if (amount == null) orderDirectMissing++
            else {
                val processCode = row.stringValue("process", "proceso")
                if (processCode != null) processCostAmounts[processCode] = (processCostAmounts[processCode] ?: 0.0) + amount
            }
        }
        order.array("equipment").forEach { equipment ->
            val row = equipment.jsonObject
            val amount = row.doubleValue("total_cost_nio", "costo_uso_nio", "costo_total_nio", "total_depreciation", "depreciacion_total") ?: 0.0
            val processCode = row.stringValue("process", "proceso")
            if (processCode != null) processCostAmounts[processCode] = (processCostAmounts[processCode] ?: 0.0) + amount
        }
        processCostAmounts.forEach { (code, amount) ->
            directCosts += amount
        }

        order.array("costs").filter { it.jsonObject.stringValue("process", "proceso").isNullOrBlank() }.forEach { cost ->
            val amount = cost.jsonObject.objValue("total_nio", "totalNio")?.toDoubleOrNull()
            if (amount == null) generalMissing++ else generalCosts += amount
        }
        order.array("incomes").forEach { incomeRow ->
            val amount = incomeRow.jsonObject.objValue("total_nio", "totalNio")?.toDoubleOrNull()
            if (amount == null) incomeMissing++ else income += amount
        }

        val orderGeneralCosts = order.array("costs").filter {
            it.jsonObject.stringValue("process", "proceso").isNullOrBlank()
        }.mapNotNull { it.jsonObject.objValue("total_nio", "totalNio")?.toDoubleOrNull() }.sum()
        val directTotal = processCostAmounts.values.sum()
        if (orderGeneralCosts > 0 && (orderDirectMissing > 0 || directTotal <= 0.0)) {
            ordersPending++
            generalPending += orderGeneralCosts
        }
        directMissing += orderDirectMissing
    }

    return PanelInicio(
        periodo = PeriodoInicio.TODO,
        offline = true,
        fechaDescarga = fechaDescarga,
        pendingOperations = pendingOperations,
        ordenesAbiertas = openOrders.size,
        procesosEnCurso = runningProcesses,
        procesosAtrasados = overdue.sortedBy { it.codigo }.take(4),
        ordenes = openOrders.take(6),
        costosNio = directCosts + generalCosts,
        costosSinEquivalente = directMissing + generalMissing,
        ingresosNio = income,
        ingresosSinEquivalente = incomeMissing,
        ordenesConGastosGeneralesPendientes = ordersPending,
        gastosGeneralesPendientesNio = generalPending,
    )
}

private fun orderFromJson(element: JsonElement): OrdenInicio {
    val row = element.jsonObject
    val rawStatus = row.stringValue("estado", "status").orEmpty()
    val status = when (rawStatus.toIntOrNull()) {
        0 -> "Cancelada"
        1 -> "Pendiente"
        2 -> "En proceso"
        3 -> "Finalizada"
        else -> rawStatus
    }
    return OrdenInicio(
        id = row.longValue("id") ?: 0L,
        codigo = row.stringValue("codigo", "code").orEmpty(),
        cliente = row.objectString("cliente", "client", "nombre", "name") ?: "Sin cliente",
        estado = status,
        procesosTerminados = row.intValue("procesos_terminados", "processes_completed") ?: 0,
        procesosTotal = row.intValue("procesos_total", "processes_total") ?: 0,
    )
}

private fun offlineOrderFromJson(row: JsonObject): OrdenInicio {
    val processes = row.array("processes")
    val completed = processes.count {
        it.jsonObject.stringValue("status", "estado").equals("Finalizado", ignoreCase = true)
    }
    val client = row["client"]?.let { element ->
        runCatching { element.jsonObject.stringValue("name", "nombre") }.getOrNull()
            ?: element.jsonPrimitive.contentOrNull
    }
    return OrdenInicio(
        id = row.longValue("id") ?: 0L,
        codigo = row.stringValue("code", "codigo").orEmpty(),
        cliente = client ?: "Sin cliente",
        estado = row.stringValue("status", "estado").orEmpty(),
        procesosTerminados = completed,
        procesosTotal = processes.size,
    )
}

private fun overdueFromJson(element: JsonElement): ProcesoAtrasadoInicio {
    val row = element.jsonObject
    return ProcesoAtrasadoInicio(
        codigo = row.stringValue("codigo", "code").orEmpty(),
        etapa = row.objectString("etapa", "stage", "nombre", "name") ?: "Proceso",
        orden = row.objectString("orden_trabajo", "order", "codigo", "code")
            ?: row.stringValue("orden", "order_code").orEmpty(),
        dias = row.intValue("dias_atraso", "days_overdue"),
        ordenId = row.objectLong("orden_trabajo", "order", "id"),
    )
}

private fun JsonObject.array(key: String): List<JsonElement> =
    runCatching { this[key]?.jsonArray?.toList() }.getOrNull().orEmpty()

private fun JsonObject.obj(key: String): JsonObject =
    runCatching { this[key]?.jsonObject }.getOrNull() ?: JsonObject(emptyMap())

private fun JsonObject.stringValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

private fun JsonObject.objValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    val value = this[key] ?: return@firstNotNullOfOrNull null
    runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
}

private fun JsonObject.intValue(vararg keys: String): Int? = objValue(*keys)?.toIntOrNull()
private fun JsonObject.longValue(vararg keys: String): Long? = objValue(*keys)?.toLongOrNull()
private fun JsonObject.doubleValue(vararg keys: String): Double? = objValue(*keys)?.toDoubleOrNull()
private fun JsonObject.booleanValue(key: String): Boolean? = objValue(key)?.toBooleanStrictOrNull()

private fun JsonObject.objectString(containerKey: String, alternateContainerKey: String, vararg valueKeys: String): String? {
    val container = this[containerKey] ?: this[alternateContainerKey] ?: return null
    return runCatching { container.jsonObject.stringValue(*valueKeys) }.getOrNull()
}

private fun JsonObject.objectLong(containerKey: String, alternateContainerKey: String, valueKey: String): Long? {
    val container = this[containerKey] ?: this[alternateContainerKey] ?: return null
    return runCatching { container.jsonObject.longValue(valueKey) }.getOrNull()
}
