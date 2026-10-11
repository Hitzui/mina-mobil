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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.jomiro.app.mina_mobil.data.ordenes.TrabajoEmpleadoOrden
import com.jomiro.app.mina_mobil.data.ordenes.OpcionTrabajoEmpleado
import com.jomiro.app.mina_mobil.data.ordenes.OpcionUnidadTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.TarifaTrabajoEmpleado
import com.jomiro.app.mina_mobil.data.ordenes.EquipoProcesoOrden
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesEquipoProceso
import com.jomiro.app.mina_mobil.data.ordenes.DepreciacionEquipoProceso
import com.jomiro.app.mina_mobil.data.ordenes.EquipoOpcionProceso
import com.composables.ui.components.BottomSheet
import com.composables.ui.components.BottomSheetDetent
import com.composables.ui.components.AlertDialog as ComposablesAlertDialog
import com.composables.ui.components.rememberBottomSheetState
import com.composables.ui.theme.ComposablesTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    canViewEmployeeWork: Boolean = false,
    canCreateEmployeeWork: Boolean = false,
    canViewProcessEquipment: Boolean = false,
    canCreateProcessEquipment: Boolean = false,
    equipmentProcessId: Long? = null,
    onAssignEquipment: (Long) -> Unit = {},
    onBackFromEquipment: () -> Unit = {},
    seccion: OrdenDetalleSeccion? = null,
    onSelectSection: (OrdenDetalleSeccion) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OrdenTrabajoDetalleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val costsSheetState = rememberBottomSheetState()
    var mostrarConfirmacionFinalizar by remember(ordenId) { mutableStateOf(false) }
    var mostrarFormularioProceso by remember(ordenId) { mutableStateOf(false) }
    var procesoParaTrabajo by remember(ordenId) { mutableStateOf<ProcesoOrdenTrabajo?>(null) }
    LaunchedEffect(ordenId) { viewModel.cargar(ordenId) }
    LaunchedEffect(state.procesoAgregado) {
        if (state.procesoAgregado) mostrarFormularioProceso = false
    }
    LaunchedEffect(state.trabajoAgregado) {
        if (state.trabajoAgregado) procesoParaTrabajo = null
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
            if (equipmentProcessId != null) {
                val process = detail.procesos.firstOrNull { it.id == equipmentProcessId }
                if (process == null) {
                    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("No se encontró el proceso seleccionado.", color = DetailMuted)
                        TextButton(onClick = onBackFromEquipment) { Text("Volver", color = DetailTeal) }
                    }
                } else {
                    AsignarEquipoScreen(
                        ordenId = ordenId,
                        process = process,
                        options = state.opcionesEquipo,
                        loadingOptions = state.cargandoOpcionesEquipo,
                        estimate = state.depreciacionEquipo,
                        estimating = state.calculandoDepreciacionEquipo,
                        saving = state.asignandoEquipo,
                        assigned = state.equipoAsignado,
                        error = state.errorEquipo,
                        existingEquipment = detail.equipos.filter { it.codigoProceso == process.codigo },
                        onLoadOptions = { viewModel.cargarOpcionesEquipo(ordenId, process.id) },
                        onEstimate = { equipmentId, start, end ->
                            viewModel.consultarDepreciacionEquipo(ordenId, process.id, equipmentId, start, end)
                        },
                        onAssign = { equipmentId, start, end, observations ->
                            viewModel.asignarEquipo(ordenId, process.id, equipmentId, start, end, observations)
                        },
                        onBack = {
                            viewModel.limpiarMensajeEquipo()
                            onBackFromEquipment()
                        },
                        modifier = modifier,
                    )
                }
            } else if (seccion == null) {
                OrderOverview(
                    modifier = modifier,
                    detalle = detail,
                    puedeAgregarProceso = canCreateProcess && order.codigoEstado != 0 && order.codigoEstado != 3,
                    puedeVerTrabajos = canViewEmployeeWork,
                    puedeAgregarTrabajos = canCreateEmployeeWork && order.codigoEstado != 0 && order.codigoEstado != 3,
                    puedeVerEquipos = canViewProcessEquipment,
                    puedeAgregarEquipos = canCreateProcessEquipment && order.codigoEstado != 0 && order.codigoEstado != 3,
                    onOpenSections = { costsSheetState.targetDetent = BottomSheetDetent.FullyExpanded },
                    onAddProcess = {
                        viewModel.cargarEtapasProceso(ordenId)
                            mostrarFormularioProceso = true
                        },
                    onAddWork = { process ->
                        viewModel.cargarOpcionesTrabajo(ordenId, process.id)
                        procesoParaTrabajo = process
                    },
                    onAddEquipment = { process ->
                        viewModel.cargarOpcionesEquipo(ordenId, process.id)
                        onAssignEquipment(process.id)
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
                procesoParaTrabajo?.let { process ->
                    if (!state.trabajoAgregado) {
                        CrearTrabajoEmpleadoDialog(
                            process = process,
                            opciones = state.opcionesTrabajo,
                            cargandoOpciones = state.cargandoOpcionesTrabajo,
                            tarifa = state.tarifaTrabajo,
                            cargandoTarifa = state.cargandoTarifaTrabajo,
                            creando = state.creandoTrabajo,
                            error = state.errorTrabajo,
                            onRetryOptions = { viewModel.cargarOpcionesTrabajo(ordenId, process.id) },
                            onQuote = { employeeId, paymentTypeId, date ->
                                viewModel.consultarTarifaTrabajo(ordenId, process.id, employeeId, paymentTypeId, date)
                            },
                            onDismiss = { if (!state.creandoTrabajo) procesoParaTrabajo = null },
                            onCreate = { employeeId, paymentTypeId, date, start, end, quantity, unitId, description, observations ->
                                viewModel.agregarTrabajo(
                                    ordenId, process.id, employeeId, paymentTypeId, date, start, end,
                                    quantity, unitId, description, observations,
                                )
                            },
                        )
                    }
                }
                if (state.trabajoAgregado) {
                    ComposablesTheme {
                        ComposablesAlertDialog(
                            visible = true,
                            onDismissRequest = { viewModel.limpiarMensajeTrabajo() },
                            paneTitle = "Trabajo registrado",
                        ) {
                            Column {
                                Text("Trabajo registrado", color = DetailTeal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(10.dp))
                                Text(state.errorTrabajo ?: "El trabajo del empleado se agregó al proceso.", color = DetailInk, fontSize = 14.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(
                                        onClick = { viewModel.limpiarMensajeTrabajo() },
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
    puedeVerTrabajos: Boolean,
    puedeAgregarTrabajos: Boolean,
    puedeVerEquipos: Boolean,
    puedeAgregarEquipos: Boolean,
    onOpenSections: () -> Unit,
    onAddProcess: () -> Unit,
    onAddWork: (ProcesoOrdenTrabajo) -> Unit,
    onAddEquipment: (ProcesoOrdenTrabajo) -> Unit,
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
                        val openProcess = process.estado != "Finalizado" && process.estado != "Cancelado"
                        ProcessCard(
                            process = process,
                            trabajos = detalle.trabajosEmpleados.filter { it.codigoProceso == process.codigo },
                            mostrarTrabajos = puedeVerTrabajos,
                            puedeAgregarTrabajo = puedeAgregarTrabajos && openProcess,
                            onAddWork = { onAddWork(process) },
                            equipos = detalle.equipos.filter { it.codigoProceso == process.codigo },
                            mostrarEquipos = puedeVerEquipos,
                            puedeAgregarEquipo = puedeAgregarEquipos && openProcess,
                            onAddEquipment = { onAddEquipment(process) },
                        )
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
private fun ProcessCard(
    process: ProcesoOrdenTrabajo,
    trabajos: List<TrabajoEmpleadoOrden>,
    mostrarTrabajos: Boolean,
    puedeAgregarTrabajo: Boolean,
    onAddWork: () -> Unit,
    equipos: List<EquipoProcesoOrden>,
    mostrarEquipos: Boolean,
    puedeAgregarEquipo: Boolean,
    onAddEquipment: () -> Unit,
) {
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
            if (mostrarTrabajos && trabajos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Trabajos de empleados", color = DetailInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                trabajos.forEach { work ->
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("${work.empleado ?: "Empleado"} · ${work.tipoPago ?: "Trabajo"}", color = DetailInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(work.fecha?.let(::formatDetailDate), listOfNotNull(work.cantidad, work.unidad).joinToString(" ").takeIf(String::isNotBlank))
                                .joinToString(" · "),
                            color = DetailMuted,
                            fontSize = 11.sp,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(listOfNotNull(work.moneda, work.total).joinToString(" ").ifBlank { "—" }, color = DetailInk, fontSize = 11.sp)
                            work.totalNio?.let { Text("C$ $it", color = DetailTeal, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
            if (puedeAgregarTrabajo) {
                androidx.compose.material3.TextButton(
                    onClick = onAddWork,
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                ) { Text("+ Agregar trabajo", color = DetailTeal, fontWeight = FontWeight.SemiBold) }
            }
            if (mostrarEquipos && equipos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Equipos del proceso", color = DetailInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                equipos.forEach { assignment ->
                    Column(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                        Text(
                            "${assignment.codigoEquipo.orEmpty()} · ${assignment.nombreEquipo.orEmpty()}",
                            color = DetailInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Inicio: ${assignment.inicio?.let(::formatDetailDateTime) ?: "—"} · Fin: ${assignment.fin?.let(::formatDetailDateTime) ?: "Sigue asignado"}",
                            color = DetailMuted,
                            fontSize = 11.sp,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${assignment.dias.displayDecimal()} días · ${assignment.depreciacionDiaria.displayDecimal()}/día", color = DetailMuted, fontSize = 11.sp)
                            Text("Deprec. ${assignment.depreciacionTotal.displayDecimal()}", color = DetailTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        assignment.observaciones?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = DetailMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (puedeAgregarEquipo) {
                androidx.compose.material3.TextButton(
                    onClick = onAddEquipment,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                ) { Text("+ Asignar equipo", color = DetailTeal, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun CrearTrabajoEmpleadoDialog(
    process: ProcesoOrdenTrabajo,
    opciones: com.jomiro.app.mina_mobil.data.ordenes.OpcionesTrabajoEmpleado?,
    cargandoOpciones: Boolean,
    tarifa: TarifaTrabajoEmpleado?,
    cargandoTarifa: Boolean,
    creando: Boolean,
    error: String?,
    onRetryOptions: () -> Unit,
    onQuote: (Long?, Long?, String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Long, Long, String, String?, String?, Double, Long, String, String) -> Unit,
) {
    val today = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
    var employeeId by remember(process.id) { mutableStateOf<Long?>(null) }
    var paymentTypeId by remember(process.id) { mutableStateOf<Long?>(null) }
    var unitId by remember(process.id) { mutableStateOf<Long?>(null) }
    var date by remember(process.id) { mutableStateOf(today) }
    var startTime by remember(process.id) { mutableStateOf("") }
    var endTime by remember(process.id) { mutableStateOf("") }
    var quantity by remember(process.id) { mutableStateOf("1") }
    var description by remember(process.id) { mutableStateOf("") }
    var observations by remember(process.id) { mutableStateOf("") }
    var openSelector by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember(process.id) { mutableStateOf(false) }
    var showTimePicker by remember(process.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(opciones) {
        if (employeeId == null) employeeId = opciones?.empleados?.firstOrNull()?.id
        if (paymentTypeId == null) paymentTypeId = opciones?.tiposPago?.firstOrNull()?.id
        if (unitId == null) unitId = opciones?.unidades?.firstOrNull()?.id
    }
    LaunchedEffect(employeeId, paymentTypeId, date) { onQuote(employeeId, paymentTypeId, date) }
    val quantityValue = quantity.trim().replace(',', '.').toDoubleOrNull()
    val timeValid = (startTime.isBlank() || startTime.matches(Regex("\\d{2}:\\d{2}"))) &&
        (endTime.isBlank() || endTime.matches(Regex("\\d{2}:\\d{2}"))) &&
        (startTime.isBlank() || endTime.isBlank() || endTime >= startTime)
    val selectedEmployee = opciones?.empleados?.firstOrNull { it.id == employeeId }
    val selectedType = opciones?.tiposPago?.firstOrNull { it.id == paymentTypeId }
    val selectedUnit = opciones?.unidades?.firstOrNull { it.id == unitId }
    val canSubmit = selectedEmployee != null && selectedType != null && selectedUnit != null &&
        tarifa != null && quantityValue != null && quantityValue >= 0 && timeValid && date.isNotBlank() &&
        !cargandoOpciones && !cargandoTarifa && !creando
    val total = tarifa?.let { if (it.metodoCalculo == "TARIFA") it.tarifa else it.tarifa * (quantityValue ?: 0.0) }
    val totalNio = tarifa?.let { if (it.metodoCalculo == "TARIFA") it.tarifaNio else it.tarifaNio * (quantityValue ?: 0.0) }

    ComposablesTheme {
        ComposablesAlertDialog(
            visible = true,
            onDismissRequest = { if (!creando) onDismiss() },
            paneTitle = "Nuevo trabajo de empleado",
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Nuevo trabajo", color = DetailInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Proceso ${process.codigo} · ${process.etapa.orEmpty()}", color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
                EmployeeWorkPickerButton("Empleado", selectedEmployee?.let { "${it.codigo.orEmpty()} · ${it.nombre}" }, !cargandoOpciones) {
                    openSelector = "employee"
                }
                EmployeeWorkPickerButton("Tipo de pago", selectedType?.nombre, !cargandoOpciones) {
                    openSelector = "payment"
                }
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = DetailInk),
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Fecha", color = DetailMuted, fontSize = 10.sp)
                            Text(date.toDisplayDate(), color = DetailInk, fontSize = 13.sp)
                        }
                        Text("▦", color = DetailTeal, fontSize = 18.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmployeeTimePickerButton(
                        label = "Hora inicio",
                        value = startTime,
                        modifier = Modifier.weight(1f),
                    ) { showTimePicker = "start" }
                    EmployeeTimePickerButton(
                        label = "Hora fin",
                        value = endTime,
                        modifier = Modifier.weight(1f),
                    ) { showTimePicker = "end" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Cantidad") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    EmployeeWorkPickerButton("Unidad", selectedUnit?.nombre, !cargandoOpciones, Modifier.weight(1f)) {
                        openSelector = "unit"
                    }
                }
                if (cargandoOpciones) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        CircularProgressIndicator(Modifier.height(18.dp), color = DetailTeal, strokeWidth = 2.dp)
                        Text("  Cargando datos del formulario…", color = DetailMuted, fontSize = 12.sp)
                    }
                }
                if (cargandoTarifa) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        CircularProgressIndicator(Modifier.height(18.dp), color = DetailTeal, strokeWidth = 2.dp)
                        Text("  Consultando tarifa vigente…", color = DetailMuted, fontSize = 12.sp)
                    }
                }
                tarifa?.let { rate ->
                    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFF1F5F6)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Tarifa: ${rate.tarifa} ${rate.moneda}", color = DetailInk, fontSize = 13.sp)
                            Text("Tarifa NIO: C$ ${rate.tarifaNio}", color = DetailMuted, fontSize = 12.sp)
                            Text("Total estimado: ${total?.let(::moneyValue) ?: "—"} ${rate.moneda} · C$ ${totalNio?.let(::moneyValue) ?: "—"}", color = DetailTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedTextField(value = description, onValueChange = { if (it.length <= 255) description = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Descripción") }, singleLine = true)
                OutlinedTextField(value = observations, onValueChange = { observations = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Observaciones") }, minLines = 2, maxLines = 4)
                error?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (opciones != null && (opciones.empleados.isEmpty() || opciones.tiposPago.isEmpty() || opciones.unidades.isEmpty())) {
                    Text("No hay empleados, tipos de pago o unidades activas disponibles.", color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (opciones == null && !cargandoOpciones) {
                    TextButton(onClick = onRetryOptions, modifier = Modifier.align(Alignment.End)) { Text("Reintentar", color = DetailTeal) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !creando) { Text("Cancelar", color = DetailMuted) }
                    Button(
                        onClick = {
                            onCreate(
                                employeeId ?: return@Button,
                                paymentTypeId ?: return@Button,
                                date,
                                startTime.takeIf(String::isNotBlank),
                                endTime.takeIf(String::isNotBlank),
                                quantityValue ?: return@Button,
                                unitId ?: return@Button,
                                description,
                                observations,
                            )
                        },
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (creando) CircularProgressIndicator(Modifier.height(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Guardar")
                    }
                }
            }
        }
    }

    val choices = when (openSelector) {
        "employee" -> opciones?.empleados?.map { it.id to "${it.codigo.orEmpty()} · ${it.nombre}" }.orEmpty()
        "payment" -> opciones?.tiposPago?.map { it.id to it.nombre }.orEmpty()
        "unit" -> opciones?.unidades?.map { it.id to it.nombre }.orEmpty()
        else -> emptyList()
    }
    if (openSelector != null) EmployeeWorkOptionsDialog(
        title = when (openSelector) { "employee" -> "Seleccionar empleado"; "payment" -> "Tipo de pago"; else -> "Seleccionar unidad" },
        options = choices,
        onSelect = { id ->
            when (openSelector) {
                "employee" -> employeeId = id
                "payment" -> paymentTypeId = id
                "unit" -> unitId = id
            }
            openSelector = null
        },
        onDismiss = { openSelector = null },
    )
    if (showDatePicker) DateSelectionDialog(
        initialDate = date,
        onSelect = { date = it.toString(); showDatePicker = false },
        onDismiss = { showDatePicker = false },
    )
    showTimePicker?.let { field ->
        EmployeeTimePickerDialog(
            title = if (field == "start") "Seleccionar hora de inicio" else "Seleccionar hora de fin",
            initialValue = if (field == "start") startTime else endTime,
            onDismiss = { showTimePicker = null },
            onSelect = { selected ->
                if (field == "start") startTime = selected else endTime = selected
                showTimePicker = null
            },
        )
    }
}

@Composable
private fun EmployeeTimePickerButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = DetailInk),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = DetailMuted, fontSize = 10.sp)
            Text(value.ifBlank { "Seleccionar hora" }, color = DetailInk, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeTimePickerDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val initialHour = initialValue.substringBefore(':').toIntOrNull() ?: now.hour
    val initialMinute = initialValue.substringAfter(':', "").toIntOrNull() ?: now.minute
    val pickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true,
    )
    ComposablesTheme {
        ComposablesAlertDialog(visible = true, onDismissRequest = onDismiss, paneTitle = title) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = DetailInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                TimePicker(state = pickerState)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = DetailMuted) }
                    TextButton(onClick = {
                        onSelect("${pickerState.hour.toString().padStart(2, '0')}:${pickerState.minute.toString().padStart(2, '0')}")
                    }) { Text("Aceptar", color = DetailTeal) }
                }
            }
        }
    }
}

@Composable
private fun EmployeeWorkPickerButton(label: String, value: String?, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = DetailInk),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = DetailMuted, fontSize = 10.sp)
            Text(value ?: "Seleccionar", color = DetailInk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmployeeWorkOptionsDialog(title: String, options: List<Pair<Long, String>>, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    ComposablesTheme {
        ComposablesAlertDialog(visible = true, onDismissRequest = onDismiss, paneTitle = title) {
            Column {
                Text(title, color = DetailInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.height(280.dp).padding(top = 8.dp)) {
                    items(options, key = { it.first }) { (id, label) ->
                        TextButton(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, color = DetailInk, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (options.isEmpty()) item { Text("No hay opciones disponibles.", color = DetailMuted, modifier = Modifier.padding(12.dp)) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar", color = DetailTeal) }
            }
        }
    }
}

private fun moneyValue(value: Double): String = (kotlin.math.round(value * 100) / 100).toString()

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

@Composable
private fun AsignarEquipoScreen(
    ordenId: Long,
    process: ProcesoOrdenTrabajo,
    options: OpcionesEquipoProceso?,
    loadingOptions: Boolean,
    estimate: DepreciacionEquipoProceso?,
    estimating: Boolean,
    saving: Boolean,
    assigned: Boolean,
    error: String?,
    existingEquipment: List<EquipoProcesoOrden>,
    onLoadOptions: () -> Unit,
    onEstimate: (Long?, String, String?) -> Unit,
    onAssign: (Long, String, String?, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var equipmentId by remember(process.id) { mutableStateOf<Long?>(null) }
    var startDate by remember(process.id) { mutableStateOf(now.date.toString()) }
    var startTime by remember(process.id) { mutableStateOf("${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}") }
    var endDate by remember(process.id) { mutableStateOf(now.date.toString()) }
    var endTime by remember(process.id) { mutableStateOf(startTime) }
    var sigueAsignado by remember(process.id) { mutableStateOf(true) }
    var observations by remember(process.id) { mutableStateOf("") }
    var selectorAbierto by remember { mutableStateOf(false) }
    var calendarioAbierto by remember { mutableStateOf<String?>(null) }
    var relojAbierto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(process.id) { onLoadOptions() }
    LaunchedEffect(options) {
        options?.let { loaded ->
            if (equipmentId == null) equipmentId = loaded.equipos.firstOrNull()?.id
            if (loaded.inicioPredeterminado.isNotBlank()) {
                val start = loaded.inicioPredeterminado.substringBefore('Z')
                startDate = start.substringBefore('T').ifBlank { startDate }
                startTime = start.substringAfter('T', startTime).take(5)
            }
        }
    }

    val start = "${startDate}T$startTime"
    val end = if (sigueAsignado) null else "${endDate}T$endTime"
    LaunchedEffect(equipmentId, start, end) {
        onEstimate(equipmentId, start, end)
    }

    val selectedEquipment = options?.equipos?.firstOrNull { it.id == equipmentId }
    val startValid = startDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && startTime.matches(Regex("\\d{2}:\\d{2}"))
    val endValid = sigueAsignado || (
        endDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
            endTime.matches(Regex("\\d{2}:\\d{2}")) && end.orEmpty() > start
        )
    val canSave = selectedEquipment != null && startValid && endValid && estimate != null &&
        estimate.solapamiento == null && estimate.disponible && !loadingOptions && !estimating && !saving && !assigned

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Asignar equipo al proceso", color = DetailInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text("Proceso ${process.codigo} · ${process.etapa.orEmpty()}", color = DetailMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            shape = RoundedCornerShape(13.dp),
            color = Color(0xFFF0F5F6),
        ) {
            Text(
                "Un equipo puede trabajar en procesos distintos en horarios consecutivos, pero no puede estar asignado a dos procesos al mismo tiempo. El servidor verifica el periodo antes de guardar.",
                color = DetailMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(14.dp),
            )
        }

        Text("Equipo", color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Button(
            onClick = { selectorAbierto = true },
            enabled = options?.equipos?.isNotEmpty() == true && !loadingOptions && !saving,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DetailInk),
            shape = RoundedCornerShape(11.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(selectedEquipment?.codigo ?: "Seleccionar equipo", color = DetailTeal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(selectedEquipment?.nombre ?: "Elija molino, taladro u otro equipo", color = DetailInk, fontSize = 14.sp)
                }
                Text("⌄", color = DetailMuted, fontSize = 19.sp)
            }
        }
        selectedEquipment?.let {
            Text(
                "Valor de adquisición: ${it.valorAdquisicion.displayDecimal()} · Residual: ${it.valorResidual.displayDecimal()} · Vida útil: ${it.vidaUtilMeses} meses",
                color = DetailMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 3.dp, top = 5.dp),
            )
        }

        Text("Periodo de uso", color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 17.dp, bottom = 5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            EquipmentDateButton("Inicio", startDate, Modifier.weight(1f)) { calendarioAbierto = "start" }
            EquipmentTimeButton("Hora de inicio", startTime, Modifier.weight(1f)) { relojAbierto = "start" }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp).clickable { sigueAsignado = !sigueAsignado },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = sigueAsignado, onCheckedChange = { sigueAsignado = it }, enabled = !saving)
            Column {
                Text("Sigue asignado", color = DetailInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Deje el periodo abierto mientras el equipo siga en uso.", color = DetailMuted, fontSize = 12.sp)
            }
        }
        if (!sigueAsignado) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                EquipmentDateButton("Fin", endDate, Modifier.weight(1f)) { calendarioAbierto = "end" }
                EquipmentTimeButton("Hora de fin", endTime, Modifier.weight(1f)) { relojAbierto = "end" }
            }
        }

        Text("Depreciación estimada", color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                when {
                    loadingOptions || estimating -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(18.dp), color = DetailTeal, strokeWidth = 2.dp)
                        Text("  Calculando días y depreciación…", color = DetailMuted, fontSize = 12.sp)
                    }
                    estimate != null -> {
                        EquipmentInfoRow("Días de uso", "${estimate.dias.displayDecimal()} días")
                        EquipmentInfoRow("Depreciación por día", estimate.depreciacionDiaria.displayDecimal())
                        EquipmentInfoRow("Depreciación del periodo", estimate.depreciacionTotal.displayDecimal(), emphasized = true)
                        if (!estimate.disponible) Text("El equipo ya alcanzó su valor residual y no tiene valor por depreciar.", color = Color(0xFFB33A3A), fontSize = 12.sp)
                        estimate.solapamiento?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 12.sp) }
                    }
                    options?.equipos?.isEmpty() == true -> Text("No hay equipos activos disponibles.", color = DetailMuted, fontSize = 13.sp)
                    else -> Text("Seleccione un equipo para calcular la depreciación.", color = DetailMuted, fontSize = 13.sp)
                }
            }
        }
        OutlinedTextField(
            value = observations,
            onValueChange = { if (it.length <= 255) observations = it },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            label = { Text("Observaciones") },
            minLines = 2,
            maxLines = 4,
            enabled = !saving,
        )
        error?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (options == null && !loadingOptions) {
            TextButton(onClick = onLoadOptions, modifier = Modifier.align(Alignment.End)) { Text("Reintentar carga", color = DetailTeal) }
        }

        if (assigned) {
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFE5F4F0)) {
                Text("El equipo se asignó correctamente al proceso.", color = Color(0xFF267A5B), modifier = Modifier.padding(13.dp), fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Volver al proceso", color = DetailMuted) }
            Button(
                onClick = { equipmentId?.let { onAssign(it, start, end, observations) } },
                enabled = canSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DetailTeal),
                shape = RoundedCornerShape(11.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.height(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Asignar equipo")
            }
        }

        if (existingEquipment.isNotEmpty()) {
            Text("Equipos ya registrados en este proceso", color = DetailInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            existingEquipment.forEach { assignment ->
                Surface(Modifier.fillMaxWidth().padding(bottom = 7.dp), shape = RoundedCornerShape(11.dp), color = Color.White) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${assignment.codigoEquipo.orEmpty()} · ${assignment.nombreEquipo.orEmpty()}", color = DetailInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${formatDetailDateTime(assignment.inicio)} – ${assignment.fin?.let(::formatDetailDateTime) ?: "Sigue asignado"}", color = DetailMuted, fontSize = 12.sp)
                        Text("Depreciación registrada: ${assignment.depreciacionTotal.displayDecimal()}", color = DetailTeal, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    val equipmentOptions = options?.equipos.orEmpty().map { it.id to "${it.codigo} · ${it.nombre}" }
    if (selectorAbierto) EmployeeWorkOptionsDialog(
        title = "Seleccionar equipo",
        options = equipmentOptions,
        onSelect = { equipmentId = it; selectorAbierto = false },
        onDismiss = { selectorAbierto = false },
    )
    calendarioAbierto?.let { field ->
        DateSelectionDialog(
            initialDate = if (field == "start") startDate else endDate,
            onSelect = {
                if (field == "start") startDate = it.toString() else endDate = it.toString()
                calendarioAbierto = null
            },
            onDismiss = { calendarioAbierto = null },
        )
    }
    relojAbierto?.let { field ->
        EmployeeTimePickerDialog(
            title = if (field == "start") "Hora de inicio" else "Hora de fin",
            initialValue = if (field == "start") startTime else endTime,
            onDismiss = { relojAbierto = null },
            onSelect = {
                if (field == "start") startTime = it else endTime = it
                relojAbierto = null
            },
        )
    }
}

@Composable
private fun EquipmentDateButton(label: String, date: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DetailInk),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = DetailMuted, fontSize = 10.sp)
            Text(date.toDisplayDate(), color = DetailInk, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EquipmentTimeButton(label: String, time: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DetailInk),
        shape = RoundedCornerShape(11.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = DetailMuted, fontSize = 10.sp)
            Text(time, color = DetailInk, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EquipmentInfoRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (emphasized) DetailInk else DetailMuted, fontSize = 13.sp, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, color = if (emphasized) DetailTeal else DetailInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Double.displayDecimal(): String =
    (kotlin.math.round(this * 10_000) / 10_000).toString()

private fun formatDetailDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    val date = formatDetailDate(value)
    val time = value.substringAfter('T', "").take(5)
    return if (time.isBlank()) date else "$date $time"
}

private fun formatDetailDate(value: String?): String {
    if (value.isNullOrBlank()) return "Sin fecha"
    val date = value.substringBefore('T').split('-')
    return if (date.size == 3) "${date[2]}/${date[1]}/${date[0]}" else value
}
