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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextField
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
import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.CostoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.DetalleOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.EtapaOpcionProceso
import com.jomiro.app.mina_mobil.data.ordenes.IngresoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.LiquidacionOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.ProcesoOrdenTrabajo
import com.composables.ui.components.BottomSheet
import com.composables.ui.components.BottomSheetDetent
import com.composables.ui.components.AlertDialog as ComposablesAlertDialog
import com.composables.ui.components.rememberBottomSheetState
import com.composables.ui.theme.ComposablesTheme
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private val DetailInk = Color(0xFF172C3D)
private val DetailTeal = Color(0xFF087E83)
private val DetailMuted = Color(0xFF71808B)

enum class OrdenDetalleSeccion(val titulo: String, val icono: String) {
    COSTOS("Costos de la orden", "¤"),
    INGRESOS("Ingresos de la orden", "↗"),
    ACUERDOS("Acuerdos de participación", "▤"),
    LIQUIDACIONES("Liquidaciones de oro", "◈"),
}

@Composable
fun OrdenTrabajoDetalleScreen(
    ordenId: Long,
    canCreateProcess: Boolean = false,
    seccion: OrdenDetalleSeccion? = null,
    onSelectSection: (OrdenDetalleSeccion) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OrdenTrabajoDetalleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val costsSheetState = rememberBottomSheetState()
    var mostrarConfirmacionFinalizar by remember(ordenId) { mutableStateOf(false) }
    var mostrarFormularioProceso by remember(ordenId) { mutableStateOf(false) }
    LaunchedEffect(ordenId) { viewModel.cargar(ordenId) }
    LaunchedEffect(state.procesoAgregado) {
        if (state.procesoAgregado) mostrarFormularioProceso = false
    }

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
            if (seccion == null) {
                OrderOverview(
                    modifier = modifier,
                    detalle = detail,
                    puedeAgregarProceso = canCreateProcess && order.codigoEstado != 0 && order.codigoEstado != 3,
                    onOpenSections = { costsSheetState.targetDetent = BottomSheetDetent.FullyExpanded },
                    onAddProcess = {
                        viewModel.cargarEtapasProceso(ordenId)
                        mostrarFormularioProceso = true
                    },
                )
                ComposablesTheme {
                    BottomSheet(
                        state = costsSheetState,
                        onDismissRequest = { costsSheetState.targetDetent = BottomSheetDetent.Hidden },
                        backgroundColor = Color.White,
                        contentColor = DetailInk,
                        toolbar = {
                            Column(Modifier.fillMaxWidth()) {
                                Text("Más información de la orden", color = DetailInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(order.codigo, color = DetailMuted, fontSize = 13.sp)
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            OrdenDetalleSeccion.entries.forEach { item ->
                                val count = when (item) {
                                    OrdenDetalleSeccion.COSTOS -> detail.costos.count { it.proceso.isNullOrBlank() }
                                    OrdenDetalleSeccion.INGRESOS -> detail.ingresos.size
                                    OrdenDetalleSeccion.ACUERDOS -> detail.acuerdos.size
                                    OrdenDetalleSeccion.LIQUIDACIONES -> detail.liquidaciones.size
                                }
                                BottomSheetMenuButton(item, count) {
                                    costsSheetState.targetDetent = BottomSheetDetent.Hidden
                                    onSelectSection(item)
                                }
                            }
                            if (order.codigoEstado != 0 && order.codigoEstado != 3) {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { mostrarConfirmacionFinalizar = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.finalizandoOrden,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFB33A3A),
                                        disabledContainerColor = Color(0xFFB99A9A),
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    if (state.finalizandoOrden) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                        )
                                        Text("  Finalizando…")
                                    } else {
                                        Text("Dar por finalizada la orden")
                                    }
                                }
                            }
                        }
                    }
                }
                if (mostrarConfirmacionFinalizar) {
                    ComposablesTheme {
                        ComposablesAlertDialog(
                            visible = true,
                            onDismissRequest = { mostrarConfirmacionFinalizar = false },
                            paneTitle = "Finalizar orden",
                        ) {
                            Column {
                                Text("¿Finalizar la orden?", color = DetailInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Esta acción cerrará la orden y no permitirá registrar nuevos datos. Solo se puede finalizar cuando todos sus procesos estén finalizados.",
                                    color = DetailMuted,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    androidx.compose.material3.TextButton(onClick = { mostrarConfirmacionFinalizar = false }) {
                                        Text("Cancelar", color = DetailMuted)
                                    }
                                    Button(
                                        onClick = {
                                            mostrarConfirmacionFinalizar = false
                                            viewModel.finalizarOrden(ordenId)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB33A3A)),
                                        shape = RoundedCornerShape(10.dp),
                                    ) { Text("Confirmar") }
                                }
                            }
                        }
                    }
                }
                val mensajeFinalizacion = state.errorFinalizacion
                    ?: if (state.finalizacionExitosa) "La orden de trabajo se finalizó correctamente." else null
                if (mensajeFinalizacion != null) {
                    ComposablesTheme {
                        ComposablesAlertDialog(
                            visible = true,
                            onDismissRequest = { viewModel.limpiarMensajeFinalizacion() },
                            paneTitle = if (state.finalizacionExitosa) "Orden finalizada" else "No se pudo finalizar",
                        ) {
                            Column {
                                Text(
                                    if (state.finalizacionExitosa) "Orden finalizada" else "No se pudo finalizar la orden",
                                    color = if (state.finalizacionExitosa) DetailTeal else Color(0xFFB33A3A),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(mensajeFinalizacion, color = DetailInk, fontSize = 14.sp)
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(
                                        onClick = { viewModel.limpiarMensajeFinalizacion() },
                                        colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                                        shape = RoundedCornerShape(10.dp),
                                    ) { Text("Entendido") }
                                }
                            }
                        }
                    }
                }
                if (mostrarFormularioProceso && !state.procesoAgregado) {
                    CrearProcesoDialog(
                        etapas = state.etapasProceso,
                        cargandoEtapas = state.cargandoEtapasProceso,
                        creando = state.creandoProceso,
                        error = state.errorProceso,
                        onRetry = { viewModel.cargarEtapasProceso(ordenId) },
                        onDismiss = { mostrarFormularioProceso = false },
                        onCreate = { etapaId, pesoEntrada, pesoSalida, observaciones ->
                            viewModel.agregarProceso(
                                ordenId = ordenId,
                                etapaId = etapaId,
                                fechaInicio = null,
                                fechaFin = null,
                                pesoEntrada = pesoEntrada,
                                pesoSalida = pesoSalida,
                                observaciones = observaciones,
                            )
                        },
                    )
                }
                if (state.procesoAgregado) {
                    ComposablesTheme {
                        ComposablesAlertDialog(
                            visible = true,
                            onDismissRequest = { viewModel.limpiarMensajeProceso() },
                            paneTitle = "Proceso agregado",
                        ) {
                            Column {
                                Text("Proceso agregado", color = DetailTeal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    state.errorProceso ?: "El proceso ya aparece en la orden.",
                                    color = DetailInk,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(
                                        onClick = { viewModel.limpiarMensajeProceso() },
                                        colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                                        shape = RoundedCornerShape(10.dp),
                                    ) { Text("Entendido") }
                                }
                            }
                        }
                    }
                }
            } else {
                OrderSectionContent(modifier, detail, seccion)
            }
        }
    }
}

