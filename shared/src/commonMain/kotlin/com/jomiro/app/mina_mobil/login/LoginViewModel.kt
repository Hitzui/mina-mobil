package com.jomiro.app.mina_mobil.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jomiro.app.mina_mobil.data.AuthRepository
import com.jomiro.app.mina_mobil.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val session: UserSession? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    init {
        mutableState.update { it.copy(session = authRepository.currentSession()) }
    }

    fun emailChanged(value: String) {
        mutableState.update { it.copy(email = value, error = null) }
    }

    fun passwordChanged(value: String) {
        mutableState.update { it.copy(password = value, error = null) }
    }

    fun login() {
        val current = mutableState.value
        if (current.isLoading) return
        if (current.email.isBlank() || current.password.isBlank()) {
            mutableState.update { it.copy(error = "Ingresa tu usuario y contraseña.") }
            return
        }

        mutableState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { authRepository.login(current.email, current.password) }
                .onSuccess { session ->
                    mutableState.update { it.copy(isLoading = false, password = "", session = session) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isLoading = false, error = error.message ?: "Ocurrió un error al iniciar sesión.")
                    }
                }
        }
    }

    fun logout() {
        mutableState.update { it.copy(password = "", error = null, session = null) }
        viewModelScope.launch { authRepository.logout() }
    }
}
