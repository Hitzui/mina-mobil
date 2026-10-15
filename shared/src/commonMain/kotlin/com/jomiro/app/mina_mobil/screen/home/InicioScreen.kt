package com.jomiro.app.mina_mobil.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
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
import com.jomiro.app.mina_mobil.data.ConnectionMode
import com.jomiro.app.mina_mobil.data.OrdenInicio
import com.jomiro.app.mina_mobil.data.PanelInicio
import com.jomiro.app.mina_mobil.data.PeriodoInicio
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

private val HomeInk = Color(0xFF172C3D)
private val HomeTeal = Color(0xFF087E83)
private val HomeMuted = Color(0xFF71808B)
private val HomeLine = Color(0xFFE1E6EB)
private val HomeCanvas = Color(0xFFF4F6F8)

@Composable
fun InicioScreen(
    userId: Long,
    name: String,
    email: String,
    cashRegister: String,
    connectionMode: ConnectionMode,
    pendingOperations: Long,
    snapshotRevision: Int,
    canViewOrders: Boolean,
    onOpenOrders: () -> Unit,
    onShowOrder: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var period by remember(userId) { mutableStateOf(PeriodoInicio.MES) }
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val today = now.date
    LaunchedEffect(userId, period, connectionMode, snapshotRevision) {
        viewModel.cargar(userId, period, forzar = true)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(greeting(now.hour) + ", $name", color = HomeInk, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Resumen del taller · ${today.day}/${today.month}/${today.year}", color = HomeMuted, fontSize = 13.sp)
            }
            Surface(color = if (connectionMode == ConnectionMode.OFFLINE) Color(0xFFFFF3D1) else Color(0xFFE4F3F1), shape = RoundedCornerShape(20.dp)) {
                Text(
                    if (connectionMode == ConnectionMode.OFFLINE) "Sin conexión" else "En línea",
                    Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = if (connectionMode == ConnectionMode.OFFLINE) Color(0xFF775A00) else HomeTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, HomeLine)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Caja asignada", color = HomeMuted, fontSize = 11.sp)
                    Text(cashRegister, color = HomeInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (connectionMode == ConnectionMode.OFFLINE) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${pendingOperations} pendiente(s)", color = if (pendingOperations > 0) Color(0xFF9A5B00) else HomeTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        state.panel?.fechaDescarga?.let { Text("Descarga: ${formatTimestamp(it)}", color = HomeMuted, fontSize = 10.sp) }
                    }
                } else Text(email, color = HomeMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (connectionMode == ConnectionMode.OFFLINE) "Corte de la descarga" else "Período", color = HomeInk, fontWeight = FontWeight.SemiBold)
            if (connectionMode == ConnectionMode.ONLINE) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    PeriodoInicio.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(option.label, fontSize = 11.sp) },
                        )
                    }
                }
            } else Text("Todos los datos descargados", color = HomeMuted, fontSize = 12.sp)
        }

        state.error?.let { message ->
            Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = Color(0xFFB33A3A), fontSize = 13.sp)
                    TextButton(onClick = { viewModel.cargar(userId, period, forzar = true) }, modifier = Modifier.align(Alignment.End)) {
                        Text("Reintentar", color = HomeTeal)
                    }
                }
            }
        }
        if (state.cargando && state.panel == null) {
            CircularProgressIndicator(color = HomeTeal, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        }
        state.panel?.let { panel ->
            MetricRow(
                leftTitle = "Órdenes abiertas",
                leftValue = panel.ordenesAbiertas.toString(),
                leftCaption = "Pendientes o en proceso",
                rightTitle = "Procesos en curso",
                rightValue = panel.procesosEnCurso.toString(),
                rightCaption = "Actividad del taller",
                onLeftClick = onOpenOrders,
                onRightClick = onOpenOrders,
            )
            MetricRow(
                leftTitle = "Procesos atrasados",
                leftValue = panel.procesosAtrasados.size.toString() + if (!panel.offline) "+" else "",
                leftCaption = "Revisar fechas pendientes",
                rightTitle = if (panel.offline) "Costos conocidos" else "Costos del período",
                rightValue = "NIO ${panel.costosNio.moneyDisplay()}",
                rightCaption = if (panel.costosSinEquivalente > 0) "${panel.costosSinEquivalente} sin equivalente NIO" else "Costos identificados en córdobas",
                rightValueColor = HomeTeal,
                onLeftClick = onOpenOrders,
                onRightClick = onOpenOrders,
            )
            MetricCard(
                title = if (panel.offline) "Ingresos descargados" else "Ingresos facturados",
                value = "NIO ${panel.ingresosNio.moneyDisplay()}",
                caption = if (panel.ingresosSinEquivalente > 0) "${panel.ingresosSinEquivalente} sin equivalente NIO" else "${panel.periodo.label}",
                valueColor = HomeInk,
            )

            if (panel.costosSinEquivalente > 0 || panel.ingresosSinEquivalente > 0 || panel.ordenesConGastosGeneralesPendientes > 0) {
                AttentionPanel(panel)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (panel.procesosAtrasados.isNotEmpty()) "Requieren atención" else "Órdenes en seguimiento", color = HomeInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (canViewOrders) TextButton(onClick = onOpenOrders) { Text("Ver órdenes", color = HomeTeal, fontSize = 12.sp) }
            }
            if (panel.procesosAtrasados.isNotEmpty()) {
                panel.procesosAtrasados.forEach { process ->
                    AttentionProcessCard(process) { process.ordenId?.let(onShowOrder) ?: onOpenOrders() }
                }
            } else if (panel.ordenes.isEmpty()) {
                EmptyHomeCard(if (panel.offline) "La descarga no contiene órdenes abiertas." else "No hay órdenes abiertas para mostrar.")
            }
            panel.ordenes.forEach { order ->
                OrderHomeCard(order, enabled = canViewOrders) { onShowOrder(order.id) }
            }
            if (canViewOrders) {
                Button(
                    onClick = onOpenOrders,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HomeTeal),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Ver todas las órdenes") }
            }
        }
    }
}

