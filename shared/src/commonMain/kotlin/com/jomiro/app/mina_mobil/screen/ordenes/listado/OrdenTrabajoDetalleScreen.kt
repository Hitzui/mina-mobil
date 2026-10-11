package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.OrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.ProcesoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.CostoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.IngresoOrdenTrabajo
import com.composables.ui.components.BottomSheet
import com.composables.ui.components.BottomSheetDetent
import com.composables.ui.components.rememberBottomSheetState
import com.composables.ui.theme.ComposablesTheme
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
    val costsSheetState = rememberBottomSheetState()
    var showingIncomes by remember { mutableStateOf(false) }
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
            val detail = state.detalle!!
            val order = detail.orden
            val processes = detail.procesos
            val orderLevelCosts = detail.costos.filter { it.proceso.isNullOrBlank() }
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
                        Button(
                            onClick = {
                                showingIncomes = false
                                costsSheetState.targetDetent = BottomSheetDetent.FullyExpanded
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("▤  Ver costos e ingresos de la orden")
                        }
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
            ComposablesTheme {
                BottomSheet(
                    state = costsSheetState,
                    onDismissRequest = { costsSheetState.targetDetent = BottomSheetDetent.Hidden },
                    backgroundColor = Color.White,
                    contentColor = DetailInk,
                    toolbar = {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Movimientos de la orden", color = DetailInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text(order.codigo, color = DetailMuted, fontSize = 13.sp)
                                }
                                TextButton(onClick = { costsSheetState.targetDetent = BottomSheetDetent.Hidden }) {
                                    Text("Cerrar", color = DetailTeal)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CostSheetTab(Modifier.weight(1f), "Costos (${orderLevelCosts.size})", selected = !showingIncomes) { showingIncomes = false }
                                CostSheetTab(Modifier.weight(1f), "Ingresos (${detail.ingresos.size})", selected = showingIncomes) { showingIncomes = true }
                            }
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                ) {
                    if (showingIncomes) {
                        if (detail.ingresos.isEmpty()) {
                            EmptyMoneyEntries("No hay ingresos registrados para esta orden.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                items(detail.ingresos, key = IngresoOrdenTrabajo::id) { entry -> IncomeEntryCard(entry) }
                            }
                        }
                    } else {
                        if (orderLevelCosts.isEmpty()) {
                            EmptyMoneyEntries("No hay costos propios registrados para esta orden.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                items(orderLevelCosts, key = CostoOrdenTrabajo::id) { entry -> CostEntryCard(entry) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostSheetTab(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) DetailTeal else Color(0xFFF1F5F6),
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else DetailMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyMoneyEntries(message: String) {
    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        Text(message, color = DetailMuted, fontSize = 14.sp)
    }
}

@Composable
private fun CostEntryCard(entry: CostoOrdenTrabajo) {
    MoneyEntrySurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(entry.categoria ?: "Costo", color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(formatDetailDate(entry.fecha), color = DetailMuted, fontSize = 12.sp)
            }
            MoneyAmounts(entry.total, entry.moneda, entry.totalNio)
        }
        entry.descripcion?.takeIf(String::isNotBlank)?.let {
            Text(it, color = DetailMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        entry.cantidad?.let { Text("Cantidad: $it", color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) }
    }
}

@Composable
private fun IncomeEntryCard(entry: IngresoOrdenTrabajo) {
    MoneyEntrySurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(entry.tipo ?: "Ingreso", color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(formatDetailDate(entry.fecha), color = DetailMuted, fontSize = 12.sp)
            }
            MoneyAmounts(entry.total, entry.moneda, entry.totalNio)
        }
        entry.descripcion?.takeIf(String::isNotBlank)?.let {
            Text(it, color = DetailMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun MoneyEntrySurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F7F8)) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun MoneyAmounts(total: String?, currency: String?, totalNio: String?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            listOfNotNull(currency, total).joinToString(" ").ifBlank { "Sin monto" },
            color = DetailInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        totalNio?.let { Text("C$ $it", color = DetailTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
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
