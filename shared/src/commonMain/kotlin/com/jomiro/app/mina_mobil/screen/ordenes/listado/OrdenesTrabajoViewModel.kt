package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.clientes.Cliente
import com.jomiro.app.mina_mobil.data.clientes.ClientesRepository
import com.jomiro.app.mina_mobil.data.ordenes.OrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

enum class VistaOrdenes { LISTADO, CALENDARIO }

data class OrdenesTrabajoUiState(
    val ordenes: List<OrdenTrabajo> = emptyList(),
    val ordenesDelMes: List<OrdenTrabajo> = emptyList(),
    val clientes: List<Cliente> = emptyList(),
    val filtroCodigo: String = "",
    val filtroClienteId: Long? = null,
    val filtroFechaDesde: String = "",
    val filtroFechaHasta: String = "",
    val total: Int = 0,
    val paginaActual: Int = 0,
    val ultimaPagina: Int = 1,
    val vista: VistaOrdenes = VistaOrdenes.LISTADO,
    val mes: LocalDate,
    val fechaSeleccionada: LocalDate,
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val cargandoMes: Boolean = false,
    val refrescando: Boolean = false,
    val error: String? = null,
    val errorCalendario: String? = null,
) {
    val hayMas: Boolean get() = paginaActual < ultimaPagina
}

class OrdenesTrabajoViewModel(
    private val repository: OrdenesRepository,
    private val clientesRepository: ClientesRepository,
) : ViewModel() {
    private val hoy = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val mutableState = MutableStateFlow(
        OrdenesTrabajoUiState(
            mes = LocalDate(hoy.year, hoy.month, 1),
            fechaSeleccionada = hoy,
        ),
    )
    val state: StateFlow<OrdenesTrabajoUiState> = mutableState.asStateFlow()
    private var loadedForUserId: Long? = null
    private var loadedMonth: LocalDate? = null

    fun cargarParaUsuario(userId: Long) {
        val current = mutableState.value
        if (loadedForUserId == userId && (current.paginaActual > 0 || current.cargando)) return
        loadedForUserId = userId
        loadedMonth = null
        mutableState.value = current.copy(
            ordenes = emptyList(),
            ordenesDelMes = emptyList(),
            total = 0,
            paginaActual = 0,
            ultimaPagina = 1,
            cargando = false,
            cargandoMas = false,
            cargandoMes = false,
            error = null,
            errorCalendario = null,
        )
        cargarPrimeraPagina()
        cargarClientes()
    }

    fun reintentarListado() = cargarPrimeraPagina()

    fun actualizarFiltroCodigo(value: String) = mutableState.update { it.copy(filtroCodigo = value) }
    fun actualizarFiltroCliente(clienteId: Long?) = mutableState.update { it.copy(filtroClienteId = clienteId) }
    fun actualizarFiltroFechaDesde(value: String) = mutableState.update { it.copy(filtroFechaDesde = value) }
    fun actualizarFiltroFechaHasta(value: String) = mutableState.update { it.copy(filtroFechaHasta = value) }

    fun aplicarFiltros() {
        mutableState.update { it.copy(ordenes = emptyList(), total = 0, paginaActual = 0, ultimaPagina = 1, error = null) }
        cargarPrimeraPagina()
    }

    fun limpiarFiltros() {
        mutableState.update {
            it.copy(
                filtroCodigo = "", filtroClienteId = null, filtroFechaDesde = "", filtroFechaHasta = "",
                ordenes = emptyList(), total = 0, paginaActual = 0, ultimaPagina = 1, error = null,
            )
        }
        cargarPrimeraPagina()
    }

    fun cargarMas() {
        val current = mutableState.value
        if (current.cargando || current.cargandoMas || !current.hayMas) return
        mutableState.update { it.copy(cargandoMas = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.listar(
                    current.paginaActual + 1, current.filtroCodigo, current.filtroClienteId,
                    current.filtroFechaDesde.filtro(), current.filtroFechaHasta.filtro(),
                )
            }
                .onSuccess { page ->
                    mutableState.update {
                        it.copy(
                            ordenes = it.ordenes + page.ordenes,
                            total = page.total,
                            paginaActual = page.paginaActual,
                            ultimaPagina = page.ultimaPagina,
                            cargandoMas = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargandoMas = false, error = error.message ?: "No se pudieron cargar más órdenes.")
                    }
                }
        }
    }

    fun mostrarVista(vista: VistaOrdenes) {
        mutableState.update { it.copy(vista = vista) }
        if (vista == VistaOrdenes.CALENDARIO && loadedMonth != mutableState.value.mes) {
            cargarMes()
        }
    }

    fun cambiarMes(meses: Int) {
        val month = mutableState.value.mes.plus(meses, DateTimeUnit.MONTH)
        mutableState.update {
            it.copy(
                mes = month,
                fechaSeleccionada = LocalDate(month.year, month.month, 1),
                ordenesDelMes = emptyList(),
                errorCalendario = null,
            )
        }
        cargarMes()
    }

    fun seleccionarFecha(fecha: LocalDate) {
        mutableState.update { it.copy(fechaSeleccionada = fecha) }
    }

    fun reintentarCalendario() = cargarMes()

    fun refrescar() {
        if (mutableState.value.vista == VistaOrdenes.CALENDARIO) cargarMes(forzar = true)
        else cargarPrimeraPagina(refrescando = true)
    }

    private fun cargarPrimeraPagina(refrescando: Boolean = false) {
        if (mutableState.value.cargando || mutableState.value.refrescando) return
        mutableState.update { it.copy(cargando = !refrescando, refrescando = refrescando, error = null) }
        viewModelScope.launch {
            val filters = mutableState.value
            runCatching {
                repository.listar(
                    1, filters.filtroCodigo, filters.filtroClienteId,
                    filters.filtroFechaDesde.filtro(), filters.filtroFechaHasta.filtro(),
                )
            }
                .onSuccess { page ->
                    mutableState.update {
                        it.copy(
                            ordenes = page.ordenes,
                            total = page.total,
                            paginaActual = page.paginaActual,
                            ultimaPagina = page.ultimaPagina,
                            cargando = false,
                            refrescando = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargando = false, refrescando = false, error = error.message ?: "No se pudieron cargar las órdenes.")
                    }
                }
        }
    }

    private fun cargarClientes() {
        viewModelScope.launch {
            runCatching {
                val clientes = mutableListOf<Cliente>()
                var pagina = 1
                var ultimaPagina = 1
                while (pagina <= ultimaPagina) {
                    val response = clientesRepository.listar(pagina)
                    clientes += response.clientes
                    ultimaPagina = response.ultimaPagina
                    pagina++
                }
                clientes
            }.onSuccess { clientes -> mutableState.update { it.copy(clientes = clientes) } }
        }
    }

    private fun cargarMes(forzar: Boolean = false) {
        val month = mutableState.value.mes
        if (mutableState.value.cargandoMes) return
        mutableState.update { it.copy(cargandoMes = true, refrescando = forzar, errorCalendario = null) }
        viewModelScope.launch {
            val firstDay = LocalDate(month.year, month.month, 1)
            val lastDay = firstDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            runCatching { repository.calendario(firstDay.toString(), lastDay.toString()) }
                .onSuccess { orders ->
                    loadedMonth = month
                    mutableState.update { it.copy(ordenesDelMes = orders, cargandoMes = false, refrescando = false) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(cargandoMes = false, refrescando = false, errorCalendario = error.message ?: "No se pudo cargar el calendario.")
                    }
                }
        }
    }
}

private fun String.filtro(): String? = trim().takeIf(String::isNotEmpty)