@Composable
private fun MetricRow(
    leftTitle: String,
    leftValue: String,
    leftCaption: String,
    rightTitle: String,
    rightValue: String,
    rightCaption: String,
    rightValueColor: Color = HomeInk,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(leftTitle, leftValue, leftCaption, Modifier.weight(1f), onClick = onLeftClick)
        MetricCard(rightTitle, rightValue, rightCaption, Modifier.weight(1f), valueColor = rightValueColor, onClick = onRightClick)
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HomeInk,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = HomeMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, color = HomeMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AttentionPanel(panel: PanelInicio) {
    Surface(color = Color(0xFFFFF4D6), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Datos incompletos", color = Color(0xFF795900), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (panel.costosSinEquivalente > 0) Text("${panel.costosSinEquivalente} costo(s) no tienen equivalente en córdobas.", color = Color(0xFF795900), fontSize = 12.sp)
            if (panel.ingresosSinEquivalente > 0) Text("${panel.ingresosSinEquivalente} ingreso(s) no tienen equivalente en córdobas.", color = Color(0xFF795900), fontSize = 12.sp)
            if (panel.ordenesConGastosGeneralesPendientes > 0) Text(
                "${panel.ordenesConGastosGeneralesPendientes} orden(es) tienen gastos generales pendientes de distribuir (NIO ${panel.gastosGeneralesPendientesNio.moneyDisplay()}).",
                color = Color(0xFF795900), fontSize = 12.sp,
            )
            Text("Los importes mostrados son conocidos; no incluyen movimientos que no se pudieron convertir.", color = Color(0xFF795900), fontSize = 11.sp)
        }
    }
}

@Composable
private fun AttentionProcessCard(process: com.jomiro.app.mina_mobil.data.ProcesoAtrasadoInicio, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(13.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("!", color = Color(0xFFB33A3A), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text("${process.orden} · ${process.codigo}", color = HomeInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(process.etapa, color = HomeMuted, fontSize = 12.sp)
            }
            process.dias?.let { Text("$it d", color = Color(0xFFB33A3A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun OrderHomeCard(order: OrdenInicio, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(13.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(order.codigo, color = HomeInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(order.estado, color = HomeTeal, fontSize = 11.sp)
            }
            Text(order.cliente, color = HomeMuted, fontSize = 12.sp)
            val progress = if (order.procesosTotal > 0) "${order.procesosTerminados} de ${order.procesosTotal} procesos finalizados" else "Sin procesos"
            Text(progress, color = HomeMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyHomeCard(message: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(13.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = HomeMuted, fontSize = 13.sp)
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Buenos días"
    in 12..17 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun formatTimestamp(value: String): String = value.replace('T', ' ').take(16)

private fun Double.moneyDisplay(): String = kotlin.math.round(this * 100.0).toLong().let { (it / 100.0).toString() }
