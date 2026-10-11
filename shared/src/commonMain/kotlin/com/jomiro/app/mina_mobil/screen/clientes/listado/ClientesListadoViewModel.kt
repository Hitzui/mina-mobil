package com.jomiro.app.mina_mobil.screen.clientes.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.clientes.Cliente
import com.jomiro.app.mina_mobil.data.clientes.ClientesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClientesListadoUiState(
    val clientes: List<Cliente> = emptyList(),
    val total: Int = 0,
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val error: String? = null,
    val paginaActual: Int = 0,
    val ultimaPagina: Int = 1,
) {
    val hayMas: Boolean get() = paginaActual < ultimaPagina
}

class ClientesListadoViewModel(
    private val clientesRepository: ClientesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ClientesListadoUiState())
    val state: StateFlow<ClientesListadoUiState> = mutableState.asStateFlow()
    private var loadedForUserId: Long? = null

    fun cargarParaUsuario(userId: Long) {
        val actual = mutableState.value
        if (loadedForUserId == userId && (actual.paginaActual > 0 || actual.cargando)) return

        loadedForUserId = userId
        mutableState.value = ClientesListadoUiState()
        cargarPrimeraPagina()
    }

    fun reintentar() = cargarPrimeraPagina()

    fun cargarMas() {
        val actual = mutableState.value
        if (actual.cargando || actual.cargandoMas || !actual.hayMas) return

        mutableState.update { it.copy(cargandoMas = true, error = null) }
        viewModelScope.launch {
            runCatching { clientesRepository.listar(actual.paginaActual + 1) }
                .onSuccess { pagina ->
                    mutableState.update { state ->
                        state.copy(
                            clientes = state.clientes + pagina.clientes,
                            total = pagina.total,
                            cargandoMas = false,
                            paginaActual = pagina.paginaActual,
                            ultimaPagina = pagina.ultimaPagina,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargandoMas = false, error = error.message ?: "No se pudieron cargar más clientes.")
                    }
                }
        }
    }

    private fun cargarPrimeraPagina() {
        if (mutableState.value.cargando) return
        mutableState.update { it.copy(cargando = true, cargandoMas = false, error = null) }
        viewModelScope.launch {
            runCatching { clientesRepository.listar(1) }
                .onSuccess { pagina ->
                    mutableState.update {
                        it.copy(
                            clientes = pagina.clientes,
                            total = pagina.total,
                            cargando = false,
                            paginaActual = pagina.paginaActual,
                            ultimaPagina = pagina.ultimaPagina,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargando = false, error = error.message ?: "No se pudo cargar la lista de clientes.")
                    }
                }
        }
    }
}
