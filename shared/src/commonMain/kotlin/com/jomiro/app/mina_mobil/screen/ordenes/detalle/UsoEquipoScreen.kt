package com.jomiro.app.mina_mobil.screen.ordenes.detalle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import com.jomiro.app.mina_mobil.data.ordenes.UsoEquipoDetalle
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun UsoEquipoScreen(
    ordenId: Long,
    procesoId: Long,
    asignacionId: Long,
    onBack: () -> Unit,
    repository: OrdenesRepository = koinInject(),
) {
    var data by remember(asignacionId) { mutableStateOf<UsoEquipoDetalle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var horas by remember { mutableStateOf("") }
    var inicial by remember { mutableStateOf("") }
    var final by remember { mutableStateOf("") }
    var confirmado by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf("entrega") }
    var recursoId by remember { mutableStateOf<Long?>(null) }
    var loteId by remember { mutableStateOf<Long?>(null) }
    var cantidad by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var anular by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val canEdit = data?.puedeEditar == true
    val canSupply = data?.puedeGestionarInsumos == true

    fun ejecutar(action: String, method: HttpMethod, body: JsonObject? = null, cargarHoras: Boolean = false) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { repository.usoEquipo(ordenId, procesoId, asignacionId, action, method, body) }
                .onSuccess {
                    data = it
                    confirmado = it.consumosConfirmados
                    if (cargarHoras) {
                        horas = it.horas?.toString().orEmpty()
                        inicial = it.horometroInicial?.toString().orEmpty()
                        final = it.horometroFinal?.toString().orEmpty()
                    }
                    cantidad = ""
                }
                .onFailure { error = it.message ?: "No se pudo actualizar el equipo." }
            busy = false
        }
    }
    LaunchedEffect(asignacionId) { ejecutar("usage", HttpMethod.Get, cargarHoras = true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver al proceso") }
        Text(data?.name ?: "Trabajo y consumos del equipo", style = MaterialTheme.typography.titleLarge)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (data == null && !busy) Button(onClick = { ejecutar("usage", HttpMethod.Get, cargarHoras = true) }) { Text("Reintentar") }
        data?.let { uso ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(uso.estado, style = MaterialTheme.typography.labelLarge)
                    Text("Depreciación: C$ ${uso.depreciacion}")
                    Text("Electricidad estimada: C$ ${uso.electricidad}")
                    Text("Insumos consumidos: C$ ${uso.insumos}")
                    Text("Total registrado: C$ ${uso.total}", style = MaterialTheme.typography.titleMedium)
                    uso.horas?.takeIf { it > 0 }?.let { Text("Costo por hora: C$ ${(uso.total / it * 100).toLong() / 100.0}") }
                }
            }
            Text("Trabajo efectivo", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(horas, { horas = it }, label = { Text("Horas totales trabajadas") }, enabled = canEdit && !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(inicial, { inicial = it }, label = { Text("Horómetro inicial (opcional)") }, enabled = canEdit && !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(final, { final = it }, label = { Text("Horómetro final (opcional)") }, enabled = canEdit && !busy, modifier = Modifier.fillMaxWidth())
            Text("Ingrese las horas o ambas lecturas. Las fechas asignadas no sustituyen las horas trabajadas.", style = MaterialTheme.typography.bodySmall)
            Row {
                Checkbox(confirmado, { confirmado = it }, enabled = canEdit && !busy)
                Text("Todos los consumos de este uso están registrados", Modifier.padding(top = 12.dp))
            }
            if (canEdit) Button(enabled = !busy, onClick = {
                val valores = listOf(horas, inicial, final)
                if (valores.any { it.isNotBlank() && (it.replace(',', '.').toDoubleOrNull()?.let { n -> n >= 0 && n.isFinite() } != true) }) {
                    error = "Ingrese horas y lecturas numéricas no negativas."
                } else ejecutar("usage", HttpMethod.Put, buildJsonObject {
                    put("horas_efectivas", horas.replace(',', '.').toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                    put("horometro_inicial", inicial.replace(',', '.').toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                    put("horometro_final", final.replace(',', '.').toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                    put("consumos_confirmados", confirmado)
                }, cargarHoras = true)
            }) { Text("Guardar trabajo efectivo") }

            HorizontalDivider()
            Text("Almacén y saldo del equipo", style = MaterialTheme.typography.titleMedium)
            uso.deliveries.forEach { Text("Entrega #${it.id} · ${it.date} · ${it.name}: ${it.quantity} entregados, ${it.available} de saldo actual.", style = MaterialTheme.typography.bodySmall) }
            Text("Entregar retira del almacén. Consumir carga el costo al proceso. El sobrante permanece en el equipo o se devuelve al almacén.", style = MaterialTheme.typography.bodySmall)
            uso.lots.forEach { Text("${it.name}: ${it.available} disponibles · C$ ${it.costoUnitario}/unidad") }
            if (uso.lots.isEmpty()) Text("Sin saldo de insumos.")
            if (canSupply) {
                UsoSelector("Operación", tipo, listOf("entrega" to "Entregar al equipo", "consumo" to "Consumido en este proceso", "devolucion" to "Devolver al almacén"), !busy) { tipo = it }
                if (tipo == "entrega") {
                    UsoSelector("Producto", recursoId?.toString().orEmpty(), uso.resources.map { it.productoId.toString() to it.name }, !busy) { recursoId = it.toLong() }
                } else {
                    UsoSelector("Entrega de origen", loteId?.toString().orEmpty(), uso.lots.map { it.id.toString() to "#${it.id} ${it.name} · saldo ${it.available}" }, !busy) { loteId = it.toLong() }
                }
                OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad en la unidad del producto") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (AAAA-MM-DD)") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    val quantity = cantidad.replace(',', '.').toDoubleOrNull()
                    val id = if (tipo == "entrega") recursoId else loteId
                    if (id == null || quantity == null || !quantity.isFinite() || quantity <= 0) {
                        error = "Seleccione el producto o entrega y una cantidad mayor que cero."
                    } else ejecutar("supplies", HttpMethod.Post, buildJsonObject {
                        put("tipo", tipo); put("cantidad", quantity); put("fecha", fecha)
                        put(if (tipo == "entrega") "producto_id" else "equipo_insumo_id", id)
                    })
                }) { Text("Registrar movimiento") }
            }
            HorizontalDivider()
            Text("Consumos y devoluciones", style = MaterialTheme.typography.titleMedium)
            uso.consumptions.forEach { consumo ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${consumo.date} · ${if (consumo.type == "consumo") "Consumo" else "Devolución"}")
                        Text("${consumo.name} · ${consumo.quantity} · C$ ${consumo.costo}")
                        if (canEdit && consumo.type == "consumo") TextButton(enabled = !busy, onClick = { anular = consumo.id }) { Text("Anular consumo") }
                    }
                }
            }
        }
    }
    anular?.let { id ->
        AlertDialog(onDismissRequest = { anular = null }, title = { Text("¿Anular consumo?") },
            text = { Text("La cantidad volverá al saldo del equipo y se retirará del costo del proceso.") },
            confirmButton = { TextButton(onClick = { anular = null; ejecutar("consumptions/$id", HttpMethod.Delete) }) { Text("Anular") } },
            dismissButton = { TextButton(onClick = { anular = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun UsoSelector(label: String, value: String, opciones: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${opciones.firstOrNull { it.first == value }?.second ?: "Seleccione"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            opciones.forEach { (id, texto) -> DropdownMenuItem(text = { Text(texto) }, onClick = { onSelect(id); open = false }) }
        }
    }
}
