package com.jomiro.app.mina_mobil.data.ordenes

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class OrdenesRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
) {
    suspend fun opcionesFormulario(): OpcionesFormularioOrden {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/form-options"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudieron cargar clientes y unidades para la orden.")
        }
        return runCatching { ApiJson.instance.decodeFromString<OpcionesOrdenResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar los datos del formulario de orden.") }
    }

    suspend fun crear(
        clienteId: Long,
        fecha: String,
        pesoMineral: Double,
        unidadPesoId: Long,
        descripcion: String,
    ): OrdenTrabajo {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(CrearOrdenRequestDto(
                clientId = clienteId,
                date = fecha,
                description = descripcion.trim().ifBlank { null },
                mineralWeight = pesoMineral,
                weightUnitId = unidadPesoId,
            )))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo crear la orden (HTTP ${response.status.value}).")
        }
        return runCatching { ApiJson.instance.decodeFromString<OrdenCreadaResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("La orden se guardó, pero no se pudo leer la respuesta del servidor.") }
    }

    suspend fun opcionesEdicion(id: Long): FormularioEdicionOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$id/edit-form-options") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los datos para editar la orden.")
        return runCatching { ApiJson.instance.decodeFromString<EdicionOrdenResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar la información editable de la orden.") }
    }

    suspend fun actualizar(
        id: Long,
        clienteId: Long,
        fecha: String,
        pesoMineral: Double,
        unidadPesoId: Long,
        descripcion: String,
        tieneProcesos: Boolean,
    ): OrdenTrabajo {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val response = client.put("${baseUrl.trimEnd('/')}/orders/$id") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(ActualizarOrdenRequestDto(
                clientId = if (tieneProcesos) null else clienteId,
                date = fecha,
                description = descripcion.trim().ifBlank { null },
                mineralWeight = if (tieneProcesos) null else pesoMineral,
                weightUnitId = if (tieneProcesos) null else unidadPesoId,
            )))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo actualizar la orden.")
        return runCatching { ApiJson.instance.decodeFromString<OrdenCreadaResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("La orden se actualizó, pero no se pudo interpretar la respuesta del servidor.") }
    }

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

    suspend fun finalizar(id: Long) {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$id/finalize"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo finalizar la orden (HTTP ${response.status.value}).")
        }
    }

    suspend fun opcionesProceso(id: Long): List<EtapaOpcionProceso> {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$id/process-form-options"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudieron cargar las etapas disponibles.")
        }
        return runCatching { ApiJson.instance.decodeFromString<EtapasProcesoResponseDto>(body).data.stages.map { it.toModel() } }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las etapas disponibles.") }
    }

    suspend fun crearProceso(
        ordenId: Long,
        etapaId: Long,
        fechaInicio: String?,
        fechaFin: String?,
        pesoEntrada: Double?,
        pesoSalida: Double?,
        observaciones: String,
    ) {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(CrearProcesoRequestDto(
                etapaId = etapaId,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                pesoEntrada = pesoEntrada,
                pesoSalida = pesoSalida,
                observaciones = observaciones.trim().ifBlank { null },
            )))
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo agregar el proceso (HTTP ${response.status.value}).")
        }
    }

    suspend fun opcionesTrabajo(ordenId: Long, procesoId: Long): OpcionesTrabajoEmpleado {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/employee-work-options"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar empleados, tipos de pago y unidades.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesTrabajoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones del trabajo.") }
    }

    suspend fun tarifaTrabajo(
        ordenId: Long,
        procesoId: Long,
        empleadoId: Long,
        tipoPagoId: Long,
        fecha: String,
    ): TarifaTrabajoEmpleado {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/employee-work-rate"
        val response = client.get(endpoint) {
            url {
                parameters.append("empleado_id", empleadoId.toString())
                parameters.append("tipo_pago_id", tipoPagoId.toString())
                parameters.append("fecha", fecha)
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo consultar la tarifa vigente.")
        return runCatching { ApiJson.instance.decodeFromString<TarifaTrabajoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar la tarifa del trabajo.") }
    }

    suspend fun crearTrabajo(
        ordenId: Long,
        procesoId: Long,
        empleadoId: Long,
        tipoPagoId: Long,
        fecha: String,
        horaInicio: String?,
        horaFin: String?,
        cantidad: Double,
        unidadId: Long,
        descripcion: String,
        observaciones: String,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/employee-works"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(CrearTrabajoEmpleadoRequestDto(
                employeeId = empleadoId,
                paymentTypeId = tipoPagoId,
                date = fecha,
                startTime = horaInicio,
                endTime = horaFin,
                quantity = cantidad,
                unitId = unidadId,
                description = descripcion.trim().ifBlank { null },
                observations = observaciones.trim().ifBlank { null },
            )))
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo registrar el trabajo del empleado.")
    }

    suspend fun opcionesEquipo(ordenId: Long, procesoId: Long): OpcionesEquipoProceso {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/equipment-options") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los equipos disponibles.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesEquipoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar la lista de equipos.") }
    }

    suspend fun depreciacionEquipo(
        ordenId: Long,
        procesoId: Long,
        equipoId: Long,
        fechaInicio: String,
        fechaFin: String?,
    ): DepreciacionEquipoProceso {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/equipment-depreciation") {
            url {
                parameters.append("equipo_id", equipoId.toString())
                parameters.append("fecha_inicio", fechaInicio)
                fechaFin?.let { parameters.append("fecha_fin", it) }
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo calcular la depreciación del equipo.")
        return runCatching { ApiJson.instance.decodeFromString<DepreciacionEquipoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar el cálculo de depreciación.") }
    }

    suspend fun asignarEquipo(
        ordenId: Long,
        procesoId: Long,
        equipoId: Long,
        fechaInicio: String,
        fechaFin: String?,
        observaciones: String,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val response = client.post("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/equipment") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(AsignarEquipoRequestDto(
                equipoId = equipoId,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                observaciones = observaciones.trim().ifBlank { null },
            )))
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo asignar el equipo al proceso.")
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

    private fun apiException(body: String, fallback: String): OrdenesException {
        val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
            .getOrNull()?.takeIf(String::isNotBlank)
        return OrdenesException(message ?: fallback)
    }
}

@Serializable
private data class CrearOrdenRequestDto(
    @SerialName("cliente_id") val clientId: Long,
    @SerialName("fecha") val date: String,
    @SerialName("descripcion") val description: String?,
    @SerialName("peso_mineral") val mineralWeight: Double,
    @SerialName("unidad_peso_id") val weightUnitId: Long,
)

@Serializable
private data class ActualizarOrdenRequestDto(
    @SerialName("cliente_id") val clientId: Long?,
    @SerialName("fecha") val date: String,
    @SerialName("descripcion") val description: String?,
    @SerialName("peso_mineral") val mineralWeight: Double?,
    @SerialName("unidad_peso_id") val weightUnitId: Long?,
)

@Serializable
private data class OpcionesOrdenResponseDto(val data: OpcionesOrdenDto)

@Serializable
private data class EdicionOrdenResponseDto(val data: EdicionOrdenDto)

@Serializable
private data class EdicionOrdenDto(
    val order: OrdenEditableDto,
    @SerialName("has_processes") val hasProcesses: Boolean,
    @SerialName("earliest_process_start") val earliestProcessStart: String? = null,
    val clients: List<ClienteEditableDto>,
    val units: List<OpcionUnidadPesoDto>,
)

@Serializable
private data class OrdenEditableDto(
    val id: Long,
    val code: String,
    @SerialName("client_id") val clientId: Long,
    @SerialName("client_name") val clientName: String? = null,
    val date: String,
    val description: String? = null,
    @SerialName("mineral_weight") val mineralWeight: Double,
    @SerialName("weight_unit_id") val weightUnitId: Long? = null,
    @SerialName("weight_unit") val weightUnit: String? = null,
    val status: String,
    @SerialName("status_code") val statusCode: Int,
)

@Serializable
private data class ClienteEditableDto(val id: Long, val name: String, @SerialName("is_active") val isActive: Boolean)

@Serializable
private data class OpcionesOrdenDto(
    val clients: List<OpcionClienteOrdenDto>,
    val units: List<OpcionUnidadPesoDto>,
)

@Serializable
private data class OpcionClienteOrdenDto(val id: Long, val name: String)

@Serializable
private data class OpcionUnidadPesoDto(val id: Long, val name: String)

private fun EdicionOrdenDto.toModel() = FormularioEdicionOrden(
    orden = OrdenTrabajo(
        id = order.id,
        codigo = order.code,
        fecha = order.date,
        cliente = order.clientName,
        descripcion = order.description,
        pesoMineral = order.mineralWeight,
        unidadPeso = order.weightUnit,
        estado = order.status,
        codigoEstado = order.statusCode,
    ),
    clienteId = order.clientId,
    unidadPesoId = order.weightUnitId,
    tieneProcesos = hasProcesses,
    fechaInicioProcesoMasTemprana = earliestProcessStart,
    clientes = clients.map { ClienteOpcionOrden(it.id, it.name) },
    unidades = units.map { UnidadPesoOpcion(it.id, it.name) },
)

private fun OpcionesOrdenDto.toModel() = OpcionesFormularioOrden(
    clientes = clients.map { ClienteOpcionOrden(it.id, it.name) },
    unidades = units.map { UnidadPesoOpcion(it.id, it.name) },
)

@Serializable
private data class OrdenCreadaResponseDto(val data: OrdenTrabajoDto)

@Serializable
private data class EtapasProcesoResponseDto(val data: EtapasProcesoDto)

@Serializable
private data class EtapasProcesoDto(val stages: List<EtapaProcesoDto>)

@Serializable
private data class EtapaProcesoDto(val id: Long, val name: String, val order: Int)

private fun EtapaProcesoDto.toModel() = EtapaOpcionProceso(id, name, order)

@Serializable
private data class CrearProcesoRequestDto(
    @SerialName("etapa_id") val etapaId: Long,
    @SerialName("fecha_inicio") val fechaInicio: String?,
    @SerialName("fecha_fin") val fechaFin: String?,
    @SerialName("peso_entrada") val pesoEntrada: Double?,
    @SerialName("peso_salida") val pesoSalida: Double?,
    @SerialName("observaciones") val observaciones: String?,
)

data class OpcionTrabajoEmpleado(val id: Long, val codigo: String?, val nombre: String, val metodoCalculo: String? = null)
data class OpcionUnidadTrabajo(val id: Long, val nombre: String)
data class OpcionesTrabajoEmpleado(
    val empleados: List<OpcionTrabajoEmpleado>,
    val tiposPago: List<OpcionTrabajoEmpleado>,
    val unidades: List<OpcionUnidadTrabajo>,
)
data class TarifaTrabajoEmpleado(
    val tarifa: Double,
    val moneda: String,
    val tipoCambio: Double,
    val tarifaNio: Double,
    val metodoCalculo: String,
    val vigenciaInicio: String?,
    val vigenciaFin: String?,
)

@Serializable
private data class OpcionesTrabajoResponseDto(val data: OpcionesTrabajoDto)

@Serializable
private data class OpcionesTrabajoDto(
    val employees: List<OpcionTrabajoDto>,
    @SerialName("payment_types") val paymentTypes: List<OpcionTipoPagoDto>,
    val units: List<OpcionUnidadDto>,
)

@Serializable
private data class OpcionTrabajoDto(val id: Long, val code: String, val name: String)

@Serializable
private data class OpcionTipoPagoDto(val id: Long, val name: String, @SerialName("calculation_method") val calculationMethod: String)

@Serializable
private data class OpcionUnidadDto(val id: Long, val name: String)

private fun OpcionesTrabajoDto.toModel() = OpcionesTrabajoEmpleado(
    empleados = employees.map { OpcionTrabajoEmpleado(it.id, it.code, it.name) },
    tiposPago = paymentTypes.map { OpcionTrabajoEmpleado(it.id, null, it.name, it.calculationMethod) },
    unidades = units.map { OpcionUnidadTrabajo(it.id, it.name) },
)

@Serializable
private data class TarifaTrabajoResponseDto(val data: TarifaTrabajoDto)

@Serializable
private data class TarifaTrabajoDto(
    val rate: Double,
    val currency: String,
    @SerialName("exchange_rate") val exchangeRate: Double,
    @SerialName("rate_nio") val rateNio: Double,
    @SerialName("calculation_method") val calculationMethod: String,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
)

private fun TarifaTrabajoDto.toModel() = TarifaTrabajoEmpleado(rate, currency, exchangeRate, rateNio, calculationMethod, validFrom, validUntil)

@Serializable
private data class CrearTrabajoEmpleadoRequestDto(
    @SerialName("empleado_id") val employeeId: Long,
    @SerialName("tipo_pago_id") val paymentTypeId: Long,
    @SerialName("fecha") val date: String,
    @SerialName("hora_inicio") val startTime: String?,
    @SerialName("hora_fin") val endTime: String?,
    @SerialName("cantidad") val quantity: Double,
    @SerialName("unidad_id") val unitId: Long,
    @SerialName("descripcion") val description: String?,
    @SerialName("observaciones") val observations: String?,
)

@Serializable
private data class OpcionesEquipoResponseDto(val data: OpcionesEquipoDto)

@Serializable
private data class OpcionesEquipoDto(
    val equipment: List<EquipoOpcionDto>,
    @SerialName("default_start") val defaultStart: String,
)

@Serializable
private data class EquipoOpcionDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("acquisition_value") val acquisitionValue: Double,
    @SerialName("residual_value") val residualValue: Double,
    @SerialName("useful_life_months") val usefulLifeMonths: Int,
)

