package com.jomiro.app.mina_mobil.data.ordenes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeguimientoOrden(
    @SerialName("order_id") val orderId: Long,
    @SerialName("order_code") val orderCode: String,
    @SerialName("generated_at") val generatedAt: String,
    val timezone: String = "UTC",
    @SerialName("total_processes") val total: Int = 0,
    @SerialName("completed_processes") val completed: Int = 0,
    @SerialName("cancelled_processes") val cancelled: Int = 0,
    val percent: Double? = null,
    @SerialName("can_register") val canRegister: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_cost") val canCost: Boolean = false,
    val timeline: LineaTiempo? = null,
    val assignments: List<AsignacionSeguimiento> = emptyList(),
    val repairs: List<ReparacionEquipo> = emptyList(),
    val categories: List<OpcionSeguimiento> = emptyList(),
    val currencies: List<OpcionSeguimiento> = emptyList(),
)

@Serializable
data class LineaTiempo(
    val start: String, val end: String,
    @SerialName("hours_in_repair") val hours: Double = 0.0,
    @SerialName("elapsed_percent") val elapsed: Double = 0.0,
    val segments: List<TramoReparacion> = emptyList(),
)

@Serializable data class TramoReparacion(val from: Double, val to: Double)
@Serializable data class OpcionSeguimiento(val id: Long, val name: String)

@Serializable
data class AsignacionSeguimiento(
    val id: Long, @SerialName("process_id") val processId: Long,
    val process: String, val equipment: String, val code: String,
    val start: String, val end: String? = null,
    @SerialName("can_register") val canRegister: Boolean = true,
    @SerialName("hours_in_repair") val hours: Double = 0.0,
    @SerialName("elapsed_percent") val elapsed: Double = 0.0,
    val segments: List<TramoReparacion> = emptyList(),
)

@Serializable
data class ReparacionEquipo(
    val id: Long, @SerialName("assignment_id") val assignmentId: Long,
    val version: Int = 1,
    @SerialName("started_at") val start: String,
    @SerialName("ended_at") val end: String? = null,
    val reason: String, val equipment: String = "", val process: String = "",
    @SerialName("can_cost") val canCost: Boolean = true,
    @SerialName("hours_in_assignment") val hours: Double = 0.0,
    val costs: List<CostoReparacion> = emptyList(),
    @SerialName("repair_location") val repairLocation: String? = null,
    @SerialName("work_description") val workDescription: String? = null,
    @SerialName("total_hours") val totalHours: Double? = null,
)

@Serializable
data class CostoReparacion(val id: Long, val description: String, val amount: Double, val currency: String)

data class EstadoSeguimiento(
    val data: SeguimientoOrden,
    val offline: Boolean,
    val downloadedAt: String? = null,
    val pending: List<CambioSeguimiento> = emptyList(),
    val formAssignments: List<AsignacionSeguimiento> = data.assignments,
) {
    val hasUncertainChanges: Boolean get() = pending.any { it.status == "unknown" }
}

data class CambioSeguimiento(val id: Long, val type: String, val description: String, val status: String)
