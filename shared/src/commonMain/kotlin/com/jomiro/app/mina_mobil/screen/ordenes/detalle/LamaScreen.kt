package com.jomiro.app.mina_mobil.screen.ordenes.detalle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jomiro.app.mina_mobil.data.ordenes.*
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.DateSelectionDialog
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import kotlin.math.round

@Composable
internal fun LamaScreen(
    ordenId: Long,
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onOpenAgreements: () -> Unit,
    onOpenProcesses: () -> Unit,
    modifier: Modifier = Modifier,
    repository: OrdenesRepository = koinInject(),
) {
    var data by remember(ordenId) { mutableStateOf<LamaGestion?>(null) }
    var error by remember(ordenId) { mutableStateOf<String?>(null) }
    var busy by remember(ordenId) { mutableStateOf(false) }
    var lotForm by remember(ordenId) { mutableStateOf(false) }
    var editing by remember(ordenId) { mutableStateOf<LamaLote?>(null) }
    var recoveryForm by remember(ordenId) { mutableStateOf(false) }
    var valueForm by remember(ordenId) { mutableStateOf(false) }
    var starting by remember(ordenId) { mutableStateOf<LamaLote?>(null) }
    var deleting by remember(ordenId) { mutableStateOf<LamaLote?>(null) }
    val scope = rememberCoroutineScope()

    fun ejecutar(action: String = "", method: HttpMethod = HttpMethod.Get, body: JsonObject? = null) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { repository.lama(ordenId, action, method, body) }
                .onSuccess {
                    data = it
                    lotForm = false; editing = null; recoveryForm = false; valueForm = false; starting = null; deleting = null
                    if (it.orderId != ordenId) onOpenOrder(it.orderId)
                }
                .onFailure { error = it.message ?: "No se pudo completar la operación." }
            busy = false
        }
    }
    LaunchedEffect(ordenId) { ejecutar() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver a la orden") }
        Text(data?.orderCode ?: "Lama y recuperación", style = MaterialTheme.typography.headlineSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (data == null && !busy) Button(onClick = { ejecutar() }) { Text("Reintentar") }
        data?.let { state ->
            if (state.offline) LamaInfo("Copia de la descarga. Los lotes, tratamientos, recuperaciones y valoraciones se gestionan Online. Suba los pendientes y cambie de modo para registrar; después actualice su descarga.")
            LamaInfo(if (!state.isTreatment)
                "1. Complete el acuerdo: quién conserva la lama y reparto de su oro.\n2. Registre cada lote físico con cantidad estimada, unidad y ubicación.\n3. Inicie una OT por lote completo, sin mezclas ni retiros parciales.\n4. Registre el trabajo, sus costos y los gramos obtenidos en esa nueva OT.\n\nLa tarifa y el cobro de molienda permanecen en la OT original. Conservar lama no registra oro ni dinero automáticamente."
            else "1. Abra los procesos y registre personal, equipos, horas, químicos y costos.\n2. Registre los gramos reales antes de finalizar esta OT; admite varias recuperaciones del mismo lote.\n3. Valore cada recuperación.\n4. Revise el resultado y el reparto heredado. Para documentar el reparto físico, utilice Liquidaciones. Valorar no genera cobro.")
            if (state.isTreatment) Button(onClick = onOpenProcesses, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Abrir procesos y registrar trabajo") }
            else if (state.canAgree) OutlinedButton(onClick = onOpenAgreements, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Paso 1 · Abrir acuerdos de la OT") }
            if (state.canRegister) {
                if (state.agreements.isEmpty()) Text("Primero necesita un acuerdo activo que indique quién conserva la lama y el porcentaje del oro posterior para la empresa. Si no tiene permiso para guardarlo, solicítelo al responsable.")
                else Button(onClick = { editing = null; lotForm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Registrar lote con cantidad estimada") }
            }
            if (state.lots.isEmpty()) Text("Todavía no hay lotes registrados.")
            state.lots.forEach { lot ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lot.code, style = MaterialTheme.typography.titleLarge)
                        Text(lot.status, style = MaterialTheme.typography.labelLarge)
                        Text("Cantidad estimada: ${lot.quantity} ${lot.unit}\nUbicación: ${lot.location}\nFecha: ${lot.date}\nConserva el material: ${lot.holder}")
                        Text("Oro de la lama: empresa ${lot.companyPercentage}% / cliente ${lot.clientPercentage}%", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { onOpenOrder(lot.sourceId) }, enabled = !busy) { Text("Origen: ${lot.sourceCode}") }
                        lot.observations?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (lot.treatmentId != null) {
                            if (!state.isTreatment) OutlinedButton(onClick = { onOpenOrder(lot.treatmentId) }, enabled = !busy) { Text("Abrir tratamiento ${lot.treatmentCode}") }
                            Text("El lote completo ya está vinculado a una OT. No queda saldo para otra, aunque el tratamiento esté pendiente o cancelado.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            if (lot.holder == "empresa" && state.canStart) Button(onClick = { starting = lot }, enabled = !busy) { Text("Iniciar tratamiento del lote completo") }
                            if (state.canEdit) {
                                TextButton(onClick = { editing = lot; lotForm = true }, enabled = !busy) { Text("Corregir cantidad estimada o ubicación") }
                                TextButton(onClick = { deleting = lot }, enabled = !busy) { Text("Anular lote registrado por error") }
                            }
                        }
                        if (lot.recoveredGrams != null && lot.treatmentId != null) {
                            Text("Recuperado: ${lot.recoveredGrams} g\nEmpresa: ${lot.companyGrams} g · Cliente: ${round((lot.recoveredGrams - (lot.companyGrams ?: 0.0)) * 10000) / 10000} g")
                        }
                        lot.result?.let { result ->
                            Text("Valor conocido del oro de la empresa: C$ ${result.companyGoldValue.moneyLama()}\nCostos conocidos del tratamiento: C$ ${result.treatmentCost.moneyLama()}")
                            Text("Resultado referencial: ${result.referenceResult?.let { "C$ ${it.moneyLama()}" } ?: "pendiente de completar recuperación, valoración o costos"}", style = MaterialTheme.typography.titleSmall)
                            Text("Es una referencia económica, no dinero cobrado. No suma otra vez una venta del mismo oro.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.isTreatment) lot.recoveries.forEach { r ->
                            Text("#${r.id} · ${r.date} · ${r.grams} g · ${r.purity?.let { "Pureza ${round(it * 10000) / 100}%" } ?: "Pureza sin medir"} · ${if (!r.needsValuation) "Valorada" else if (r.valued) "Recalcular valoración (corrección o equivalente NIO pendiente)" else "Por valorar"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (state.canRecover) Button(onClick = { recoveryForm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Registrar oro recuperado") }
            if (state.canValue && state.canSeeGold && state.lots.any { l -> l.recoveries.any { it.needsValuation && it.canValue } }) OutlinedButton(onClick = { valueForm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Valorar una recuperación") }
            state.financialSummary?.let { f ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Servicio y aprovechamiento posterior", style = MaterialTheme.typography.titleMedium)
                        Text("Ingresos registrados del servicio: C$ ${f.serviceIncome.moneyLama()}\nCostos del servicio: C$ ${f.serviceCost.moneyLama()}\nValor conocido del oro de la empresa: C$ ${f.companyGoldValue.moneyLama()}\nCostos de tratamientos: C$ ${f.treatmentCost.moneyLama()}")
                        Text("Resultado conjunto referencial: ${f.referenceResult?.let { "C$ ${it.moneyLama()}" } ?: "pendiente de completar los datos"}")
                        Text("Ingreso del servicio + valor del oro de la empresa − costos originales − costos de tratamientos. Cada costo se incluye una vez. No incluye nuevamente ventas del mismo oro. No es saldo de caja ni utilidad neta contable. Lotes pendientes: ${f.pendingLots}.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    data?.let { state ->
        if (lotForm) LamaLoteDialog(state, editing, busy, error, { if (!busy) lotForm = false }) { body ->
            ejecutar(if (editing == null) "lotes" else "lotes/${editing!!.id}", if (editing == null) HttpMethod.Post else HttpMethod.Put, body)
        }
        starting?.let { lot ->
            LamaFechaDialog("Iniciar ${lot.code} completo", "Se creará una OT vinculada para ${lot.quantity} ${lot.unit} estimados. El reparto será empresa ${lot.companyPercentage}% / cliente ${lot.clientPercentage}%. No podrá usar este lote en otra OT.", busy, error, { starting = null }) { date ->
                ejecutar("lotes/${lot.id}/tratamiento", HttpMethod.Post, buildJsonObject { put("fecha", date) })
            }
        }
        deleting?.let { lot ->
            AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("¿Anular ${lot.code}?") }, text = { Text(error ?: "Solo se anula un lote sin tratamiento; su registro se conserva en el historial.") }, confirmButton = { TextButton(enabled = !busy, onClick = { ejecutar("lotes/${lot.id}", HttpMethod.Delete) }) { Text("Anular lote") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancelar") } })
        }
        if (recoveryForm) LamaOroDialog(state, false, busy, error, { recoveryForm = false }) { ejecutar("recuperaciones", HttpMethod.Post, it) }
        if (valueForm) LamaOroDialog(state, true, busy, error, { valueForm = false }) { ejecutar("valoraciones", HttpMethod.Post, it) }
    }
}

@Composable
private fun LamaInfo(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LamaSelect(label: String, options: List<Pair<Long, String>>, value: Long?, onValue: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(options.firstOrNull { it.first == value }?.second ?: "Seleccione") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onValue(id); open = false }) }
        }
    }
}

@Composable
private fun LamaDate(date: String, onDate: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text("Fecha: $date") }
    if (picker) DateSelectionDialog(initialDate = date, onSelect = { onDate(it.toString()); picker = false }, onDismiss = { picker = false })
}

@Composable
private fun LamaNumber(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun LamaLoteDialog(state: LamaGestion, lot: LamaLote?, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    var agreement by remember { mutableStateOf(state.agreements.firstOrNull()?.id) }
    var unit by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf(lot?.quantity?.toString().orEmpty()) }
    var location by remember { mutableStateOf(lot?.location.orEmpty()) }
    var observations by remember { mutableStateOf(lot?.observations.orEmpty()) }
    var date by remember { mutableStateOf(lamaToday()) }
    val number = quantity.replace(',', '.').toDoubleOrNull()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (lot == null) "Registrar lote estimado" else "Corregir ${lot.code}") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lot == null) {
                LamaSelect("Acuerdo del lote", state.agreements.map { it.id to it.label }, agreement) { agreement = it }
                LamaDate(date) { date = it }
                LamaSelect("Unidad de peso", state.units.map { it.id to it.name }, unit) { unit = it }
            }
            LamaNumber("Cantidad estimada del lote completo", quantity) { quantity = it }
            OutlinedTextField(location, { location = it.take(200) }, label = { Text("Ubicación o depósito") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(observations, { observations = it.take(2000) }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
            Text("La cantidad es estimada. Cada lote completo tendrá una única OT de tratamiento, sin mezclas ni retiros parciales. Sus condiciones se conservan al registrarlo.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && number != null && number > 0 && number.isFinite() && location.isNotBlank() && (lot != null || agreement != null && unit != null), onClick = {
        onSave(buildJsonObject {
            put("cantidad_estimada", number!!); put("ubicacion", location); put("observaciones", observations)
            if (lot == null) { put("acuerdo_id", agreement!!); put("unidad_id", unit!!); put("fecha", date) }
        })
    }) { Text(if (busy) "Guardando…" else "Guardar") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun LamaFechaDialog(title: String, text: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var date by remember { mutableStateOf(lamaToday()) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(text); LamaDate(date) { date = it }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(enabled = !busy, onClick = { onSave(date) }) { Text(if (busy) "Creando…" else "Crear OT de tratamiento") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun LamaOroDialog(state: LamaGestion, value: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    val pending = state.lots.flatMap { it.recoveries }.filter { it.needsValuation && it.canValue }
    var recovery by remember { mutableStateOf(pending.firstOrNull()?.id) }
    var currency by remember { mutableStateOf(state.currencies.firstOrNull()?.id) }
    var date by remember { mutableStateOf(lamaToday()) }
    var grams by remember { mutableStateOf("") }
    var purity by remember { mutableStateOf("") }
    val g = grams.replace(',', '.').toDoubleOrNull()
    val p = purity.replace(',', '.').toDoubleOrNull()
    val valid = if (value) recovery != null && currency != null else g != null && g.isFinite() && g >= 0 && (purity.isBlank() || p != null && p.isFinite() && p in 0.0..1.0)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (value) "Valorar recuperación" else "Registrar oro recuperado") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LamaDate(date) { date = it }
            if (value) {
                LamaSelect("Recuperación", pending.map { it.id to "#${it.id} · ${it.grams} g · ${it.date}" }, recovery) { recovery = it }
                LamaSelect("Moneda del precio del oro", state.currencies.map { it.id to it.name }, currency) { currency = it }
                Text("Se calcula con el último precio válido no posterior a la fecha y con la pureza registrada. Se guarda en moneda base. Si faltan precios o tipos de cambio, primero debe registrarlos en la web. Valorar no genera cobro.", style = MaterialTheme.typography.bodySmall)
            } else {
                LamaNumber("Gramos reales recuperados", grams) { grams = it }
                LamaNumber("Pureza opcional (0 a 1)", purity) { purity = it }
                Text("0.80 = 80% de pureza. Deje vacío si no se midió. Registre esta recuperación antes de finalizar la OT.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && valid, onClick = {
        onSave(buildJsonObject {
            put("fecha", date)
            if (value) { put("recuperacion_id", recovery!!); put("precio_moneda", currency!!) }
            else { put("gramos", g!!); if (p != null) put("pureza", p) }
        })
    }) { Text(if (busy) "Guardando…" else if (value) "Calcular y guardar" else "Guardar recuperación") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

private fun lamaToday() = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
private fun Double.moneyLama() = (round(this * 100) / 100).toString()
