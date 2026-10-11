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
    val busqueda: String = "",
    val total: Int = 0,
    val cargando: Boolean = false,
    val refrescando: Boolean = false,
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
    private var activeUserId: Long? = null

    fun cargarParaUsuario(userId: Long) {
        val actual = mutableState.value
        if (loadedForUserId == userId && (actual.clientes.isNotEmpty() || actual.cargando)) return
        loadedForUserId = userId
        activeUserId = userId
        val locales = clientesRepository.listarLocales(userId)
        mutableState.value = ClientesListadoUiState(
            clientes = locales,
            total = locales.size,
            cargando = locales.isEmpty(),
            paginaActual = if (locales.isEmpty()) 0 else 1,
        )
        if (locales.isEmpty()) sincronizarDesdeApi()
    }

    fun actualizarBusqueda(value: String) = mutableState.update { it.copy(busqueda = value) }

    fun reintentar() = sincronizarDesdeApi()

    fun refrescar() = sincronizarDesdeApi(manual = true)

    fun cargarMas() {
        // Se carga y cachea el catálogo completo, por eso no hay más páginas locales.
    }

    private fun sincronizarDesdeApi(manual: Boolean = false) {
        val ownerId = activeUserId ?: return
        val current = mutableState.value
        if (current.cargando || current.refrescando) return
        mutableState.update {
            it.copy(cargando = current.clientes.isEmpty(), refrescando = manual, error = null)
        }
        viewModelScope.launch {
            runCatching {
                val todos = mutableListOf<Cliente>()
                var numeroPagina = 1
                var ultimaPagina = 1
                while (numeroPagina <= ultimaPagina) {
                    val page = clientesRepository.listar(numeroPagina)
                    todos += page.clientes
                    ultimaPagina = page.ultimaPagina
                    numeroPagina++
                }
                clientesRepository.guardarLocales(ownerId, todos)
                todos
            }
                .onSuccess { clientes ->
                    mutableState.update {
                        it.copy(
                            clientes = clientes,
                            total = clientes.size,
                            cargando = false,
                            refrescando = false,
                            paginaActual = 1,
                            ultimaPagina = 1,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            cargando = false,
                            refrescando = false,
                            error = error.message ?: "No se pudo cargar la lista de clientes.",
                        )
                    }
                }
        }
    }
}
