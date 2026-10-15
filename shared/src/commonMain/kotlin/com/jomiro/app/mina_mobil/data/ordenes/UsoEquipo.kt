package com.jomiro.app.mina_mobil.data.ordenes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsoEquipoDetalle(
    val id: Long,
    val name: String,
    @SerialName("can_edit") val puedeEditar: Boolean = false,
    @SerialName("can_manage_supplies") val puedeGestionarInsumos: Boolean = false,
    @SerialName("effective_hours") val horas: Double? = null,
    @SerialName("meter_start") val horometroInicial: Double? = null,
    @SerialName("meter_end") val horometroFinal: Double? = null,
    @SerialName("consumption_confirmed") val consumosConfirmados: Boolean = false,
    @SerialName("cost_status") val estado: String,
    @SerialName("total_cost_nio") val total: Double,
    @SerialName("depreciation_nio") val depreciacion: Double,
    @SerialName("energy_cost_nio") val electricidad: Double,
    @SerialName("supplies_cost_nio") val insumos: Double,
    val resources: List<RecursoUsoEquipo> = emptyList(),
    val lots: List<LoteUsoEquipo> = emptyList(),
    val consumptions: List<ConsumoUsoEquipo> = emptyList(),
    val deliveries: List<EntregaUsoEquipo> = emptyList(),
)

@Serializable
data class EntregaUsoEquipo(val id: Long, val name: String, val quantity: Double, val available: Double, val date: String)

@Serializable
data class RecursoEstimadoEquipo(
    @SerialName("producto_id") val productoId: Long,
    val nombre: String,
    val unidad: String,
    @SerialName("cantidad_por_hora") val cantidadPorHora: Double,
    @SerialName("costo_unitario_nio") val costoUnitarioNio: Double,
)

@Serializable
data class RecursoUsoEquipo(@SerialName("product_id") val productoId: Long, val name: String)

@Serializable
data class LoteUsoEquipo(
    val id: Long, val name: String, val available: Double,
    @SerialName("unit_cost_nio") val costoUnitario: Double,
)

@Serializable
data class ConsumoUsoEquipo(
    val id: Long, val type: String, val name: String, val quantity: Double,
    @SerialName("cost_nio") val costo: Double, val date: String,
)

@Serializable
internal data class UsoEquipoResponse(val data: UsoEquipoDetalle)
