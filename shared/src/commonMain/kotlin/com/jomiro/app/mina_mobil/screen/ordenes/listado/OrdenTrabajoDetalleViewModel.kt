package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.ordenes.DetalleOrdenTrabajo
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
)

class OrdenTrabajoDetalleViewModel(
    private val repository: OrdenesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrdenTrabajoDetalleUiState())
    val state: StateFlow<OrdenTrabajoDetalleUiState> = mutableState.asStateFlow()
    private var loadedOrderId: Long? = null

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
}
