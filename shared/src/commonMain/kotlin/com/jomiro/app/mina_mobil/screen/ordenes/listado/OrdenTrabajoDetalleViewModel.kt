package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.ordenes.DetalleOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.EtapaOpcionProceso
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesTrabajoEmpleado
import com.jomiro.app.mina_mobil.data.ordenes.TarifaTrabajoEmpleado
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdenTrabajoDetalleUiState(
    val detalle: DetalleOrdenTrabajo? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val finalizandoOrden: Boolean = false,
    val finalizacionExitosa: Boolean = false,
    val errorFinalizacion: String? = null,
    val etapasProceso: List<EtapaOpcionProceso> = emptyList(),
    val cargandoEtapasProceso: Boolean = false,
    val creandoProceso: Boolean = false,
    val procesoAgregado: Boolean = false,
    val errorProceso: String? = null,
    val opcionesTrabajo: OpcionesTrabajoEmpleado? = null,
    val cargandoOpcionesTrabajo: Boolean = false,
    val tarifaTrabajo: TarifaTrabajoEmpleado? = null,
    val cargandoTarifaTrabajo: Boolean = false,
    val creandoTrabajo: Boolean = false,
    val trabajoAgregado: Boolean = false,
    val errorTrabajo: String? = null,
)

class OrdenTrabajoDetalleViewModel(
    private val repository: OrdenesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrdenTrabajoDetalleUiState())
    val state: StateFlow<OrdenTrabajoDetalleUiState> = mutableState.asStateFlow()
    private var loadedOrderId: Long? = null
    private var tarifaTrabajoVersion = 0

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
}
