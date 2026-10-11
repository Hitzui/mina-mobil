package com.jomiro.app.mina_mobil.screen.empleados.listado

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.jomiro.app.mina_mobil.data.empleados.Empleado
import org.koin.compose.viewmodel.koinViewModel

private val Ink = Color(0xFF172C3D)
private val Teal = Color(0xFF087E83)
private val Muted = Color(0xFF71808B)

@Composable
fun EmpleadosScreen(
    userId: Long,
    modifier: Modifier = Modifier,
    viewModel: EmpleadosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(userId) { viewModel.cargarParaUsuario(userId) }

    PullToRefreshBox(
        isRefreshing = state.refrescando,
        onRefresh = viewModel::refrescar,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.cargando && state.empleados.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
            state.error != null && state.empleados.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error.orEmpty(), color = Color(0xFFB33A3A))
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::reintentar, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                    Text("Reintentar")
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.busqueda,
                        onValueChange = viewModel::actualizarBusqueda,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Buscar empleados") },
                        placeholder = { Text("Nombre, código o teléfono") },
                        leadingIcon = { Text("⌕", color = Teal, fontSize = 22.sp) },
                        shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, focusedLabelColor = Teal),
                    )
                    Text(
                        "${state.empleados.size} ${if (state.empleados.size == 1) "empleado" else "empleados"}",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 3.dp, top = 9.dp, bottom = 2.dp),
                    )
                }
                if (state.error != null) item { Text(state.error.orEmpty(), color = Color(0xFFB33A3A), fontSize = 12.sp) }
                if (state.empleados.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("No se encontraron empleados.", color = Muted) } }
                } else {
                    items(state.empleados, key = Empleado::id) { EmployeeCard(it) }
                }
            }
        }
    }
}

@Composable
private fun EmployeeCard(employee: Empleado) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = CircleShape, color = Teal.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(employee.nombre.firstOrNull()?.uppercase() ?: "?", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(employee.nombre, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${employee.codigo} · #${employee.id}", color = Muted, fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = if (employee.activo) Color(0xFFE5F4F0) else Color(0xFFF2E9E9)) {
                    Text(
                        if (employee.activo) "Activo" else "Inactivo",
                        color = if (employee.activo) Color(0xFF267A5B) else Color(0xFF956060),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            employee.tipo?.let { Text("Tipo: $it", color = Muted, fontSize = 13.sp) }
            employee.telefono?.takeIf(String::isNotBlank)?.let { Text("Teléfono: $it", color = Muted, fontSize = 13.sp) }
            employee.fechaIngreso?.let { Text("Ingreso: ${it.split('-').reversed().joinToString("/")}", color = Muted, fontSize = 13.sp) }
        }
    }
}
