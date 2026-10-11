package com.jomiro.app.mina_mobil.data.ordenes

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.SessionRepository
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class OrdenesRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
) {
    suspend fun listar(
        pagina: Int,
        codigo: String? = null,
        clienteId: Long? = null,
        fechaDesde: String? = null,
        fechaHasta: String? = null,
    ): PaginaOrdenes = consultar(
        pagina = pagina,
        perPage = LIST_PAGE_SIZE,
        codigo = codigo,
        clienteId = clienteId,
        fechaDesde = fechaDesde,
        fechaHasta = fechaHasta,
    )

    suspend fun calendario(fechaDesde: String, fechaHasta: String): List<OrdenTrabajo> {
        val firstPage = consultar(
            pagina = 1,
            perPage = CALENDAR_PAGE_SIZE,
            fechaDesde = fechaDesde,
            fechaHasta = fechaHasta,
        )
        val result = firstPage.ordenes.toMutableList()
        for (pagina in 2..firstPage.ultimaPagina) {
            result += consultar(
                pagina = pagina,
                perPage = CALENDAR_PAGE_SIZE,
                fechaDesde = fechaDesde,
                fechaHasta = fechaHasta,
            ).ordenes
        }
        return result
    }

    suspend fun detalle(id: Long): DetalleOrdenTrabajo {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$id"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo cargar el detalle de la orden (HTTP ${response.status.value}).")
        }
        return parseOrderDetail(body)
    }

    private suspend fun consultar(
        pagina: Int,
        perPage: Int,
        codigo: String? = null,
        clienteId: Long? = null,
        fechaDesde: String? = null,
        fechaHasta: String? = null,
    ): PaginaOrdenes {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders"
        val response = client.get(endpoint) {
            url {
                parameters.append("page", pagina.toString())
                parameters.append("per_page", perPage.toString())
                codigo?.takeIf(String::isNotBlank)?.let { parameters.append("q", it) }
                clienteId?.let { parameters.append("cliente_id", it.toString()) }
                fechaDesde?.let { parameters.append("fecha_desde", it) }
                fechaHasta?.let { parameters.append("fecha_hasta", it) }
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudieron cargar las órdenes de trabajo (HTTP ${response.status.value}).")
        }

        return parseOrdersPage(body)
    }

    private companion object {
        const val LIST_PAGE_SIZE = 30
        const val CALENDAR_PAGE_SIZE = 100
    }
}

class OrdenesException(message: String) : Exception(message)

internal fun parseOrdersPage(body: String): PaginaOrdenes {
    val normalizedBody = body.removePrefix("\uFEFF").trimStart()
    val payload = runCatching { ApiJson.instance.decodeFromString<OrdenesResponseDto>(normalizedBody) }
        .getOrElse { error ->
            val reason = if (normalizedBody.startsWith("<")) {
                "el servidor devolvió HTML en lugar de JSON"
            } else {
                "el formato de la respuesta no es válido (${error::class.simpleName})"
            }
            throw OrdenesException("No se pudieron interpretar las órdenes: $reason.")
        }

    return PaginaOrdenes(
        ordenes = payload.data.map(OrdenTrabajoDto::toModel),
        paginaActual = payload.meta.currentPage,
        ultimaPagina = payload.meta.lastPage,
        total = payload.meta.total,
    )
}

internal fun parseOrderDetail(body: String): DetalleOrdenTrabajo {
    val normalizedBody = body.removePrefix("\uFEFF").trimStart()
    val payload = runCatching { ApiJson.instance.decodeFromString<OrdenDetalleResponseDto>(normalizedBody) }
        .getOrElse { error ->
            val reason = if (normalizedBody.startsWith("<")) {
                "el servidor devolvió HTML en lugar de JSON"
            } else {
                "el formato de la respuesta no es válido (${error::class.simpleName})"
            }
            throw OrdenesException("No se pudo interpretar el detalle de la orden: $reason.")
        }
    return payload.data.toModel()
}

