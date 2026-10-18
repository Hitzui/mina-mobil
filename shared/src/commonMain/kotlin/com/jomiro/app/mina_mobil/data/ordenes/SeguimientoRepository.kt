package com.jomiro.app.mina_mobil.data.ordenes

import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.*
import kotlin.time.Clock

class SeguimientoRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val sync: OfflineSyncRepository,
) {
    suspend fun cargar(orderId: Long): EstadoSeguimiento {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró.")
        val offline = sync.isOfflineMode(session.userId)
        val raw = if (offline) {
            sync.offlineSnapshot(session.userId)?.get("orders")?.jsonArray
                ?.map { it.jsonObject }?.firstOrNull { it["id"]?.jsonPrimitive?.content?.toLongOrNull() == orderId }
                ?.get("tracking")
        } else {
            val response = client.get("${baseUrl.trimEnd('/')}/orders/$orderId/tracking") {
                headers { set(HttpHeaders.Authorization, "Bearer ${session.token}"); set(HttpHeaders.Accept, "application/json") }
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) throw OrdenesException(message(body))
            ApiJson.instance.parseToJsonElement(body).jsonObject.getValue("data")
        }
        val data = if (raw != null && raw !is JsonNull) ApiJson.instance.decodeFromString<SeguimientoOrden>(raw.toString()) else {
            val localOrder = sync.localEntities(session.userId).firstOrNull { it.type == "order" && it.id == orderId && orderId < 0 }
                ?: throw OrdenesException("Esta descarga no incluye seguimiento para la OT. Suba los pendientes y descargue datos actualizados.")
            val catalogs = sync.snapshot(session.userId)?.get("catalogs")?.jsonObject
            fun options(key: String) = catalogs?.get(key)?.jsonArray.orEmpty().map { OpcionSeguimiento(it.jsonObject.long("id"), it.jsonObject.text("name")) }
            val admin = "admin" in session.roles
            SeguimientoOrden(orderId, localOrder.payload.text("code"), "Pendiente de sincronización",
                timezone = catalogs?.text("tracking_timezone")?.ifBlank { "UTC" } ?: "UTC",
                canRegister = admin || "proceso_equipo.edit" in session.permissions,
                canEdit = admin || "proceso_equipo.edit" in session.permissions,
                canCost = admin || "movimientos_costo.create" in session.permissions,
                categories = options("cost_categories"), currencies = options("currencies"))
        }
        val localAssignments = if (offline) sync.localEntities(session.userId).filter { it.type == "equipment" && it.payload.long("order_id") == orderId }.map { e ->
            val p = e.payload
            AsignacionSeguimiento(e.id, p.long("process_id"), p.text("process"), p.text("equipment_name"), p.text("equipment_code"), p.text("start_date"), p.text("end_date").takeIf(String::isNotBlank))
        } else emptyList()
        val assignments = (data.assignments + localAssignments).associateBy { it.id }.toMutableMap()
        sync.localEntities(session.userId).filter { it.type == "tracking_pending" && it.payload.long("order_id") == orderId && it.payload.text("operation_type") == "equipment.extend" }
            .sortedByDescending { it.id }.forEach { e -> assignments[e.payload.long("assignment_id")]?.let { assignments[it.id] = it.copy(end = e.payload.text("ended_at")) } }
        val localClosed = sync.localEntities(session.userId).any { it.type == "order" && it.id == orderId && it.payload["status_code"]?.jsonPrimitive?.content?.toLongOrNull() in setOf(0L, 3L) }
        val displayed = if (localClosed) data.copy(canRegister = false, canCost = false) else data
        return EstadoSeguimiento(displayed, offline, sync.snapshotDownloadedAt(session.userId), pendientes(orderId), assignments.values.toList())
    }

    fun pendientes(orderId: Long): List<CambioSeguimiento> {
        val owner = sessions.current()?.userId ?: return emptyList()
        return sync.localEntities(owner).filter { it.type == "tracking_pending" && it.payload.long("order_id") == orderId }
            .map { CambioSeguimiento(it.id, it.payload.text("operation_type"), it.payload.text("description"), it.payload.text("sync_status").ifBlank { "never" }) }
    }

    /** Para los formularios se superponen los registros locales; la barra conserva el snapshot. */
    fun reparacionesEditables(state: EstadoSeguimiento): List<ReparacionEquipo> {
        val owner = sessions.current()?.userId ?: return state.data.repairs
        val repairs = state.data.repairs.associateBy { it.id }.toMutableMap()
        sync.localEntities(owner).filter { it.type == "tracking_pending" && it.payload.long("order_id") == state.data.orderId }.sortedByDescending { it.id }.forEach { e ->
            val p = e.payload
            val type = p.text("operation_type")
            val id = if (type == "repair.create") e.id else p.long("repair_id")
            if (type == "repair.delete") repairs.remove(id)
            if (type == "repair.create" || type == "repair.update") {
                val assignment = state.formAssignments.firstOrNull { it.id == p.long("assignment_id") }
                val old = repairs[id]
                repairs[id] = ReparacionEquipo(id, p.long("assignment_id"), if (type == "repair.create") 1 else p.long("expected_version").toInt() + 1,
                    p.text("started_at"), p.text("ended_at").takeIf(String::isNotBlank), p.text("reason"),
                    assignment?.equipment ?: old?.equipment.orEmpty(), assignment?.process ?: old?.process.orEmpty(), canCost = old?.canCost ?: assignment?.canRegister ?: false, costs = old?.costs.orEmpty(),
                    repairLocation = if (p.containsKey("repair_location")) p.text("repair_location").takeIf(String::isNotBlank) else old?.repairLocation,
                    workDescription = if (p.containsKey("work_description")) p.text("work_description").takeIf(String::isNotBlank) else old?.workDescription,
                    totalHours = null)
            }
        }
        return repairs.values.toList()
    }

    suspend fun guardar(orderId: Long, type: String, body: JsonObject, description: String): String {
        require(type in setOf("repair.create", "repair.update", "repair.delete", "repair_cost.create", "equipment.extend"))
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró.")
        val permission = if (type == "repair_cost.create") "movimientos_costo.create" else "proceso_equipo.edit"
        if ("admin" !in session.roles && permission !in session.permissions) throw OrdenesException("No tiene permiso para registrar este cambio.")
        if (type == "repair.create" || type == "repair.update") {
            val start = runCatching { LocalDateTime.parse(body.text("started_at")) }.getOrNull() ?: throw OrdenesException("Indique una fecha y hora de inicio válida.")
            val end = body.text("ended_at").takeIf(String::isNotBlank)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() ?: throw OrdenesException("Indique una fecha y hora de fin válida.") }
            if (end != null && end <= start) throw OrdenesException("El fin debe ser posterior al inicio.")
            if (body.text("reason").isBlank() || body.text("reason").length > 1000) throw OrdenesException("Indique el motivo de la reparación (máximo 1000 caracteres).")
            if (body.text("repair_location").isNotBlank() && body.text("repair_location") !in setOf("interna", "externa")) throw OrdenesException("Seleccione reparación interna o externa.")
            if (body.text("work_description").length > 2000) throw OrdenesException("La descripción del trabajo admite hasta 2000 caracteres.")
        }
        if (pendientes(orderId).any { it.status == "unknown" }) throw OrdenesException("Hay un envío sin confirmación. Suba el mismo pendiente antes de registrar otro cambio en esta OT.")
        if (sync.isOfflineMode(session.userId)) validarOffline(cargar(orderId), type, body)
        val payload = buildJsonObject { body.forEach { (k,v) -> put(k,v) }; put("order_id", orderId) }
        sync.enqueueOperation(session.userId, type, payload, "tracking_pending") { id ->
            buildJsonObject {
                payload.forEach { (k,v) -> put(k,v) }
                put("id", id); put("operation_type", type); put("description", description); put("sync_status", "never")
            }
        }
        if (sync.isOfflineMode(session.userId)) return "Cambio guardado pendiente de subida. La barra conserva la última descarga hasta subir y descargar de nuevo."
        return runCatching { sync.upload(session.userId) }.fold(
            onSuccess = { "Cambio sincronizado. Actualizando el progreso del servidor." },
            onFailure = { "Cambio guardado en el teléfono. Envío pendiente: ${it.message}. Reintente subir el mismo cambio desde Sincronización." },
        )
    }

    fun quitarPendientes(orderId: Long) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró.")
        sync.removePendingTracking(session.userId, orderId)
    }

    private fun validarOffline(state: EstadoSeguimiento, type: String, body: JsonObject) {
        val assignments = state.formAssignments
        val records = reparacionesEditables(state)
        val record = records.firstOrNull { it.id == body.long("repair_id") }
        val assignment = assignments.firstOrNull { it.id == body.long("assignment_id") }
            ?: record?.let { r -> assignments.firstOrNull { it.id == r.assignmentId } }
            ?: throw OrdenesException("No se encontró la asignación en los datos locales.")
        if (type != "repair.create" && type != "equipment.extend" && record == null) throw OrdenesException("No se encontró la reparación en los datos locales.")
        when (type) {
            "repair.create", "repair.update" -> {
                if (type == "repair.create" && (!state.data.canRegister || !assignment.canRegister)) throw OrdenesException("La OT o el proceso está cerrado, o no puede registrar reparaciones.")
                if (type == "repair.update" && (!state.data.canEdit || record?.version != body.long("expected_version").toInt())) throw OrdenesException("Revise la versión de la reparación antes de corregirla.")
                val start = LocalDateTime.parse(body.text("started_at"))
                val end = body.text("ended_at").takeIf(String::isNotBlank)?.let { LocalDateTime.parse(it) }
                val zone = TimeZone.of(state.data.timezone)
                if (start.toInstant(zone) > Clock.System.now() || end?.toInstant(zone)?.let { it > Clock.System.now() } == true) throw OrdenesException("La reparación no admite fechas futuras.")
                if (start < LocalDateTime.parse(assignment.start) || assignment.end?.let { start >= LocalDateTime.parse(it) } == true) throw OrdenesException("La reparación debe comenzar dentro de la asignación.")
                val overlaps = records.filter { it.assignmentId == assignment.id && it.id != record?.id }.any { r ->
                    (end == null || LocalDateTime.parse(r.start) < end) && (r.end == null || LocalDateTime.parse(r.end) > start)
                }
                if (overlaps) throw OrdenesException("El intervalo se solapa con otra reparación. Termine o corrija el registro existente.")
            }
            "repair_cost.create" -> if (!state.data.canCost || record?.canCost != true) throw OrdenesException("La OT o el proceso está cerrado, o no puede registrar este costo.")
            "equipment.extend" -> {
                if (!state.data.canRegister || !assignment.canRegister) throw OrdenesException("No se puede ampliar esta asignación cerrada.")
                val finish = runCatching { LocalDateTime.parse(body.text("ended_at")) }.getOrNull() ?: throw OrdenesException("Indique el nuevo fin de asignación.")
                if (finish <= LocalDateTime.parse(assignment.end ?: assignment.start)) throw OrdenesException("El nuevo fin debe ser posterior al plazo actual.")
            }
            "repair.delete" -> {
                if (!state.data.canEdit) throw OrdenesException("No puede anular esta reparación.")
                if (record?.costs?.isNotEmpty() == true) throw OrdenesException("La reparación tiene costos. Revise Costos del proceso antes de anularla.")
                val owner = sessions.current()?.userId ?: throw OrdenesException("La sesión expiró.")
                if (sync.localEntities(owner).any { it.type == "tracking_pending" && it.payload.text("operation_type") == "repair_cost.create" && it.payload.long("repair_id") == record?.id }) {
                    throw OrdenesException("Hay un costo pendiente de esta reparación. Sincronice o quite los cambios pendientes antes de anularla.")
                }
            }
        }
    }

    private fun message(body: String): String = runCatching {
        val root = ApiJson.instance.parseToJsonElement(body).jsonObject
        root["errors"]?.jsonObject?.values?.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: root["message"]?.jsonPrimitive?.content
    }.getOrNull() ?: "No se pudo consultar el seguimiento."
}

private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
