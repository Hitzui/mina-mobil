package com.jomiro.app.mina_mobil.screen.ordenes.detalle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jomiro.app.mina_mobil.data.ordenes.*
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.DateSelectionDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import kotlin.math.round
import kotlin.time.Clock

@Composable
internal fun SeguimientoResumen(ordenId: Long, onOpen: () -> Unit, repository: SeguimientoRepository = koinInject()) {
    var state by remember(ordenId) { mutableStateOf<EstadoSeguimiento?>(null) }
    var error by remember(ordenId) { mutableStateOf<String?>(null) }
    LaunchedEffect(ordenId) {
        runCatching { repository.cargar(ordenId) }.onSuccess { state = it }.onFailure { error = it.message }
        while (true) {
            delay(30_000)
            if (state?.offline == false) runCatching { repository.cargar(ordenId) }
                .onSuccess { state = it; error = null }.onFailure { error = "Se conserva la última consulta: ${it.message}" }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avance y tiempo de reparación", style = MaterialTheme.typography.titleMedium)
            state?.let { AvanceConfirmado(it) }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Ver reparaciones y plazos") }
        }
    }
}

@Composable
private fun AvanceConfirmado(state: EstadoSeguimiento) {
    val data = state.data
    Text("${data.completed} de ${data.total} procesos finalizados · ${data.percent?.let { "$it%" } ?: "Sin procesos vigentes"}")
    LinearProgressIndicator(progress = { ((data.percent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF16876B))
    Text("Cada proceso vigente tiene el mismo peso. Cancelados: ${data.cancelled}.", style = MaterialTheme.typography.bodySmall)
    data.timeline?.let { timeline ->
        Text("Tiempo de asignación de la OT", style = MaterialTheme.typography.labelLarge)
        BarraReparaciones(timeline.elapsed, timeline.segments)
        Text("${timeline.start.fechaSeguimiento()} — ${timeline.end.fechaSeguimiento()}", style = MaterialTheme.typography.bodySmall)
        Text("${timeline.hours.horasSeguimiento()} h calendario con algún equipo en reparación. Los intervalos simultáneos se cuentan una vez.", style = MaterialTheme.typography.bodySmall)
    }
    Text("Azul: transcurrido · Rojo: reparación · Gris: por transcurrir. No representa horas trabajadas.", style = MaterialTheme.typography.bodySmall)
    if (state.offline) {
        Text("REFERENCIA DE LA ÚLTIMA DESCARGA. Puede haber otros cambios en la web. Suba los pendientes y descargue de nuevo para actualizar el progreso.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    } else if (state.pending.isNotEmpty()) {
        Text("CONSULTA DEL SERVIDOR CON CAMBIOS PENDIENTES EN ESTE TELÉFONO. Complete la subida y actualice la consulta para ver el resultado conjunto.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Text("Calculado: ${data.generatedAt.fechaSeguimiento()}${state.downloadedAt?.takeIf { state.offline }?.let { " · Descarga: ${it.fechaSeguimiento()}" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BarraReparaciones(elapsed: Double, segments: List<TramoReparacion>) {
    Canvas(Modifier.fillMaxWidth().height(20.dp).semantics { contentDescription = "Periodo asignado. Rojo: intervalos de reparación registrados. Transcurrido ${round(elapsed)} por ciento." }) {
        drawRect(Color(0xFFDCE3E8))
        drawRect(Color(0xFF89BDCF), size = Size(size.width * elapsed.toFloat().coerceIn(0f,100f) / 100, size.height))
        segments.forEach { segment -> drawRect(Color(0xFFD94A52), topLeft = Offset(size.width * segment.from.toFloat() / 100, 0f), size = Size(size.width * (segment.to - segment.from).toFloat() / 100, size.height)) }
    }
}

@Composable
internal fun SeguimientoOrdenScreen(
    ordenId: Long, onBack: () -> Unit, onCosts: (Long) -> Unit,
    modifier: Modifier = Modifier, repository: SeguimientoRepository = koinInject(),
) {
    var state by remember(ordenId) { mutableStateOf<EstadoSeguimiento?>(null) }
    var error by remember(ordenId) { mutableStateOf<String?>(null) }
    var message by remember(ordenId) { mutableStateOf<String?>(null) }
    var busy by remember(ordenId) { mutableStateOf(false) }
    var form by remember(ordenId) { mutableStateOf<FormularioSeguimiento?>(null) }
    var confirmDiscard by remember(ordenId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun cargar() {
        if (busy) return
        busy = true; error = null
        scope.launch { runCatching { repository.cargar(ordenId) }.onSuccess { state = it }.onFailure { error = it.message }; busy = false }
    }
    fun guardar(type: String, body: JsonObject, description: String) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching { repository.guardar(ordenId, type, body, description) }
                .onSuccess { result ->
                    form = null; message = result
                    state = state?.copy(pending = repository.pendientes(ordenId))
                    runCatching { repository.cargar(ordenId) }.onSuccess { state = it }.onFailure { error = "El cambio quedó guardado; no se pudo renovar el progreso: ${it.message}" }
                }.onFailure { error = it.message }
            busy = false
        }
    }
    LaunchedEffect(ordenId) {
        cargar()
        while (true) {
            delay(30_000)
            if (state?.offline == false && !busy && form == null && !confirmDiscard) cargar()
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver a la OT") }
        Text(state?.data?.orderCode ?: "Seguimiento de la OT", style = MaterialTheme.typography.headlineSmall)
        Text("Progreso, reparaciones y plazos", style = MaterialTheme.typography.titleMedium)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = ::cargar, enabled = !busy) { Text("Actualizar consulta") }
        state?.let { s ->
            AvanceConfirmado(s)
            Text("El horómetro mantiene las horas reales y su costo. Una reparación no modifica el plazo ni genera un gasto automáticamente. Fechas y horas del taller: ${s.data.timezone}.", style = MaterialTheme.typography.bodySmall)
            Text("1. Ponga el equipo en reparación: interna o enviado a taller. Deje el fin vacío si continúa. 2. Al terminar, indique el fin y qué se hizo. 3. Registre el costo total y su descripción.", style = MaterialTheme.typography.bodyMedium)
            if (s.pending.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cambios pendientes de sincronización", style = MaterialTheme.typography.titleMedium)
                    s.pending.forEach { Text("• ${it.description} · ${when(it.status) { "unknown" -> "envío sin confirmación"; "rejected" -> "rechazado, sin aplicar"; else -> "pendiente de subida" }}") }
                    Text("Abra Menú → Sincronización → Subir. Después descargue los datos actualizados. Si hubo conflicto con la web, revise el rechazo; puede quitar estos cambios locales cuando el servidor confirmó que no los aplicó.", style = MaterialTheme.typography.bodySmall)
                    if (!s.hasUncertainChanges) TextButton(onClick = { confirmDiscard = true }, enabled = !busy) { Text("Quitar cambios locales de seguimiento") }
                } }
            }
            val enabled = !busy && !s.hasUncertainChanges
            s.formAssignments.forEach { assignment ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${assignment.code} · ${assignment.equipment}", style = MaterialTheme.typography.titleMedium)
                    Text("${assignment.process} · Asignación #${assignment.id}")
                    Text("${assignment.start.fechaSeguimiento()} — ${assignment.end?.fechaSeguimiento() ?: "Sin fin definido"}")
                    if (assignment.id > 0) { BarraReparaciones(assignment.elapsed, assignment.segments); Text("${assignment.hours.horasSeguimiento()} h de reparación en la última consulta.") }
                    else Text("Asignación local pendiente. Su barra aparecerá después de sincronizar.")
                    if (s.data.canRegister && assignment.canRegister) {
                        Button(onClick = { form = FormularioSeguimiento("repair.create", assignment) }, enabled = enabled) { Text("Poner equipo en reparación") }
                        OutlinedButton(onClick = { form = FormularioSeguimiento("equipment.extend", assignment) }, enabled = enabled) { Text("Ampliar plazo manualmente") }
                    }
                } }
            }
            Text("Registros de reparación", style = MaterialTheme.typography.titleLarge)
            repository.reparacionesEditables(s).forEach { repair ->
                val assignment = s.formAssignments.firstOrNull { it.id == repair.assignmentId }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${repair.equipment} · ${repair.process}", style = MaterialTheme.typography.titleMedium)
                    Text("${if (repair.id < 0) "Registro local" else "#${repair.id}"} · ${if (repair.end == null) "En reparación" else "Reparación terminada"}", color = if (repair.end == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(repair.reason)
                    Text(when (repair.repairLocation) { "interna" -> "Reparación interna · personal propio"; "externa" -> "Reparación externa · enviado a taller"; else -> "Lugar de reparación sin especificar" })
                    repair.workDescription?.takeIf(String::isNotBlank)?.let { Text("Trabajo realizado: $it") }
                    Text("${repair.start.fechaSeguimiento()} — ${repair.end?.fechaSeguimiento() ?: "Continúa"}")
                    repair.totalHours?.let { Text("Tiempo total de reparación: ${it.horasSeguimiento()} h${if (repair.end == null) " (al momento de la consulta)" else ""}. En la asignación: ${repair.hours.horasSeguimiento()} h.") }
                        ?: Text("Duración pendiente de cálculo al sincronizar este intervalo.", style = MaterialTheme.typography.bodySmall)
                    if (s.data.canEdit && assignment != null) {
                        OutlinedButton(onClick = { form = FormularioSeguimiento("repair.update", assignment, repair) }, enabled = enabled) { Text("Terminar o corregir intervalo") }
                        TextButton(onClick = { form = FormularioSeguimiento("repair.delete", assignment, repair) }, enabled = enabled) { Text("Anular registro por error") }
                    }
                    repair.costs.groupBy { it.currency }.forEach { (currency, costs) -> Text("Costo total registrado: $currency ${costs.sumOf { it.amount }.horasSeguimiento()}", style = MaterialTheme.typography.titleSmall) }
                    repair.costs.forEach { Text("${it.description} · ${it.currency} ${it.amount}") }
                    if (s.data.canCost && repair.canCost && assignment != null) OutlinedButton(onClick = { form = FormularioSeguimiento("repair_cost.create", assignment, repair) }, enabled = enabled) { Text("Registrar costo de reparación") }
                    if (assignment != null && repair.costs.isNotEmpty()) TextButton(onClick = { onCosts(assignment.processId) }, enabled = !busy) { Text("Abrir costos del proceso para corregir") }
                } }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("¿Quitar cambios locales de seguimiento?") }, text = { Text("Se quitarán reparaciones, correcciones, ampliaciones y costos pendientes de esta OT. La barra descargada no cambia. Luego podrá subir otros pendientes y descargar la versión del servidor para revisar las diferencias.") }, confirmButton = { TextButton(onClick = {
        runCatching { repository.quitarPendientes(ordenId) }.onSuccess { state = state?.copy(pending = emptyList()); message = "Cambios locales quitados. Actualice su descarga antes de volver a registrar."; cargar() }.onFailure { error = it.message }
        confirmDiscard = false
    }) { Text("Quitar pendientes") } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } })
    form?.let { f -> state?.let { s -> FormSeguimiento(f, s, busy, error, { if (!busy) form = null }) { body, description -> guardar(f.type, body, description) } } }
}

private data class FormularioSeguimiento(val type: String, val assignment: AsignacionSeguimiento, val repair: ReparacionEquipo? = null)

@Composable
private fun FormSeguimiento(form: FormularioSeguimiento, state: EstadoSeguimiento, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (JsonObject, String) -> Unit) {
    var start by remember(form) { mutableStateOf(form.repair?.start?.take(16).orEmpty()) }
    var end by remember(form) { mutableStateOf(form.repair?.end?.take(16).orEmpty()) }
    var reason by remember(form) { mutableStateOf(if (form.type == "repair_cost.create") form.repair?.workDescription?.take(255).orEmpty() else form.repair?.reason.orEmpty()) }
    var location by remember(form) { mutableStateOf(form.repair?.repairLocation.orEmpty()) }
    var workDescription by remember(form) { mutableStateOf(form.repair?.workDescription.orEmpty()) }
    var category by remember(form) { mutableStateOf<Long?>(null) }
    var currency by remember(form) { mutableStateOf<Long?>(null) }
    var date by remember(form) { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.of(state.data.timezone)).date.toString()) }
    var showDate by remember(form) { mutableStateOf(false) }
    var amount by remember(form) { mutableStateOf("") }
    var localError by remember(form) { mutableStateOf<String?>(null) }
    val cost = form.type == "repair_cost.create"
    val extend = form.type == "equipment.extend"
    val delete = form.type == "repair.delete"
    val title = when { cost -> "Costo total de reparación"; extend -> "Ampliar plazo"; delete -> "Anular registro"; form.repair != null -> "Terminar o corregir"; else -> "Poner equipo en reparación" }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${form.assignment.equipment} · ${form.assignment.process} · ${state.data.orderCode}")
            if (delete) Text("Se conserva el historial. Si tiene costos vigentes vinculados, primero corríjalos o anúlelos en Costos del proceso.")
            else if (cost) {
                Text("Este costo se carga una sola vez al proceso de esta OT. No registra una salida de caja.")
                SeleccionSeguimiento("Categoría", state.data.categories, category) { category = it }
                SeleccionSeguimiento("Moneda", state.data.currencies, currency) { currency = it }
                OutlinedButton(onClick = { showDate = true }) { Text("Fecha del costo: $date") }
                OutlinedTextField(amount, { amount = it }, label = { Text("Costo total de reparación") }, singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("Descripción de lo que se hizo") })
            } else {
                Text("Fecha y hora del taller (${state.data.timezone}).")
                if (!extend) {
                    Text("¿Dónde se reparará?", style = MaterialTheme.typography.labelLarge)
                    Row { RadioButton(selected = location == "interna", onClick = { location = "interna" }); TextButton(onClick = { location = "interna" }) { Text("Interna · personal propio") } }
                    Row { RadioButton(selected = location == "externa", onClick = { location = "externa" }); TextButton(onClick = { location = "externa" }) { Text("Externa · enviado a taller") } }
                }
                if (!extend) FechaHoraSeguimiento("Inicio de la falla", start, state.data.timezone, false) { start = it }
                FechaHoraSeguimiento(if (extend) "Nuevo fin de asignación" else "Fin de la reparación", end, state.data.timezone, !extend) { end = it }
                if (!extend) {
                    OutlinedTextField(reason, { reason = it }, label = { Text("Motivo de la falla") })
                    OutlinedTextField(workDescription, { workDescription = it }, label = { Text("Descripción del trabajo realizado") }, supportingText = { Text("Puede completarla al terminar.") })
                }
                Text(if (extend) "Se comprueba disponibilidad. No modifica el horómetro ni sus costos históricos." else "La reparación no amplía el plazo ni genera un costo automáticamente.", style = MaterialTheme.typography.bodySmall)
            }
            (localError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        runCatching {
            val body = buildJsonObject {
                put("assignment_id", form.assignment.id)
                form.repair?.let { put("repair_id", it.id); put("expected_version", it.version) }
                when {
                    delete -> Unit
                    cost -> {
                        require(category != null && currency != null) { "Seleccione categoría y moneda." }
                        kotlinx.datetime.LocalDate.parse(date.trim())
                        val value = amount.replace(',', '.').toDoubleOrNull()
                        require(value != null && value.isFinite() && value >= 0) { "Indique un importe válido." }
                        require(reason.isNotBlank() && reason.length <= 255) { "Describa el costo (máximo 255 caracteres)." }
                        put("categoria_costo_id", category!!); put("moneda_id", currency!!); put("fecha", date.trim())
                        put("total_cost", value); put("descripcion", reason.trim())
                    }
                    extend -> {
                        val finish = LocalDateTime.parse(end.trim().replace(' ', 'T'))
                        val oldEnd = form.assignment.end?.let { LocalDateTime.parse(it) } ?: LocalDateTime.parse(form.assignment.start)
                        require(finish > oldEnd) { "El nuevo fin debe ser posterior al plazo actual." }
                        put("ended_at", finish.toString()); put("expected_end", form.assignment.end?.let(::JsonPrimitive) ?: JsonNull)
                    }
                    else -> {
                        val begin = LocalDateTime.parse(start.trim().replace(' ', 'T'))
                        val finish = end.takeIf(String::isNotBlank)?.let { LocalDateTime.parse(it.trim().replace(' ', 'T')) }
                        require(finish == null || finish > begin) { "El fin debe ser posterior al inicio." }
                        val zone = TimeZone.of(state.data.timezone)
                        require(begin.toInstant(zone) <= Clock.System.now() && (finish == null || finish.toInstant(zone) <= Clock.System.now())) { "Registre fechas reales; la reparación no admite fechas futuras." }
                        val assignedFrom = LocalDateTime.parse(form.assignment.start)
                        val assignedTo = form.assignment.end?.let { LocalDateTime.parse(it) }
                        require(begin >= assignedFrom && (assignedTo == null || begin < assignedTo)) { "La reparación debe comenzar dentro del periodo de asignación." }
                        require(reason.isNotBlank() && reason.length <= 1000) { "Indique el motivo (máximo 1000 caracteres)." }
                        require(location in setOf("interna", "externa")) { "Seleccione reparación interna o externa." }
                        require(workDescription.length <= 2000) { "La descripción del trabajo admite hasta 2000 caracteres." }
                        put("started_at", begin.toString()); put("ended_at", finish?.let { JsonPrimitive(it.toString()) } ?: JsonNull); put("reason", reason.trim())
                        put("repair_location", location); put("work_description", workDescription.trim().takeIf(String::isNotBlank)?.let(::JsonPrimitive) ?: JsonNull)
                    }
                }
            }
            onSave(body, "$title · ${form.assignment.equipment}")
        }.onFailure { localError = it.message ?: "Revise las fechas y los campos." }
    }) { Text(if (busy) "Guardando…" else "Confirmar") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
    if (showDate) DateSelectionDialog(initialDate = date, onSelect = { date = it.toString(); showDate = false }, onDismiss = { showDate = false })
}

