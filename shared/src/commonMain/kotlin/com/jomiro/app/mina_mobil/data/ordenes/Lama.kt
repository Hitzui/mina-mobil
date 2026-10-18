package com.jomiro.app.mina_mobil.data.ordenes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LamaResponse(val data: LamaGestion)

@Serializable
data class LamaGestion(
    @SerialName("order_id") val orderId: Long,
    @SerialName("order_code") val orderCode: String,
    @SerialName("is_treatment") val isTreatment: Boolean = false,
    @SerialName("can_register") val canRegister: Boolean = false,
    @SerialName("can_start") val canStart: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_agree") val canAgree: Boolean = false,
    @SerialName("can_recover") val canRecover: Boolean = false,
    @SerialName("can_value") val canValue: Boolean = false,
    @SerialName("can_see_gold") val canSeeGold: Boolean = false,
    @SerialName("can_see_finances") val canSeeFinances: Boolean = false,
    val agreements: List<LamaOpcionAcuerdo> = emptyList(),
    val units: List<LamaOpcion> = emptyList(),
    val currencies: List<LamaOpcion> = emptyList(),
    val lots: List<LamaLote> = emptyList(),
    @SerialName("financial_summary") val financialSummary: LamaResumen? = null,
    val offline: Boolean = false,
)

@Serializable data class LamaOpcion(val id: Long, val name: String)
@Serializable data class LamaOpcionAcuerdo(val id: Long, val label: String)

@Serializable
data class LamaLote(
    val id: Long,
    val code: String,
    @SerialName("source_id") val sourceId: Long,
    @SerialName("source_code") val sourceCode: String,
    val date: String,
    @SerialName("estimated_quantity") val quantity: Double,
    val unit: String,
    val location: String,
    val holder: String,
    @SerialName("company_percentage") val companyPercentage: Double,
    @SerialName("client_percentage") val clientPercentage: Double,
    val observations: String? = null,
    @SerialName("treatment_id") val treatmentId: Long? = null,
    @SerialName("treatment_code") val treatmentCode: String? = null,
    val status: String,
    @SerialName("available_quantity") val availableQuantity: Double = 0.0,
    @SerialName("recovered_grams") val recoveredGrams: Double? = null,
    @SerialName("company_grams") val companyGrams: Double? = null,
    val recoveries: List<LamaRecuperacion> = emptyList(),
    val result: LamaResultado? = null,
)

@Serializable
data class LamaRecuperacion(
    val id: Long, val date: String, val grams: Double, val purity: Double? = null, val valued: Boolean = false,
    @SerialName("needs_valuation") val needsValuation: Boolean = !valued,
    @SerialName("can_value") val canValue: Boolean = true,
)

@Serializable
data class LamaResultado(
    @SerialName("company_gold_value_nio") val companyGoldValue: Double,
    @SerialName("treatment_cost_nio") val treatmentCost: Double,
    @SerialName("pending_valuations") val pendingValuations: Int = 0,
    val complete: Boolean = false,
    @SerialName("reference_result_nio") val referenceResult: Double? = null,
)

@Serializable
data class LamaResumen(
    @SerialName("service_income_nio") val serviceIncome: Double,
    @SerialName("service_cost_nio") val serviceCost: Double,
    @SerialName("company_gold_value_nio") val companyGoldValue: Double,
    @SerialName("treatment_cost_nio") val treatmentCost: Double,
    @SerialName("pending_lots") val pendingLots: Int = 0,
    val complete: Boolean = false,
    @SerialName("reference_result_nio") val referenceResult: Double? = null,
)
