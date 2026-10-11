package com.jomiro.app.mina_mobil.screen.empleados.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.empleados.Empleado
import com.jomiro.app.mina_mobil.data.empleados.EmpleadosRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmpleadosUiState(
    val empleados: List<Empleado> = emptyList(),
    val busqueda: String = "",
    val cargando: Boolean = false,
    val refrescando: Boolean = false,
    val error: String? = null,
)

class EmpleadosViewModel(private val repository: EmpleadosRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(EmpleadosUiState())
    val state: StateFlow<EmpleadosUiState> = mutableState.asStateFlow()
    private var loadedForUser: Long? = null
    private var searchJob: Job? = null
    private var requestJob: Job? = null

    fun cargarParaUsuario(userId: Long) {
        if (loadedForUser == userId && (mutableState.value.empleados.isNotEmpty() || mutableState.value.cargando)) return
        loadedForUser = userId
        buscarAhora("", refrescando = false)
    }

    fun actualizarBusqueda(value: String) {
        mutableState.update { it.copy(busqueda = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            buscarAhora(value, refrescando = false)
        }
    }

    fun refrescar() = buscarAhora(mutableState.value.busqueda, refrescando = true)

    fun reintentar() = buscarAhora(mutableState.value.busqueda, refrescando = false)

    private fun buscarAhora(query: String, refrescando: Boolean) {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutableState.update {
                it.copy(cargando = !refrescando, refrescando = refrescando, error = null)
            }
            runCatching { repository.listar(query) }
                .onSuccess { empleados ->
                    mutableState.update {
                        it.copy(empleados = empleados, cargando = false, refrescando = false)
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            cargando = false,
                            refrescando = false,
                            error = error.message ?: "No se pudieron cargar los empleados.",
                        )
                    }
                }
        }
    }
}