@Composable
private fun FechaHoraSeguimiento(label: String, value: String, timezone: String, optional: Boolean, onChange: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    val now = Clock.System.now().toLocalDateTime(TimeZone.of(timezone))
    val date = value.take(10)
    val time = value.substringAfter('T', "").take(5)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { picker = true }) { Text(date.ifBlank { "Elegir fecha" }) }
        OutlinedTextField(time, { onChange("${date.ifBlank { now.date.toString() }}T$it") }, label = { Text("Hora HH:mm (24 horas)") }, singleLine = true)
        if (optional) TextButton(onClick = { onChange("") }) { Text(if (value.isBlank()) "Sin fin: continúa en reparación" else "Dejar sin fin: continúa en reparación") }
    }
    if (picker) DateSelectionDialog(initialDate = date.ifBlank { now.date.toString() }, onSelect = {
        onChange("${it}T${time.ifBlank { now.time.toString().take(5) }}"); picker = false
    }, onDismiss = { picker = false })
}

@Composable
private fun SeleccionSeguimiento(label: String, items: List<OpcionSeguimiento>, value: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${items.firstOrNull { it.id == value }?.name ?: "Seleccione"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { items.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { onSelect(item.id); expanded = false }) } }
    }
}

private fun String.fechaSeguimiento() = replace('T', ' ').take(19)
private fun Double.horasSeguimiento() = (round(this * 100) / 100).toString()