@Composable
private fun OrderOverview(
    modifier: Modifier,
    detalle: DetalleOrdenTrabajo,
    puedeAgregarProceso: Boolean,
    onOpenSections: () -> Unit,
    onAddProcess: () -> Unit,
) {
    val order = detalle.orden
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
                    onClick = onOpenSections,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Ver costos, ingresos, acuerdos y liquidaciones") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Procesos", color = DetailInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (puedeAgregarProceso) {
                        TextButton(onClick = onAddProcess) { Text("+ Agregar", color = DetailTeal, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Text(
                    if (detalle.procesos.isEmpty()) "No hay procesos disponibles para mostrar."
                    else "${detalle.procesos.size} ${if (detalle.procesos.size == 1) "proceso asociado" else "procesos asociados"}",
                    color = DetailMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                )
                if (detalle.procesos.isEmpty()) {
                    Text("Los procesos pueden no estar registrados o tu usuario no tiene permiso para verlos.", color = DetailMuted, fontSize = 13.sp)
                } else {
                    detalle.procesos.forEachIndexed { index, process ->
                        ProcessCard(process)
                        if (index < detalle.procesos.lastIndex) Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CrearProcesoDialog(
    etapas: List<EtapaOpcionProceso>,
    cargandoEtapas: Boolean,
    creando: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Long, Double?, Double?, String) -> Unit,
) {
    var etapaId by remember { mutableStateOf<Long?>(null) }
    var pesoEntrada by remember { mutableStateOf("") }
    var pesoSalida by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    var abrirSelectorEtapa by remember { mutableStateOf(false) }
    LaunchedEffect(etapas) {
        if (etapaId == null) etapaId = etapas.firstOrNull()?.id
    }
    val entrada = pesoEntrada.trim().replace(',', '.').toDoubleOrNull()
    val salida = pesoSalida.trim().replace(',', '.').toDoubleOrNull()
    val pesoValido = (pesoEntrada.isBlank() || entrada != null && entrada >= 0) &&
        (pesoSalida.isBlank() || salida != null && salida >= 0)
    ComposablesTheme {
        ComposablesAlertDialog(
            visible = true,
            onDismissRequest = { if (!creando) onDismiss() },
            paneTitle = "Agregar proceso",
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Agregar proceso", color = DetailInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("El código se asignará automáticamente y el proceso comenzará como Pendiente.", color = DetailMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { abrirSelectorEtapa = true },
                    enabled = etapas.isNotEmpty() && !cargandoEtapas && !creando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = DetailInk),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(etapas.firstOrNull { it.id == etapaId }?.let { "${it.orden}. ${it.nombre}" } ?: "Seleccionar etapa", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cargandoEtapas) {
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(18.dp), color = DetailTeal, strokeWidth = 2.dp)
                        Text("  Cargando etapas…", color = DetailMuted, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pesoEntrada,
                        onValueChange = { pesoEntrada = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Peso entrada") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = pesoSalida,
                        onValueChange = { pesoSalida = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Peso salida") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    value = observaciones,
                    onValueChange = { observaciones = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Observaciones") },
                    minLines = 2,
                    maxLines = 4,
                )
                error?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (etapas.isEmpty() && !cargandoEtapas) {
                    Text("No hay etapas activas disponibles.", color = DetailMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Reintentar", color = DetailTeal) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !creando) { Text("Cancelar", color = DetailMuted) }
                    Button(
                        onClick = {
                            val selected = etapaId ?: return@Button
                            onCreate(selected, entrada, salida, observaciones)
                        },
                        enabled = etapaId != null && etapas.isNotEmpty() && pesoValido && !creando && !cargandoEtapas,
                        colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (creando) CircularProgressIndicator(Modifier.height(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Agregar")
                    }
                }
            }
        }
    }
    if (abrirSelectorEtapa) {
        ComposablesTheme {
            ComposablesAlertDialog(
                visible = true,
                onDismissRequest = { abrirSelectorEtapa = false },
                paneTitle = "Seleccionar etapa",
            ) {
                Column {
                    Text("Seleccionar etapa", color = DetailInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.height(280.dp).padding(top = 8.dp)) {
                        items(etapas, key = EtapaOpcionProceso::id) { stage ->
                            androidx.compose.material3.TextButton(
                                onClick = { etapaId = stage.id; abrirSelectorEtapa = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${stage.orden}. ${stage.nombre}", color = DetailInk, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { abrirSelectorEtapa = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cerrar", color = DetailTeal)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetMenuButton(section: OrdenDetalleSeccion, count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF4F7F8),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = DetailTeal.copy(alpha = 0.1f)) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(section.icono, color = DetailTeal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(section.titulo, color = DetailInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$count ${if (count == 1) "registro" else "registros"}", color = DetailMuted, fontSize = 12.sp)
            }
            Text("›", color = DetailTeal, fontSize = 25.sp)
        }
    }
}

@Composable
private fun OrderSectionContent(
    modifier: Modifier,
    detail: DetalleOrdenTrabajo,
    section: OrdenDetalleSeccion,
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(detail.orden.codigo, color = DetailMuted, fontSize = 13.sp)
        when (section) {
            OrdenDetalleSeccion.COSTOS -> {
                val costs = detail.costos.filter { it.proceso.isNullOrBlank() }
                if (costs.isEmpty()) EmptyMoneyEntries("No hay costos generales registrados para esta orden.")
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(costs, key = CostoOrdenTrabajo::id) { CostEntryCard(it) }
                }
            }
            OrdenDetalleSeccion.INGRESOS -> {
                if (detail.ingresos.isEmpty()) EmptyMoneyEntries("No hay ingresos registrados para esta orden.")
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(detail.ingresos, key = IngresoOrdenTrabajo::id) { IncomeEntryCard(it) }
                }
            }
            OrdenDetalleSeccion.ACUERDOS -> {
                if (detail.acuerdos.isEmpty()) EmptyMoneyEntries("No hay acuerdos de participación para esta orden.")
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(detail.acuerdos, key = AcuerdoOrdenTrabajo::id) { AgreementEntryCard(it) }
                }
            }
            OrdenDetalleSeccion.LIQUIDACIONES -> {
                if (detail.liquidaciones.isEmpty()) EmptyMoneyEntries("No hay liquidaciones registradas para esta orden.")
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(detail.liquidaciones, key = LiquidacionOrdenTrabajo::id) { LiquidationEntryCard(it) }
                }
            }
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
private fun AgreementEntryCard(entry: AcuerdoOrdenTrabajo) {
    MoneyEntrySurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(entry.tipoParticipacion ?: "Acuerdo", color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(if (entry.modalidad == "tarifa") "Tarifa de servicio" else "Participación", color = DetailMuted, fontSize = 12.sp)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (entry.activo) Color(0xFFE5F4F0) else Color(0xFFF0F2F4),
            ) {
                Text(
                    if (entry.activo) "Activo" else "Inactivo",
                    color = if (entry.activo) Color(0xFF267A5B) else DetailMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (entry.modalidad == "tarifa") {
            Text(
                "Tarifa: ${listOfNotNull(entry.moneda, entry.tarifaServicio).joinToString(" ").ifBlank { "Sin monto" }}",
                color = DetailInk,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                "Cliente: ${entry.porcentajeCliente ?: "—"}%  ·  Empresa: ${entry.porcentajeEmpresa ?: "—"}%",
                color = DetailInk,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        entry.observaciones?.takeIf(String::isNotBlank)?.let {
            Text(it, color = DetailMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun LiquidationEntryCard(entry: LiquidacionOrdenTrabajo) {
    MoneyEntrySurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Liquidación de oro", color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(formatDetailDate(entry.fecha), color = DetailMuted, fontSize = 12.sp)
            }
            Text("${entry.gramosRecuperados ?: "—"} g", color = DetailTeal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Cliente: ${entry.gramosCliente ?: "—"} g (${entry.porcentajeCliente ?: "—"}%)  ·  " +
                "Empresa: ${entry.gramosEmpresa ?: "—"} g (${entry.porcentajeEmpresa ?: "—"}%)",
            color = DetailInk,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        entry.observaciones?.takeIf(String::isNotBlank)?.let {
            Text(it, color = DetailMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
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