private fun OpcionesEquipoDto.toModel() = OpcionesEquipoProceso(
    equipos = equipment.map {
        EquipoOpcionProceso(it.id, it.code, it.name, it.acquisitionValue, it.residualValue, it.usefulLifeMonths)
    },
    inicioPredeterminado = defaultStart,
)

@Serializable
private data class DepreciacionEquipoResponseDto(val data: DepreciacionEquipoDto)

@Serializable
private data class DepreciacionEquipoDto(
    val code: String,
    @SerialName("acquisition_value") val acquisitionValue: Double,
    @SerialName("residual_value") val residualValue: Double,
    @SerialName("useful_life_months") val usefulLifeMonths: Int,
    val days: Double,
    @SerialName("daily_depreciation") val dailyDepreciation: Double,
    @SerialName("total_depreciation") val totalDepreciation: Double,
    @SerialName("available_value") val availableValue: Boolean,
    val overlap: String? = null,
)

private fun DepreciacionEquipoDto.toModel() = DepreciacionEquipoProceso(
    code, acquisitionValue, residualValue, usefulLifeMonths, days,
    dailyDepreciation, totalDepreciation, availableValue, overlap,
)

@Serializable
private data class AsignarEquipoRequestDto(
    @SerialName("equipo_id") val equipoId: Long,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String?,
    @SerialName("observaciones") val observaciones: String?,
)

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
    acuerdos = agreements.map { agreement ->
        AcuerdoOrdenTrabajo(
            id = agreement.id,
            tipoParticipacion = agreement.participationType,
            modalidad = agreement.modality,
            porcentajeCliente = agreement.clientPercentage?.contentOrNull,
            porcentajeEmpresa = agreement.companyPercentage?.contentOrNull,
            tarifaServicio = agreement.serviceFee?.contentOrNull,
            moneda = agreement.currency,
            observaciones = agreement.observations,
            activo = agreement.active,
        )
    },
    liquidaciones = liquidations.map { liquidation ->
        LiquidacionOrdenTrabajo(
            id = liquidation.id,
            fecha = liquidation.date,
            gramosRecuperados = liquidation.recoveredGrams?.contentOrNull,
            gramosCliente = liquidation.clientGrams?.contentOrNull,
            gramosEmpresa = liquidation.companyGrams?.contentOrNull,
            porcentajeCliente = liquidation.clientPercentage?.contentOrNull,
            porcentajeEmpresa = liquidation.companyPercentage?.contentOrNull,
            observaciones = liquidation.observations,
        )
    },
    trabajosEmpleados = employeeWork.map { work ->
        TrabajoEmpleadoOrden(
            id = work.id,
            codigoProceso = work.process,
            empleado = work.employee,
            tipoPago = work.paymentType,
            fecha = work.date,
            descripcion = work.description,
            cantidad = work.quantity?.contentOrNull,
            unidad = work.unit,
            moneda = work.currency,
            total = work.total?.contentOrNull,
            totalNio = work.totalNio?.contentOrNull,
        )
    },
    equipos = equipment.map { assignment ->
        EquipoProcesoOrden(
            id = assignment.id,
            codigoProceso = assignment.process,
            equipoId = assignment.equipmentId,
            codigoEquipo = assignment.equipmentCode,
            nombreEquipo = assignment.equipmentName,
            inicio = assignment.startDate,
            fin = assignment.endDate,
            dias = assignment.days,
            depreciacionDiaria = assignment.dailyDepreciation,
            depreciacionTotal = assignment.totalDepreciation,
            observaciones = assignment.observations,
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
    val agreements: List<AcuerdoOrdenDto> = emptyList(),
    val liquidations: List<LiquidacionOrdenDto> = emptyList(),
    @SerialName("employee_work") val employeeWork: List<TrabajoEmpleadoDto> = emptyList(),
    val equipment: List<EquipoProcesoDto> = emptyList(),
)

@Serializable
internal data class EquipoProcesoDto(
    val id: Long,
    val process: String? = null,
    @SerialName("equipment_id") val equipmentId: Long,
    @SerialName("equipment_code") val equipmentCode: String? = null,
    @SerialName("equipment_name") val equipmentName: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val days: Double = 0.0,
    @SerialName("daily_depreciation") val dailyDepreciation: Double = 0.0,
    @SerialName("total_depreciation") val totalDepreciation: Double = 0.0,
    val observations: String? = null,
)

@Serializable
internal data class TrabajoEmpleadoDto(
    val id: Long,
    val date: String? = null,
    val employee: String? = null,
    val process: String? = null,
    @SerialName("payment_type") val paymentType: String? = null,
    val description: String? = null,
    val quantity: JsonPrimitive? = null,
    val unit: String? = null,
    val currency: String? = null,
    val total: JsonPrimitive? = null,
    @SerialName("total_nio") val totalNio: JsonPrimitive? = null,
)

@Serializable
internal data class AcuerdoOrdenDto(
    val id: Long,
    @SerialName("participation_type") val participationType: String? = null,
    val modality: String,
    @SerialName("client_percentage") val clientPercentage: JsonPrimitive? = null,
    @SerialName("company_percentage") val companyPercentage: JsonPrimitive? = null,
    @SerialName("service_fee") val serviceFee: JsonPrimitive? = null,
    val currency: String? = null,
    val observations: String? = null,
    val active: Boolean = false,
)

@Serializable
internal data class LiquidacionOrdenDto(
    val id: Long,
    val date: String? = null,
    @SerialName("recovered_grams") val recoveredGrams: JsonPrimitive? = null,
    @SerialName("client_grams") val clientGrams: JsonPrimitive? = null,
    @SerialName("company_grams") val companyGrams: JsonPrimitive? = null,
    @SerialName("client_percentage") val clientPercentage: JsonPrimitive? = null,
    @SerialName("company_percentage") val companyPercentage: JsonPrimitive? = null,
    val observations: String? = null,
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
