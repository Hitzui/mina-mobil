package com.jomiro.app.mina_mobil.data.ordenes

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.datetime.daysUntil
import kotlin.math.roundToInt

class OrdenesRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val offlineSync: OfflineSyncRepository,
) {
    suspend fun opcionesFormulario(): OpcionesFormularioOrden {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.snapshot(session.userId)?.let { snapshot ->
            val clients = ApiJson.instance.decodeFromString<List<OpcionClienteOrdenDto>>(snapshot.getValue("clients").toString())
            val units = ApiJson.instance.decodeFromString<List<OpcionUnidadPesoDto>>(snapshot.getValue("catalogs").jsonObject.getValue("units").toString())
            return OpcionesOrdenDto(clients, units).toModel()
        }
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
        if (offlineSync.downloaded(session.userId)) {
            val operation = buildJsonObject {
                put("cliente_id", clienteId)
                put("fecha", fecha)
                put("peso_mineral", pesoMineral)
                put("unidad_peso_id", unidadPesoId)
                put("descripcion", descripcion.trim())
            }
            var order = OrdenTrabajo(0, "", fecha, null, descripcion, pesoMineral, null, "Pendiente", 1)
            val localId = offlineSync.enqueueOperation(
                session.userId, "order.create", operation, "order",
                localEntityPayload = { id ->
                    val selectedClient = ApiJson.instance.decodeFromString<List<OpcionClienteOrdenDto>>(
                        offlineSync.snapshot(session.userId)?.getValue("clients").toString(),
                    ).firstOrNull { it.id == clienteId }
                    order = order.copy(id = id, codigo = "LOCAL-${-id}", cliente = selectedClient?.name)
                    order.toOfflineJson(clienteId, unidadPesoId)
                },
            )
            return order.copy(id = localId, codigo = "LOCAL-${-localId}")
        }
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
        if (offlineSync.downloaded(session.userId)) {
            val order = offlineOrder(session.userId, id) ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            val options = opcionesFormulario()
            val detail = detalleLocal(session.userId, id)
            return FormularioEdicionOrden(order, offlineClientId(session.userId, id) ?: 0L,
                offlineUnitId(session.userId, id), detail.procesos.isNotEmpty(), options.clientes, options.unidades)
        }
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
        if (offlineSync.downloaded(session.userId)) {
            val previous = offlineOrder(session.userId, id) ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            val clientName = opcionesFormulario().clientes.firstOrNull { it.id == clienteId }?.nombre
            val updated = previous.copy(cliente = clientName, fecha = if (tieneProcesos) previous.fecha else fecha,
                pesoMineral = if (tieneProcesos) previous.pesoMineral else pesoMineral, descripcion = descripcion)
            val body = buildJsonObject {
                put("order_id", id); put("cliente_id", clienteId); put("fecha", fecha)
                put("descripcion", descripcion); put("peso_mineral", pesoMineral); put("unidad_peso_id", unidadPesoId)
            }
            offlineSync.enqueueOperation(session.userId, "order.update", body, "order", id) {
                updated.toOfflineJson(clienteId, if (tieneProcesos) offlineUnitId(session.userId, id) else unidadPesoId)
            }
            return updated
        }
        val response = client.put("${baseUrl.trimEnd('/')}/orders/$id") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(ActualizarOrdenRequestDto(
                clientId = clienteId,
                date = if (tieneProcesos) null else fecha,
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
    ): PaginaOrdenes {
        val owner = sessions.current()?.userId
        if (owner != null && offlineSync.downloaded(owner)) {
            val orders = offlineOrders(owner).filter { order ->
                (codigo.isNullOrBlank() || order.codigo.contains(codigo, ignoreCase = true)) &&
                    (clienteId == null || offlineClientId(owner, order.id) == clienteId) &&
                    (fechaDesde == null || (order.fecha ?: "") >= fechaDesde) &&
                    (fechaHasta == null || (order.fecha ?: "") <= fechaHasta)
            }
            val start = (pagina - 1) * LIST_PAGE_SIZE
            return PaginaOrdenes(orders.drop(start).take(LIST_PAGE_SIZE), pagina,
                ((orders.size + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE).coerceAtLeast(1), orders.size)
        }
        return consultar(pagina, LIST_PAGE_SIZE, codigo, clienteId, fechaDesde, fechaHasta)
    }

    suspend fun calendario(fechaDesde: String, fechaHasta: String): List<OrdenTrabajo> {
        val owner = sessions.current()?.userId
        if (owner != null && offlineSync.downloaded(owner)) {
            return offlineOrders(owner).filter { (it.fecha ?: "") in fechaDesde..fechaHasta }
        }
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
        if (offlineSync.downloaded(session.userId)) return detalleLocal(session.userId, id)
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
        if (offlineSync.downloaded(session.userId)) {
            val order = offlineOrder(session.userId, id) ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            offlineSync.enqueueOperation(
                session.userId, "order.finalize", buildJsonObject { put("order_id", id) }, "order", id,
            ) { order.copy(estado = "Finalizada", codigoEstado = 3).toOfflineJson(offlineClientId(session.userId, id)) }
            return
        }
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
        offlineSync.snapshot(session.userId)?.let { snapshot ->
            return ApiJson.instance.decodeFromString<List<EtapaProcesoDto>>(
                snapshot.getValue("catalogs").jsonObject.getValue("stages").toString(),
            ).map { it.toModel() }
        }
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
        if (offlineSync.downloaded(session.userId)) {
            val processOptions = opcionesProceso(ordenId)
            val stage = processOptions.firstOrNull { it.id == etapaId }?.nombre ?: "Etapa"
            val payload = buildJsonObject {
                put("order_id", ordenId); put("etapa_id", etapaId)
                fechaInicio?.let { put("fecha_inicio", it) }; fechaFin?.let { put("fecha_fin", it) }
                pesoEntrada?.let { put("peso_entrada", it) }; pesoSalida?.let { put("peso_salida", it) }
                put("observaciones", observaciones)
            }
            offlineSync.enqueueOperation(session.userId, "process.create", payload, "process") { localId ->
                buildJsonObject {
                    put("id", localId); put("order_id", ordenId); put("code", "LOCAL-${-localId}")
                    put("stage", stage); put("status", "Pendiente")
                    fechaInicio?.let { put("start_date", it) }; fechaFin?.let { put("end_date", it) }
                    pesoEntrada?.let { put("input_weight", it) }; pesoSalida?.let { put("output_weight", it) }
                    put("observations", observaciones)
                }
            }
            return
        }
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
        offlineSync.snapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot.getValue("catalogs").jsonObject
            return OpcionesTrabajoDto(
                ApiJson.instance.decodeFromString(catalogs.getValue("employees").toString()),
                ApiJson.instance.decodeFromString(catalogs.getValue("payment_types").toString()),
                ApiJson.instance.decodeFromString(catalogs.getValue("units").toString()),
            ).toModel()
        }
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
        offlineSync.snapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot.getValue("catalogs").jsonObject
            val rates = ApiJson.instance.decodeFromString<List<OfflineEmployeeRateDto>>(catalogs.getValue("employee_rates").toString())
            val rate = rates.filter { it.employeeId == empleadoId && it.paymentTypeId == tipoPagoId && it.active }
                .filter { (it.validFrom == null || fecha >= it.validFrom) && (it.validUntil == null || fecha <= it.validUntil) }
                .maxByOrNull { it.validFrom.orEmpty() }
                ?: throw OrdenesException("No hay una tarifa descargada vigente para este empleado, tipo de pago y fecha.")
            val exchange = if (rate.isBaseCurrency) 1.0 else {
                val exchangeRates = ApiJson.instance.decodeFromString<List<OfflineExchangeRateDto>>(catalogs.getValue("exchange_rates").toString())
                exchangeRates.firstOrNull { it.currencyId == rate.currencyId && it.fecha == fecha }?.rate
                    ?: throw OrdenesException("No se descargó el tipo de cambio necesario para la fecha seleccionada.")
            }
            return TarifaTrabajoEmpleado(rate.rate, rate.currency ?: "—", exchange, rate.rate * exchange,
                rate.calculationMethod ?: "cantidad_x_tarifa", rate.validFrom, rate.validUntil)
        }
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
        if (offlineSync.downloaded(session.userId)) {
            val options = opcionesTrabajo(ordenId, procesoId)
            val employee = options.empleados.firstOrNull { it.id == empleadoId }?.nombre ?: "Empleado"
            val payment = options.tiposPago.firstOrNull { it.id == tipoPagoId }?.nombre ?: "Tipo de pago"
            val unit = options.unidades.firstOrNull { it.id == unidadId }?.nombre ?: ""
            val processCode = detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }?.codigo
                ?: "LOCAL-${-procesoId}"
            val rate = tarifaTrabajo(ordenId, procesoId, empleadoId, tipoPagoId, fecha)
            val total = (if (rate.metodoCalculo == "TARIFA") rate.tarifa else cantidad * rate.tarifa).roundMoney()
            val totalNio = (if (rate.metodoCalculo == "TARIFA") rate.tarifaNio else cantidad * rate.tarifaNio).roundMoney()
            val payload = buildJsonObject {
                put("order_id", ordenId); put("process_id", procesoId); put("empleado_id", empleadoId)
                put("tipo_pago_id", tipoPagoId); put("fecha", fecha)
                horaInicio?.let { put("hora_inicio", it) }; horaFin?.let { put("hora_fin", it) }
                put("cantidad", cantidad); put("unidad_id", unidadId); put("descripcion", descripcion); put("observaciones", observaciones)
            }
            offlineSync.enqueueOperation(session.userId, "employee_work.create", payload, "employee_work") { localId ->
                buildJsonObject {
                    put("id", localId); put("order_id", ordenId); put("process_id", procesoId)
                    put("employee", employee); put("payment_type", payment); put("date", fecha)
                    put("process", processCode); put("description", descripcion)
                    put("quantity", cantidad); put("unit", unit); put("currency", rate.moneda)
                    put("total", total); put("total_nio", totalNio)
                }
            }
            return
        }
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
        offlineSync.snapshot(session.userId)?.let { snapshot ->
            val equipment = ApiJson.instance.decodeFromString<List<EquipoOpcionDto>>(
                snapshot.getValue("catalogs").jsonObject.getValue("equipment").toString(),
            )
            val process = detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }
            return OpcionesEquipoDto(equipment, process?.fechaInicio?.take(16).orEmpty()).toModel()
        }
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
        offlineSync.snapshot(session.userId)?.let { snapshot ->
            val equipment = ApiJson.instance.decodeFromString<List<EquipoOpcionDto>>(
                snapshot.getValue("catalogs").jsonObject.getValue("equipment").toString(),
            ).firstOrNull { it.id == equipoId } ?: throw OrdenesException("El equipo no está en la descarga local.")
            val start = kotlinx.datetime.LocalDate.parse(fechaInicio.take(10))
            val end = kotlinx.datetime.LocalDate.parse((fechaFin ?: fechaInicio).take(10))
            val days = start.daysUntil(end).coerceAtLeast(0).toDouble()
            val daily = if (equipment.usefulLifeMonths > 0) (((equipment.acquisitionValue - equipment.residualValue).coerceAtLeast(0.0) /
                (equipment.usefulLifeMonths * 30.0)) * 10_000).roundToInt() / 10_000.0 else 0.0
            val overlap = offlineEquipmentAssignments(session.userId, equipoId).firstOrNull { (priorStart, priorEnd) ->
                val startDate = fechaInicio.take(10)
                val endDate = fechaFin?.take(10)
                (priorEnd == null || startDate <= priorEnd) && (endDate == null || priorStart <= endDate)
            }?.let { "El equipo ya tiene una asignación que se cruza con este periodo." }
            val remaining = equipment.remainingValue.takeIf { it > 0.0 } ?: (equipment.acquisitionValue - equipment.residualValue).coerceAtLeast(0.0)
            return DepreciacionEquipoProceso(equipment.code, equipment.acquisitionValue, equipment.residualValue,
                equipment.usefulLifeMonths, days, daily, (daily * days).roundMoney().coerceAtMost(remaining.roundMoney()), remaining > 0.0, overlap)
        }
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
        if (offlineSync.downloaded(session.userId)) {
            val equipment = opcionesEquipo(ordenId, procesoId).equipos.firstOrNull { it.id == equipoId }
                ?: throw OrdenesException("El equipo no está en la descarga local.")
            val estimate = depreciacionEquipo(ordenId, procesoId, equipoId, fechaInicio, fechaFin)
            if (!estimate.disponible || estimate.solapamiento != null) {
                throw OrdenesException(estimate.solapamiento ?: "El equipo no tiene valor disponible para depreciar.")
            }
            val payload = buildJsonObject {
                put("order_id", ordenId); put("process_id", procesoId); put("equipo_id", equipoId)
                put("fecha_inicio", fechaInicio); fechaFin?.let { put("fecha_fin", it) }; put("observaciones", observaciones)
            }
            val processCode = detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }?.codigo
                ?: "LOCAL-${-procesoId}"
            offlineSync.enqueueOperation(session.userId, "equipment.assign", payload, "equipment") { localId ->
                buildJsonObject {
                    put("id", localId); put("order_id", ordenId); put("process_id", procesoId); put("process", processCode)
                    put("equipment_id", equipoId); put("equipment_code", equipment.codigo); put("equipment_name", equipment.nombre)
                    put("start_date", fechaInicio); fechaFin?.let { put("end_date", it) }; put("observations", observaciones)
                    put("days", estimate.dias); put("daily_depreciation", estimate.depreciacionDiaria)
                    put("total_depreciation", estimate.depreciacionTotal)
                }
            }
            return
        }
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

    private fun offlineOrder(ownerId: Long, id: Long): OrdenTrabajo? = offlineOrders(ownerId).firstOrNull { it.id == id }

    private fun offlineClientId(ownerId: Long, id: Long): Long? {
        val cached = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().firstOrNull { it.id == id }?.client?.id
        if (cached != null) return cached
        return offlineSync.localEntities(ownerId).firstOrNull { it.type == "order" && it.id == id }
            ?.payload?.get("client")?.jsonObject?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
    }

    private fun offlineUnitId(ownerId: Long, id: Long): Long? {
        val snapshotUnit = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().firstOrNull { it.id == id }?.weightUnitId
        if (snapshotUnit != null) return snapshotUnit
        return offlineSync.localEntities(ownerId).firstOrNull { it.type == "order" && it.id == id }
            ?.payload?.get("weight_unit_id")?.jsonPrimitive?.content?.toLongOrNull()
    }

    private fun offlineEquipmentAssignments(ownerId: Long, equipmentId: Long): List<Pair<String, String?>> {
        val fromCatalog = offlineSync.snapshot(ownerId)?.get("catalogs")?.jsonObject?.get("equipment_assignments")
            ?.let { raw ->
                runCatching { raw.jsonArray }.getOrNull().orEmpty().mapNotNull { entry ->
                    val assignment = entry.jsonObject
                    if (assignment["equipment_id"]?.jsonPrimitive?.content?.toLongOrNull() != equipmentId) null
                    else assignment["start_date"]?.jsonPrimitive?.content?.take(10)?.let {
                        it to assignment["end_date"]?.jsonPrimitive?.content?.take(10)
                    }
                }
            }.orEmpty()
        val fromOrderDetails = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().flatMap { it.equipment }.filter { it.equipmentId == equipmentId }
            .mapNotNull { item -> item.startDate?.take(10)?.let { it to item.endDate?.take(10) } }
        val local = offlineSync.localEntities(ownerId).filter { it.type == "equipment" }
            .mapNotNull { entity ->
                val payload = entity.payload
                if (payload["equipment_id"]?.jsonPrimitive?.content?.toLongOrNull() != equipmentId) null
                else payload["start_date"]?.jsonPrimitive?.content?.take(10)?.let {
                    it to payload["end_date"]?.jsonPrimitive?.content?.take(10)
                }
            }
        return fromCatalog + fromOrderDetails + local
    }

    private fun offlineOrders(ownerId: Long): List<OrdenTrabajo> {
        val snapshotOrders = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().map { dto -> OrdenTrabajoDto(
                dto.id, dto.code, dto.date, dto.client, dto.description, dto.mineralWeight, dto.weightUnit, dto.status, dto.statusCode,
            ).toModel() }
        val localOrders = offlineSync.localEntities(ownerId).filter { it.type == "order" }.mapNotNull { entity ->
            runCatching { ApiJson.instance.decodeFromString<OrdenTrabajoDto>(entity.payload.toString()).toModel() }.getOrNull()
        }
        return (snapshotOrders + localOrders).associateBy { it.id }.values.sortedWith(
            compareByDescending<OrdenTrabajo> { it.fecha }.thenByDescending { it.id },
        )
    }

    private fun detalleLocal(ownerId: Long, id: Long): DetalleOrdenTrabajo {
        val snapshotOrder = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().firstOrNull { it.id == id }
        val localOrders = offlineSync.localEntities(ownerId).filter { it.type == "order" && it.id == id }
        val localOrder = localOrders.lastOrNull()?.let { runCatching { ApiJson.instance.decodeFromString<OrdenTrabajoDetalleDto>(it.payload.toString()) }.getOrNull() }
        val orderDto = when {
            localOrder != null && snapshotOrder != null -> snapshotOrder.copy(
                code = localOrder.code, date = localOrder.date, client = localOrder.client,
                description = localOrder.description, mineralWeight = localOrder.mineralWeight,
                weightUnit = localOrder.weightUnit, status = localOrder.status, statusCode = localOrder.statusCode,
            )
            localOrder != null -> localOrder
            snapshotOrder != null -> snapshotOrder
            else -> throw OrdenesException("No se encontró la orden en la descarga local.")
        }
        val local = offlineSync.localEntities(ownerId)
        val localProcesses = local.filter { it.type == "process" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<ProcesoOrdenDto>(it.payload.toString()) }.getOrNull() }
        val localWorks = local.filter { it.type == "employee_work" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<TrabajoEmpleadoDto>(it.payload.toString()) }.getOrNull() }
        val localEquipment = local.filter { it.type == "equipment" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<EquipoProcesoDto>(it.payload.toString()) }.getOrNull() }
        return orderDto.copy(
            processes = (orderDto.processes + localProcesses).distinctBy { it.id },
            employeeWork = (orderDto.employeeWork + localWorks).distinctBy { it.id },
            equipment = (orderDto.equipment + localEquipment).distinctBy { it.id },
        ).toModel()
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

