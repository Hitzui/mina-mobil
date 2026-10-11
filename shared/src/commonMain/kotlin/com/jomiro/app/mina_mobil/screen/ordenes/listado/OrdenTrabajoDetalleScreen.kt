package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.jomiro.app.mina_mobil.data.ordenes.OrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.ProcesoOrdenTrabajo
import org.koin.compose.viewmodel.koinViewModel

private val DetailInk = Color(0xFF172C3D)
private val DetailTeal = Color(0xFF087E83)
private val DetailMuted = Color(0xFF71808B)

@Composable
fun OrdenTrabajoDetalleScreen(
    ordenId: Long,
    modifier: Modifier = Modifier,
    viewModel: OrdenTrabajoDetalleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(ordenId) { viewModel.cargar(ordenId) }

    when {
        state.cargando && state.detalle == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DetailTeal)
        }
        state.error != null && state.detalle == null -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.error.orEmpty(), color = Color(0xFFB33A3A), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.cargar(ordenId, reintentar = true) },
                colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
            ) { Text("Reintentar") }
        }
        state.detalle != null -> {
            val order = state.detalle!!.orden
            val processes = state.detalle!!.procesos
            Column(
                modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("ORDEN DE TRABAJO", color = DetailMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                                Text(order.codigo, color = DetailInk, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DetailStatusBadge(order)
                        }
                        Spacer(Modifier.height(18.dp))
                        DetailField("Cliente", order.cliente ?: "Sin cliente")
                        DetailField("Fecha", formatDetailDate(order.fecha))
                        DetailField("Peso mineral", order.pesoMineral?.let { "$it ${order.unidadPeso.orEmpty()}".trim() } ?: "Sin registrar")
                        DetailField("Descripción", order.descripcion?.takeIf(String::isNotBlank) ?: "Sin descripción")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Avance de la orden", color = DetailInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (processes.isEmpty()) "No hay procesos disponibles para mostrar."
                            else "${processes.size} ${if (processes.size == 1) "proceso asociado" else "procesos asociados"}",
                            color = DetailMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                        )
                        if (processes.isEmpty()) {
                            Text("Los procesos pueden no estar registrados o tu usuario no tiene permiso para verlos.", color = DetailMuted, fontSize = 13.sp)
                        } else {
                            processes.forEachIndexed { index, process ->
                                ProcessCard(process)
                                if (index < processes.lastIndex) Spacer(Modifier.height(9.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, color = DetailMuted, fontSize = 12.sp)
        Text(value, color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProcessCard(process: ProcesoOrdenTrabajo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFF4F7F8),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(process.etapa ?: "Proceso ${process.codigo}", color = DetailInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Código ${process.codigo}", color = DetailMuted, fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = DetailTeal.copy(alpha = 0.1f)) {
                    Text(process.estado, color = DetailTeal, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            val dates = listOfNotNull(
                process.fechaInicio?.let { "Inicio: ${formatDetailDate(it)}" },
                process.fechaFin?.let { "Fin: ${formatDetailDate(it)}" },
            )
            if (dates.isNotEmpty()) Text(dates.joinToString("  ·  "), color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            if (process.pesoEntrada != null || process.pesoSalida != null) {
                Text(
                    "Entrada: ${process.pesoEntrada ?: "—"}  ·  Salida: ${process.pesoSalida ?: "—"}",
                    color = DetailMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            process.observaciones?.takeIf(String::isNotBlank)?.let {
                Text(it, color = DetailInk, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun DetailStatusBadge(order: OrdenTrabajo) {
    val (background, foreground) = when (order.codigoEstado) {
        0 -> Color(0xFFFCE8E8) to Color(0xFFB33A3A)
        1 -> Color(0xFFE8EEFF) to Color(0xFF4361EE)
        2 -> Color(0xFFFFF3DC) to Color(0xFF9A6710)
        3 -> Color(0xFFE5F4F0) to Color(0xFF267A5B)
        else -> Color(0xFFF0F2F4) to DetailMuted
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(order.estado, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
    }
}

private fun formatDetailDate(value: String?): String {
    if (value.isNullOrBlank()) return "Sin fecha"
    val date = value.substringBefore('T').split('-')
    return if (date.size == 3) "${date[2]}/${date[1]}/${date[0]}" else value
}
