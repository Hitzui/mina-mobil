package com.jomiro.app.mina_mobil.screen.ordenes.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.LiquidacionOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoLiquidacionOpcion
import com.jomiro.app.mina_mobil.data.ordenes.IngresoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesIngresoOrden
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrdenTrabajoDetalleViewModel(
    private val repository: OrdenesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrdenTrabajoDetalleUiState())
    val state: StateFlow<OrdenTrabajoDetalleUiState> = mutableState.asStateFlow()
    private var loadedOrderId: Long? = null
    private var tarifaTrabajoVersion = 0
    private var depreciacionEquipoVersion = 0

    fun cargar(id: Long, reintentar: Boolean = false) {
        val current = mutableState.value
        if (!reintentar && (loadedOrderId == id && (current.detalle != null || current.cargando))) return
        loadedOrderId = id
        mutableState.value = OrdenTrabajoDetalleUiState(cargando = true)
        viewModelScope.launch {
            runCatching { repository.detalle(id) }
                .onSuccess { detalle -> mutableState.update { it.copy(detalle = detalle, cargando = false, error = null) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargando = false, error = error.message ?: "No se pudo cargar el detalle de la orden.")
                    }
                }
        }
    }

    fun finalizarOrden(id: Long) {
        if (mutableState.value.finalizandoOrden || mutableState.value.finalizacionExitosa) return
        mutableState.update { it.copy(finalizandoOrden = true, errorFinalizacion = null) }
        viewModelScope.launch {
            runCatching { repository.finalizar(id) }
                .onSuccess {
                    mutableState.update { current ->
                        val detalle = current.detalle
                        current.copy(
                            detalle = detalle?.copy(
                                orden = detalle.orden.copy(estado = "Finalizada", codigoEstado = 3),
                            ),
                            finalizandoOrden = false,
                            finalizacionExitosa = true,
                            errorFinalizacion = null,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            finalizandoOrden = false,
                            errorFinalizacion = error.message ?: "No se pudo finalizar la orden.",
                        )
                    }
                }
        }
    }

    fun limpiarMensajeFinalizacion() {
        mutableState.update { it.copy(finalizacionExitosa = false, errorFinalizacion = null) }
    }

    fun cargarEtapasProceso(id: Long) {
        if (mutableState.value.cargandoEtapasProceso) return
        mutableState.update { it.copy(cargandoEtapasProceso = true, errorProceso = null, procesoAgregado = false) }
        viewModelScope.launch {
            runCatching { repository.opcionesProceso(id) }
                .onSuccess { etapas ->
                    mutableState.update { it.copy(etapasProceso = etapas, cargandoEtapasProceso = false) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            cargandoEtapasProceso = false,
                            errorProceso = error.message ?: "No se pudieron cargar las etapas.",
                        )
                    }
                }
        }
    }

    fun agregarProceso(
        ordenId: Long,
        etapaId: Long,
        fechaInicio: String?,
        fechaFin: String?,
        pesoEntrada: Double?,
        pesoSalida: Double?,
        observaciones: String,
    ) {
        if (mutableState.value.creandoProceso) return
        mutableState.update { it.copy(creandoProceso = true, errorProceso = null, procesoAgregado = false) }
        viewModelScope.launch {
            runCatching {
                repository.crearProceso(ordenId, etapaId, fechaInicio, fechaFin, pesoEntrada, pesoSalida, observaciones)
            }.onSuccess {
                val detalleActualizado = runCatching { repository.detalle(ordenId) }
                mutableState.update { current ->
                    current.copy(
                        detalle = detalleActualizado.getOrNull() ?: current.detalle,
                        creandoProceso = false,
                        procesoAgregado = true,
                        errorProceso = detalleActualizado.exceptionOrNull()?.let {
                            "El proceso se agregó, pero no se pudo actualizar el detalle. Desliza para actualizar."
                        },
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        creandoProceso = false,
                        errorProceso = error.message ?: "No se pudo agregar el proceso.",
                    )
                }
            }
        }
    }

    fun limpiarMensajeProceso() {
        mutableState.update { it.copy(procesoAgregado = false, errorProceso = null) }
    }

    fun cargarOpcionesTrabajo(ordenId: Long, procesoId: Long) {
        if (mutableState.value.cargandoOpcionesTrabajo) return
        tarifaTrabajoVersion++
        mutableState.update {
            it.copy(
                opcionesTrabajo = null,
                cargandoOpcionesTrabajo = true,
                errorTrabajo = null,
                trabajoAgregado = false,
                tarifaTrabajo = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.opcionesTrabajo(ordenId, procesoId) }
                .onSuccess { options -> mutableState.update { it.copy(opcionesTrabajo = options, cargandoOpcionesTrabajo = false) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargandoOpcionesTrabajo = false, errorTrabajo = error.message ?: "No se pudieron cargar las opciones del trabajo.")
                    }
                }
        }
    }

    fun consultarTarifaTrabajo(ordenId: Long, procesoId: Long, empleadoId: Long?, tipoPagoId: Long?, fecha: String) {
        val requestVersion = ++tarifaTrabajoVersion
        if (empleadoId == null || tipoPagoId == null || fecha.isBlank()) {
            mutableState.update { it.copy(tarifaTrabajo = null, cargandoTarifaTrabajo = false, errorTrabajo = null) }
            return
        }
        mutableState.update { it.copy(cargandoTarifaTrabajo = true, tarifaTrabajo = null, errorTrabajo = null) }
        viewModelScope.launch {
            runCatching { repository.tarifaTrabajo(ordenId, procesoId, empleadoId, tipoPagoId, fecha) }
                .onSuccess { rate ->
                    if (requestVersion == tarifaTrabajoVersion) {
                        mutableState.update { it.copy(tarifaTrabajo = rate, cargandoTarifaTrabajo = false) }
                    }
                }
                .onFailure { error ->
                    if (requestVersion == tarifaTrabajoVersion) {
                        mutableState.update {
                            it.copy(cargandoTarifaTrabajo = false, tarifaTrabajo = null, errorTrabajo = error.message ?: "No se pudo consultar la tarifa vigente.")
                        }
                    }
                }
        }
    }

    fun agregarTrabajo(
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
        if (mutableState.value.creandoTrabajo) return
        mutableState.update { it.copy(creandoTrabajo = true, errorTrabajo = null, trabajoAgregado = false) }
        viewModelScope.launch {
            runCatching {
                repository.crearTrabajo(
                    ordenId, procesoId, empleadoId, tipoPagoId, fecha,
                    horaInicio, horaFin, cantidad, unidadId, descripcion, observaciones,
                )
            }.onSuccess {
                val updatedDetail = runCatching { repository.detalle(ordenId) }
                mutableState.update { current ->
                    current.copy(
                        detalle = updatedDetail.getOrNull() ?: current.detalle,
                        creandoTrabajo = false,
                        trabajoAgregado = true,
                        errorTrabajo = updatedDetail.exceptionOrNull()?.let {
                            "El trabajo se registró, pero no se pudo actualizar el detalle. Desliza para actualizar."
                        },
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(creandoTrabajo = false, errorTrabajo = error.message ?: "No se pudo registrar el trabajo.")
                }
            }
        }
    }

    fun limpiarMensajeTrabajo() {
        mutableState.update { it.copy(trabajoAgregado = false, errorTrabajo = null, tarifaTrabajo = null) }
    }

    fun cargarOpcionesEquipo(ordenId: Long, procesoId: Long) {
        if (mutableState.value.cargandoOpcionesEquipo) return
        depreciacionEquipoVersion++
        mutableState.update {
            it.copy(
                opcionesEquipo = null,
                depreciacionEquipo = null,
                cargandoOpcionesEquipo = true,
                errorEquipo = null,
                equipoAsignado = false,
            )
        }
        viewModelScope.launch {
            runCatching { repository.opcionesEquipo(ordenId, procesoId) }
                .onSuccess { options ->
                    mutableState.update { it.copy(opcionesEquipo = options, cargandoOpcionesEquipo = false) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargandoOpcionesEquipo = false, errorEquipo = error.message ?: "No se pudieron cargar los equipos.")
                    }
                }
        }
    }

    fun consultarDepreciacionEquipo(
        ordenId: Long,
        procesoId: Long,
        equipoId: Long?,
        inicio: String,
        fin: String?,
        horas: Double? = null,
    ) {
        val requestVersion = ++depreciacionEquipoVersion
        if (equipoId == null || inicio.isBlank()) {
            mutableState.update { it.copy(depreciacionEquipo = null, calculandoDepreciacionEquipo = false, errorEquipo = null) }
            return
        }
        mutableState.update { it.copy(depreciacionEquipo = null, calculandoDepreciacionEquipo = true, errorEquipo = null) }
        viewModelScope.launch {
            runCatching { repository.depreciacionEquipo(ordenId, procesoId, equipoId, inicio, fin, horas) }
                .onSuccess { estimate ->
                    if (requestVersion == depreciacionEquipoVersion) {
                        mutableState.update { it.copy(depreciacionEquipo = estimate, calculandoDepreciacionEquipo = false) }
                    }
                }
                .onFailure { error ->
                    if (requestVersion == depreciacionEquipoVersion) {
                        mutableState.update {
                            it.copy(
                                calculandoDepreciacionEquipo = false,
                                errorEquipo = error.message ?: "No se pudo calcular la depreciación del equipo.",
                            )
                        }
                    }
                }
        }
    }

    fun asignarEquipo(
        ordenId: Long,
        procesoId: Long,
        equipoId: Long,
        inicio: String,
        fin: String?,
        observaciones: String,
        horas: Double? = null,
    ) {
        if (mutableState.value.asignandoEquipo) return
        mutableState.update { it.copy(asignandoEquipo = true, errorEquipo = null, equipoAsignado = false) }
        viewModelScope.launch {
            runCatching { repository.asignarEquipo(ordenId, procesoId, equipoId, inicio, fin, observaciones, horas) }
                .onSuccess {
                    val refreshed = runCatching { repository.detalle(ordenId) }
                    mutableState.update { current ->
                        current.copy(
                            detalle = refreshed.getOrNull() ?: current.detalle,
                            asignandoEquipo = false,
                            equipoAsignado = true,
                            errorEquipo = refreshed.exceptionOrNull()?.let {
                                "El equipo se asignó, pero no se pudo actualizar el detalle. Vuelve a cargar la orden."
                            },
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(asignandoEquipo = false, errorEquipo = error.message ?: "No se pudo asignar el equipo.")
                    }
                }
        }
    }

    fun limpiarMensajeEquipo() {
        mutableState.update {
            it.copy(
                equipoAsignado = false,
                errorEquipo = null,
                opcionesEquipo = null,
                depreciacionEquipo = null,
                calculandoDepreciacionEquipo = false,
            )
        }
    }

    fun cargarFormularioEdicion(ordenId: Long) {
        if (mutableState.value.cargandoFormularioEdicion) return
        mutableState.update {
            it.copy(
                formularioEdicion = null,
                cargandoFormularioEdicion = true,
                edicionGuardada = false,
                errorEdicion = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.opcionesEdicion(ordenId) }
                .onSuccess { form ->
                    mutableState.update { it.copy(formularioEdicion = form, cargandoFormularioEdicion = false) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargandoFormularioEdicion = false, errorEdicion = error.message ?: "No se pudo cargar el formulario de edición.")
                    }
                }
        }
    }

    fun actualizarOrden(
        ordenId: Long,
        clienteId: Long,
        fecha: String,
        pesoMineral: Double,
        unidadPesoId: Long,
        descripcion: String,
    ) {
        val form = mutableState.value.formularioEdicion ?: return
        if (mutableState.value.guardandoEdicion) return
        mutableState.update { it.copy(guardandoEdicion = true, errorEdicion = null, edicionGuardada = false) }
        viewModelScope.launch {
            runCatching {
                repository.actualizar(
                    ordenId, clienteId, fecha, pesoMineral, unidadPesoId,
                    descripcion, form.tieneProcesos,
                )
            }.onSuccess { order ->
                mutableState.update { current ->
                    current.copy(
                        detalle = current.detalle?.copy(orden = order),
                        formularioEdicion = current.formularioEdicion?.copy(orden = order),
                        guardandoEdicion = false,
                        edicionGuardada = true,
                        errorEdicion = null,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(guardandoEdicion = false, errorEdicion = error.message ?: "No se pudo actualizar la orden.")
                }
            }
        }
    }

    fun limpiarResultadoEdicion() {
        mutableState.update {
            it.copy(
                formularioEdicion = null,
                cargandoFormularioEdicion = false,
                guardandoEdicion = false,
                edicionGuardada = false,
                errorEdicion = null,
            )
        }
    }

    fun cargarOpcionesAcuerdo() {
        if (mutableState.value.cargandoOpcionesAcuerdo || mutableState.value.opcionesAcuerdo != null) return
        mutableState.update { it.copy(cargandoOpcionesAcuerdo = true, errorAcuerdo = null) }
        viewModelScope.launch {
            runCatching { repository.opcionesAcuerdo() }
                .onSuccess { options -> mutableState.update { it.copy(opcionesAcuerdo = options, cargandoOpcionesAcuerdo = false) } }
                .onFailure { error -> mutableState.update { it.copy(cargandoOpcionesAcuerdo = false, errorAcuerdo = error.message ?: "No se pudieron cargar las opciones de acuerdos.") } }
        }
    }

    fun guardarAcuerdo(
        orderId: Long,
        agreementId: Long?,
        typeId: Long,
        clientPercentage: Double?,
        companyPercentage: Double?,
        serviceFee: Double?,
        currencyId: Long,
        observations: String,
        active: Boolean,
    ) {
        if (mutableState.value.guardandoAcuerdo) return
        mutableState.update { it.copy(guardandoAcuerdo = true, acuerdoGuardado = false, errorAcuerdo = null) }
        viewModelScope.launch {
            runCatching {
                repository.guardarAcuerdo(orderId, agreementId, typeId, clientPercentage, companyPercentage, serviceFee, currencyId, observations, active)
                repository.detalle(orderId)
            }.onSuccess { detail ->
                mutableState.update { it.copy(detalle = detail, guardandoAcuerdo = false, acuerdoGuardado = true) }
            }.onFailure { error ->
                mutableState.update { it.copy(guardandoAcuerdo = false, errorAcuerdo = error.message ?: "No se pudo guardar el acuerdo.") }
            }
        }
    }

    fun eliminarAcuerdo(orderId: Long, agreement: AcuerdoOrdenTrabajo) {
        if (mutableState.value.eliminandoAcuerdo) return
        mutableState.update { it.copy(eliminandoAcuerdo = true, acuerdoGuardado = false, errorAcuerdo = null) }
        viewModelScope.launch {
            runCatching {
                repository.eliminarAcuerdo(orderId, agreement)
                repository.detalle(orderId)
            }.onSuccess { detail -> mutableState.update { it.copy(detalle = detail, eliminandoAcuerdo = false, acuerdoGuardado = true) } }
                .onFailure { error -> mutableState.update { it.copy(eliminandoAcuerdo = false, errorAcuerdo = error.message ?: "No se pudo eliminar el acuerdo.") } }
        }
    }

    fun limpiarResultadoAcuerdo() {
        mutableState.update { it.copy(acuerdoGuardado = false, errorAcuerdo = null) }
    }

    fun cargarOpcionesLiquidacion(orderId: Long) {
        if (mutableState.value.cargandoOpcionesLiquidacion || mutableState.value.opcionesLiquidacion != null) return
        mutableState.update { it.copy(cargandoOpcionesLiquidacion = true, errorLiquidacion = null) }
        viewModelScope.launch {
            runCatching { repository.opcionesLiquidacion(orderId) }
                .onSuccess { options -> mutableState.update { it.copy(opcionesLiquidacion = options, cargandoOpcionesLiquidacion = false) } }
                .onFailure { error -> mutableState.update { it.copy(cargandoOpcionesLiquidacion = false, errorLiquidacion = error.message ?: "No se pudieron cargar los acuerdos liquidables.") } }
        }
    }

    fun guardarLiquidacion(
        orderId: Long,
        liquidation: LiquidacionOrdenTrabajo?,
        date: String,
        grams: Double,
        agreement: AcuerdoLiquidacionOpcion?,
        observations: String,
    ) {
        if (mutableState.value.guardandoLiquidacion) return
        mutableState.update { it.copy(guardandoLiquidacion = true, liquidacionGuardada = false, errorLiquidacion = null) }
        viewModelScope.launch {
            runCatching {
                repository.guardarLiquidacion(orderId, liquidation, date, grams, agreement, observations)
                repository.detalle(orderId)
            }.onSuccess { detail -> mutableState.update { it.copy(detalle = detail, guardandoLiquidacion = false, liquidacionGuardada = true) } }
                .onFailure { error -> mutableState.update { it.copy(guardandoLiquidacion = false, errorLiquidacion = error.message ?: "No se pudo guardar la liquidación.") } }
        }
    }

    fun eliminarLiquidacion(orderId: Long, liquidation: LiquidacionOrdenTrabajo) {
        if (mutableState.value.eliminandoLiquidacion) return
        mutableState.update { it.copy(eliminandoLiquidacion = true, liquidacionGuardada = false, errorLiquidacion = null) }
        viewModelScope.launch {
            runCatching {
                repository.eliminarLiquidacion(orderId, liquidation)
                repository.detalle(orderId)
            }.onSuccess { detail -> mutableState.update { it.copy(detalle = detail, eliminandoLiquidacion = false, liquidacionGuardada = true) } }
                .onFailure { error -> mutableState.update { it.copy(eliminandoLiquidacion = false, errorLiquidacion = error.message ?: "No se pudo eliminar la liquidación.") } }
        }
    }

    fun limpiarResultadoLiquidacion() {
        mutableState.update { it.copy(liquidacionGuardada = false, errorLiquidacion = null) }
    }

    fun cargarOpcionesIngreso(orderId: Long) {
        if (mutableState.value.cargandoOpcionesIngreso || mutableState.value.opcionesIngreso != null) return
        mutableState.update { it.copy(cargandoOpcionesIngreso = true, errorIngreso = null) }
        viewModelScope.launch {
            runCatching { repository.opcionesIngreso(orderId) }
                .onSuccess { options -> mutableState.update { it.copy(opcionesIngreso = options, cargandoOpcionesIngreso = false) } }
                .onFailure { error -> mutableState.update { it.copy(cargandoOpcionesIngreso = false, errorIngreso = error.message ?: "No se pudieron cargar las opciones de ingresos.") } }
        }
    }

    fun guardarIngreso(
        orderId: Long,
        income: IngresoOrdenTrabajo?,
        typeId: Long,
        date: String,
        description: String,
        quantity: Double,
        unitId: Long?,
        currencyId: Long,
        unitPrice: Double,
        observations: String,
        options: OpcionesIngresoOrden,
    ) {
        if (mutableState.value.guardandoIngreso) return
        mutableState.update { it.copy(guardandoIngreso = true, ingresoGuardado = false, errorIngreso = null) }
        viewModelScope.launch {
            runCatching {
                repository.guardarIngreso(orderId, income, typeId, date, description, quantity, unitId, currencyId, unitPrice, observations, options)
                repository.detalle(orderId)
            }.onSuccess { detail -> mutableState.update { it.copy(detalle = detail, guardandoIngreso = false, ingresoGuardado = true) } }
                .onFailure { error -> mutableState.update { it.copy(guardandoIngreso = false, errorIngreso = error.message ?: "No se pudo guardar el ingreso.") } }
        }
    }

    fun eliminarIngreso(orderId: Long, income: IngresoOrdenTrabajo) {
        if (mutableState.value.eliminandoIngreso) return
        mutableState.update { it.copy(eliminandoIngreso = true, ingresoGuardado = false, errorIngreso = null) }
        viewModelScope.launch {
            runCatching {
                repository.eliminarIngreso(orderId, income)
                repository.detalle(orderId)
            }.onSuccess { detail -> mutableState.update { it.copy(detalle = detail, eliminandoIngreso = false, ingresoGuardado = true) } }
                .onFailure { error -> mutableState.update { it.copy(eliminandoIngreso = false, errorIngreso = error.message ?: "No se pudo eliminar el ingreso.") } }
        }
    }

    fun limpiarResultadoIngreso() {
        mutableState.update { it.copy(ingresoGuardado = false, errorIngreso = null) }
    }
}