private fun OrdenTrabajo.toOfflineJson(clientId: Long? = null, unitId: Long? = null): JsonObject = buildJsonObject {
    put("id", id)
    put("code", codigo)
    fecha?.let { put("date", it) }
    put("client", buildJsonObject {
        clientId?.let { put("id", it) }
        cliente?.let { put("name", it) }
    })
    descripcion?.let { put("description", it) }
    pesoMineral?.let { put("mineral_weight", it) }
    unidadPeso?.let { put("weight_unit", it) }
    unitId?.let { put("weight_unit_id", it) }
    put("status", estado)
    put("status_code", codigoEstado)
}

private fun Double.roundMoney(): Double = (this * 100).roundToInt() / 100.0

@Serializable
private data class OfflineEmployeeRateDto(
    @SerialName("employee_id") val employeeId: Long,
    @SerialName("payment_type_id") val paymentTypeId: Long,
    val rate: Double,
    @SerialName("currency_id") val currencyId: Long,
    val currency: String? = null,
    @SerialName("is_base_currency") val isBaseCurrency: Boolean,
    @SerialName("calculation_method") val calculationMethod: String? = null,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    val active: Boolean,
)

@Serializable
private data class OfflineExchangeRateDto(
    @SerialName("moneda_id") val currencyId: Long,
    val fecha: String,
    @SerialName("valor") val rate: Double,
)

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
    @SerialName("cliente_id") val clientId: Long,
    @SerialName("fecha") val date: String?,
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
    @SerialName("remaining_value") val remainingValue: Double = 0.0,
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
    @SerialName("weight_unit_id") val weightUnitId: Long? = null,
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
