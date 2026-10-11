package com.jomiro.app.mina_mobil.screen.clientes.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.clientes.Cliente
import org.koin.compose.viewmodel.koinViewModel

private val Ink = Color(0xFF172C3D)
private val Teal = Color(0xFF087E83)
private val Muted = Color(0xFF71808B)
private val CardSurface = Color.White

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ClientesListadoScreen(
    userId: Long,
    modifier: Modifier = Modifier,
    viewModel: ClientesListadoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(userId) { viewModel.cargarParaUsuario(userId) }

    val clientesVisibles = state.clientes.filter { cliente ->
        val query = state.busqueda.trim()
        query.isEmpty() || cliente.nombre.contains(query, ignoreCase = true) ||
            cliente.id.toString().contains(query) || cliente.telefono.orEmpty().contains(query, ignoreCase = true)
    }

    PullToRefreshBox(
        isRefreshing = state.refrescando,
        onRefresh = viewModel::refrescar,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.cargando && state.clientes.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item { LoadingState(Modifier.fillMaxWidth()) }
            }
            state.error != null && state.clientes.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item { ErrorState(state.error.orEmpty(), viewModel::reintentar, Modifier.fillMaxWidth()) }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.busqueda,
                        onValueChange = viewModel::actualizarBusqueda,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Buscar clientes") },
                        placeholder = { Text("Nombre, teléfono o número") },
                        leadingIcon = { Text("⌕", color = Teal, fontSize = 22.sp) },
                        shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, focusedLabelColor = Teal),
                    )
                    val shown = clientesVisibles.size
                    Text(
                        text = if (state.busqueda.isBlank()) "$shown ${if (shown == 1) "cliente" else "clientes"}"
                        else "$shown resultados de ${state.total} clientes",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 3.dp, top = 10.dp, bottom = 3.dp),
                    )
                }
                if (state.error != null) item { InlineError(state.error.orEmpty(), viewModel::refrescar) }
                if (clientesVisibles.isEmpty()) {
                    item { EmptyState(Modifier.fillMaxWidth()) }
                } else {
                    items(clientesVisibles, key = Cliente::id) { cliente -> ClienteCard(cliente) }
                }
            }
        }
    }
}

@Composable
private fun ClienteCard(cliente: Cliente) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = Teal.copy(alpha = 0.1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = cliente.nombre.firstOrNull()?.uppercase() ?: "?",
                            color = Teal,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(
                        text = cliente.nombre,
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Cliente #${cliente.id}", color = Muted, fontSize = 12.sp)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (cliente.activo) Color(0xFFE5F4F0) else Color(0xFFF2E9E9),
                ) {
                    Text(
                        text = if (cliente.activo) "Activo" else "Inactivo",
                        color = if (cliente.activo) Color(0xFF267A5B) else Color(0xFF956060),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            if (!cliente.telefono.isNullOrBlank() || !cliente.direccion.isNullOrBlank()) {
                Spacer(Modifier.height(13.dp))
                cliente.telefono?.takeIf(String::isNotBlank)?.let {
                    Text("Teléfono  $it", color = Muted, fontSize = 13.sp)
                }
                cliente.direccion?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier.height(300.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Teal)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier.height(220.dp), contentAlignment = Alignment.Center) {
        Text("No hay clientes registrados.", color = Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.height(300.dp).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color(0xFFB33A3A), fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Color(0xFFB33A3A), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
            Text("Reintentar")
        }
    }
}