private fun OrdenTrabajoDetalleDto.toModel() = DetalleOrdenTrabajo(
    orden = OrdenTrabajoDto(
        id = id,
        code = code,
        date = date,
        client = client,
        description = description,
        mineralWeight = mineralWeight,
        weightUnit = weightUnit,
        status = status,
        statusCode = statusCode,
    ).toModel(),
    procesos = processes.map { process ->
        ProcesoOrdenTrabajo(
            id = process.id,
            codigo = process.code,
            etapa = process.stage,
            estado = process.status,
            fechaInicio = process.startDate,
            fechaFin = process.endDate,
            pesoEntrada = process.inputWeight?.contentOrNull?.toDoubleOrNull(),
            pesoSalida = process.outputWeight?.contentOrNull?.toDoubleOrNull(),
            observaciones = process.observations,
        )
    },
    costos = costs.map { cost ->
        CostoOrdenTrabajo(
            id = cost.id,
            fecha = cost.date,
            proceso = cost.process,
            categoria = cost.category,
            descripcion = cost.description,
            cantidad = cost.quantity?.contentOrNull,
            moneda = cost.currency,
            total = cost.total?.contentOrNull,
            totalNio = cost.totalNio?.contentOrNull,
        )
    },
    ingresos = incomes.map { income ->
        IngresoOrdenTrabajo(
            id = income.id,
            fecha = income.date,
            tipo = income.type,
            descripcion = income.description,
            moneda = income.currency,
            total = income.total?.contentOrNull,
            totalNio = income.totalNio?.contentOrNull,
        )
    },
)

@Serializable
internal data class OrdenesResponseDto(
    val data: List<OrdenTrabajoDto>,
    val meta: OrdenesMetaDto,
)

@Serializable
internal data class OrdenDetalleResponseDto(val data: OrdenTrabajoDetalleDto)

@Serializable
internal data class OrdenTrabajoDetalleDto(
    val id: Long,
    val code: String,
    val date: String? = null,
    val client: OrdenClienteDto? = null,
    val description: String? = null,
    @SerialName("mineral_weight") val mineralWeight: JsonPrimitive? = null,
    @SerialName("weight_unit") val weightUnit: String? = null,
    val status: String,
    @SerialName("status_code") val statusCode: Int,
    val processes: List<ProcesoOrdenDto> = emptyList(),
    val costs: List<CostoOrdenDto> = emptyList(),
    val incomes: List<IngresoOrdenDto> = emptyList(),
)

@Serializable
internal data class CostoOrdenDto(
    val id: Long,
    val date: String? = null,
    val process: String? = null,
    val category: String? = null,
    val description: String? = null,
    val quantity: JsonPrimitive? = null,
    val currency: String? = null,
    val total: JsonPrimitive? = null,
    @SerialName("total_nio") val totalNio: JsonPrimitive? = null,
)

@Serializable
internal data class IngresoOrdenDto(
    val id: Long,
    val date: String? = null,
    val type: String? = null,
    val description: String? = null,
    val currency: String? = null,
    val total: JsonPrimitive? = null,
    @SerialName("total_nio") val totalNio: JsonPrimitive? = null,
)

@Serializable
internal data class ProcesoOrdenDto(
    val id: Long,
    val code: String,
    val stage: String? = null,
    val status: String,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("input_weight") val inputWeight: JsonPrimitive? = null,
    @SerialName("output_weight") val outputWeight: JsonPrimitive? = null,
    val observations: String? = null,
)

@Serializable
internal data class OrdenTrabajoDto(
    val id: Long,
    val code: String,
    val date: String? = null,
    val client: OrdenClienteDto? = null,
    val description: String? = null,
    @SerialName("mineral_weight") val mineralWeight: JsonPrimitive? = null,
    @SerialName("weight_unit") val weightUnit: String? = null,
    val status: String,
    @SerialName("status_code") val statusCode: Int,
) {
    fun toModel() = OrdenTrabajo(
        id = id,
        codigo = code,
        fecha = date,
        cliente = client?.name,
        descripcion = description,
        pesoMineral = mineralWeight?.contentOrNull?.toDoubleOrNull(),
        unidadPeso = weightUnit,
        estado = status,
        codigoEstado = statusCode,
    )
}

@Serializable
internal data class OrdenClienteDto(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
internal data class OrdenesMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
)
