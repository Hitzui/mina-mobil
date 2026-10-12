package com.jomiro.app.mina_mobil.screen.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class OfflineSyncUiState(
    val downloaded: Boolean = false,
    val pending: Long = 0,
    val running: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class OfflineSyncViewModel(private val repository: OfflineSyncRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(OfflineSyncUiState())
    val state: StateFlow<OfflineSyncUiState> = mutableState.asStateFlow()

    fun load(userId: Long) {
        mutableState.update { it.copy(downloaded = repository.downloaded(userId), pending = repository.pendingCount(userId)) }
    }

    fun download(userId: Long) = run(userId, "Datos descargados. La copia local anterior fue reemplazada.") {
        repository.download(userId)
    }

    fun upload(userId: Long) = run(userId, "Cambios enviados. Descargue nuevamente los datos para continuar.") {
        repository.upload(userId)
    }

    private fun run(userId: Long, success: String, action: suspend () -> Unit) {
        if (mutableState.value.running) return
        mutableState.update { it.copy(running = true, error = null, message = null) }
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { action() }
                .onSuccess {
                    mutableState.update {
                        it.copy(running = false, downloaded = repository.downloaded(userId), pending = repository.pendingCount(userId), message = success)
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(running = false, error = error.message ?: "No se pudo sincronizar.", downloaded = repository.downloaded(userId), pending = repository.pendingCount(userId))
                    }
                }
        }
    }
}
