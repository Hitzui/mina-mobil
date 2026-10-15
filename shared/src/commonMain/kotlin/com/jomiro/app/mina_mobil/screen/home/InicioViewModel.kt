package com.jomiro.app.mina_mobil.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.InicioException
import com.jomiro.app.mina_mobil.data.InicioRepository
import com.jomiro.app.mina_mobil.data.PanelInicio
import com.jomiro.app.mina_mobil.data.PeriodoInicio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InicioUiState(
    val panel: PanelInicio? = null,
    val cargando: Boolean = false,
    val error: String? = null,
)

class InicioViewModel(private val repository: InicioRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(InicioUiState())
    val state: StateFlow<InicioUiState> = mutableState.asStateFlow()
    private var lastUserId: Long? = null
    private var lastPeriod: PeriodoInicio? = null
    private var requestId = 0

    fun cargar(ownerId: Long, periodo: PeriodoInicio, forzar: Boolean = false) {
        if (!forzar && lastUserId == ownerId && lastPeriod == periodo &&
            (mutableState.value.panel != null || mutableState.value.cargando)
        ) return
        lastUserId = ownerId
        lastPeriod = periodo
        val request = ++requestId
        mutableState.value = mutableState.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            runCatching { repository.cargar(ownerId, periodo) }
                .onSuccess { panel ->
                    if (request == requestId) mutableState.value = InicioUiState(panel = panel)
                }
                .onFailure { error ->
                    if (request == requestId) mutableState.value = InicioUiState(
                        error = error.message ?: "No se pudo cargar Inicio.",
                    )
                }
        }
    }
}
