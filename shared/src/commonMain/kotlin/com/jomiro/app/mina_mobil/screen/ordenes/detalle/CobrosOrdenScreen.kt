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
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import kotlin.math.round

@Composable
internal fun CobrosOrdenScreen(
    orderId: Long,
    onBack: () -> Unit,
    onOpenIncomes: () -> Unit,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier,
    repository: CobrosRepository = koinInject(),
) {
    var data by remember(orderId) { mutableStateOf<CobrosOrden?>(null) }
    var pending by remember(orderId) { mutableStateOf(repository.pendientes(orderId)) }
    var error by remember(orderId) { mutableStateOf<String?>(null) }
    var message by remember(orderId) { mutableStateOf<String?>(null) }
    var busy by remember(orderId) { mutableStateOf(false) }
    var form by remember(orderId) { mutableStateOf(false) }
    var removing by remember(orderId) { mutableStateOf<CobroPendiente?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        busy = true; error = null
        scope.launch {
            runCatching { repository.cargar(orderId) }.onSuccess { data = it }.onFailure { error = it.message }
            pending = repository.pendientes(orderId)
            busy = false
        }
    }
    LaunchedEffect(orderId) { load() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver a la orden") }
        Text(data?.orderCode ?: "Cobros de la orden", style = MaterialTheme.typography.headlineSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { CobroInfo(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { load() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Actualizar consulta") }
        CobroInfo("1. Registre el ingreso del servicio en la OT; guardar la tarifa del acuerdo no lo crea.\n2. Registre el pago que realmente recibió, completo o por abonos.\n3. En Online se confirma en el servidor y genera entrada en su caja. En Offline queda pendiente en el teléfono hasta subirlo. Finalizar la OT no significa que esté pagada.")
        OutlinedButton(onClick = onOpenIncomes, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Abrir ingresos de la OT") }
        data?.let { state ->
            Text("Cliente: ${state.clientName ?: "Sin cliente"}\nCaja asignada: ${state.cashRegister?.name ?: "No hay caja activa"}")
            if (state.offline) CobroInfo("Copia de la última descarga. Los pagos pendientes del teléfono se reservan en el saldo local, pero todavía no son cobros confirmados ni movimientos de caja. Otros usuarios pueden haber registrado pagos después de la descarga.")
            if (state.incomes.isEmpty()) Text("No hay ingresos registrados para cobrar. Abra Ingresos y registre el servicio, incluso si la OT ya finalizó.")
            state.incomes.forEach { income ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(income.description, style = MaterialTheme.typography.titleMedium)
                        Text("Total: ${income.currency.orEmpty()} ${income.total.moneyCobro()}\nCobrado confirmado: ${income.confirmed.moneyCobro()}\nPendiente en este teléfono: ${income.pending.moneyCobro()}")
                        if (income.mixedCurrency) Text("Tiene pagos confirmados en otra moneda. Revise su saldo y cobre desde la web; no se mezclan importes para calcular un saldo móvil.", color = MaterialTheme.colorScheme.error)
                        else Text("Disponible para registrar: ${income.currency.orEmpty()} ${income.available.moneyCobro()}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (state.statusCode == 0) Text("La OT está cancelada. No admite nuevos cobros.")
            else if (state.clientId == null) Text("Este trabajo propio no tiene cliente al que cobrar.")
            else if (!state.canCreate) Text("Su usuario puede consultar; necesita permiso de crear cobros para registrar pagos.")
            else if (state.cashRegister == null || state.paymentMethods.isEmpty() || state.currencies.isEmpty()) Text("Necesita caja activa asignada, moneda y método de pago activos. Solicite completar esa configuración y actualice la descarga si está Offline.")
            else Button(onClick = { form = true }, enabled = !busy && pending.none { it.syncStatus == "unknown" } && state.incomes.any { !it.mixedCurrency && it.available > 0.005 }, modifier = Modifier.fillMaxWidth()) { Text(if (state.offline) "Registrar pago Offline" else "Registrar cobro o abono") }
            if (state.history.isNotEmpty()) Text("Cobros de esta OT en su caja", style = MaterialTheme.typography.titleLarge)
            state.history.forEach { payment ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("${payment.code} · ${payment.status}", style = MaterialTheme.typography.titleMedium)
                    Text("${payment.date.orEmpty()} · ${payment.currency.orEmpty()} ${payment.amount.moneyCobro()}\n${payment.paymentMethod.orEmpty()}")
                    payment.reference?.takeIf { it.isNotBlank() }?.let { Text("Referencia: $it") }
                } }
            }
        }
        if (pending.isNotEmpty()) {
            Text("Pagos pendientes en este teléfono", style = MaterialTheme.typography.titleLarge)
            pending.forEach { payment ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pendiente local #${-payment.id} · ${payment.currency} ${payment.amount.moneyCobro()}", style = MaterialTheme.typography.titleMedium)
                    Text("${payment.payload["date"]?.jsonPrimitive?.content.orEmpty()} · ${payment.paymentMethod}")
                    Text(when (payment.syncStatus) {
                        "unknown" -> "Envío sin respuesta confirmada. Reintente subir este mismo pendiente: su identificador evita crear otro cobro si el servidor ya lo recibió."
                        "rejected" -> "El servidor rechazó el envío. Revise el motivo de sincronización, corrija el pendiente y vuelva a subir."
                        else -> "Guardado en este dispositivo. Todavía no confirmado en caja."
                    })
                    if (payment.syncStatus != "unknown") TextButton(onClick = { removing = payment }, enabled = !busy) { Text("Quitar pendiente para corregirlo") }
                } }
            }
            Button(onClick = onOpenUpload, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ir a Subir datos / reintentar envío") }
            Text("Si ya recibió el dinero, no quite el pendiente sin revisar el pago. Un pago enviado sin respuesta se reintenta; no se vuelve a registrar.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (form && data != null) CobroForm(data!!, busy, error, { if (!busy) form = false }) { date, currency, method, lines, reference, observations ->
        if (!busy) {
            busy = true; error = null
            scope.launch {
                runCatching { repository.guardar(data!!, date, currency, method, lines, reference, observations) }
                    .onSuccess { message = it; form = false }.onFailure { error = it.message }
                pending = repository.pendientes(orderId)
                runCatching { repository.cargar(orderId) }.onSuccess { data = it }.onFailure { error = it.message }
                busy = false
            }
        }
    }
    removing?.let { payment ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("¿Quitar el pendiente local?") }, text = { Text("Se quitará este registro del teléfono para poder corregirlo. No genera una devolución de dinero ni anula un cobro confirmado. Si recibió ${payment.currency} ${payment.amount.moneyCobro()}, registre después el pago correcto.") }, confirmButton = { TextButton(onClick = {
            runCatching { repository.quitar(payment.id) }.onSuccess { removing = null; load() }.onFailure { error = it.message; removing = null }
        }) { Text("Quitar pendiente") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Conservar") } })
    }
}

@Composable
private fun CobroInfo(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) { Text(text, Modifier.fillMaxWidth().padding(16.dp)) }
}

@Composable
private fun CobroSelect(label: String, options: List<CobroOpcion>, selected: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(options.firstOrNull { it.id == selected }?.name ?: "Seleccione") }
            DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { onSelect(option.id); expanded = false }) } }
        }
    }
}

@Composable
private fun CobroForm(state: CobrosOrden, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (String, Long, Long, Map<Long, Double>, String, String) -> Unit,
) {
    val currencies = state.currencies.filter { c -> state.incomes.any { it.currencyId == c.id && !it.mixedCurrency && it.available > 0.005 } }
    var currency by remember { mutableStateOf(currencies.firstOrNull()?.id) }
    var method by remember { mutableStateOf(state.paymentMethods.firstOrNull()?.id) }
    var date by remember { mutableStateOf(kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var picker by remember { mutableStateOf(false) }
    var amounts by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var reference by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val incomes = state.incomes.filter { it.currencyId == currency && !it.mixedCurrency && it.available > 0.005 }
    val values = amounts.filterValues { it.isNotBlank() }.mapValues { it.value.replace(',', '.').toDoubleOrNull() }
    val valid = values.isNotEmpty() && values.all { (id, amount) -> amount != null && amount.isFinite() && amount >= 0.01 && amount <= (incomes.firstOrNull { it.id == id }?.available ?: 0.0) + 0.005 && kotlin.math.abs(amount * 100 - round(amount * 100)) < 0.0001 }
    val total = values.values.sumOf { it ?: 0.0 }
    if (confirm) AlertDialog(onDismissRequest = { if (!busy) confirm = false }, title = { Text(if (state.offline) "¿Guardar el pago recibido?" else "¿Confirmar el dinero recibido?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cliente: ${state.clientName}\nOT: ${state.orderCode}\nCaja: ${state.cashRegister?.name}\nImporte: ${currencies.firstOrNull { it.id == currency }?.name} ${total.moneyCobro()}\nMétodo: ${state.paymentMethods.firstOrNull { it.id == method }?.name}\nFecha: $date")
            Text(if (state.offline) "Se guarda pendiente en este teléfono. Al subir, el servidor validará el saldo y generará la entrada de caja." else "Se enviará como cobro Finalizado y generará una sola entrada de caja. Confirme únicamente el importe recibido.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && valid, onClick = { onSave(date, currency!!, method!!, values.mapValues { it.value!! }, reference, observations) }) { Text(if (busy) "Guardando…" else "Confirmar pago recibido") } }, dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text("Revisar") } })
    else AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Cobro o abono · ${state.orderCode}") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Caja: ${state.cashRegister?.name}. Indique cuánto recibió y a qué ingresos se aplica. Deje vacíos los que no va a cobrar.")
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text("Fecha: $date") }
            CobroSelect("Moneda del pago (misma del ingreso)", currencies, currency) { currency = it; amounts = emptyMap() }
            CobroSelect("Método de pago", state.paymentMethods, method) { method = it }
            incomes.forEach { income ->
                Text("${income.description}\nDisponible: ${income.currency} ${income.available.moneyCobro()}")
                OutlinedTextField(amounts[income.id].orEmpty(), { amounts = amounts + (income.id to it) }, label = { Text("Importe recibido para este ingreso") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
            Text("Total recibido: ${if (total.isFinite()) total.moneyCobro() else "Revise los importes"}", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(reference, { reference = it.take(100) }, label = { Text("Referencia del pago (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(observations, { observations = it.take(5000) }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
            if (!valid && amounts.values.any { it.isNotBlank() }) Text("Use importes positivos con hasta dos decimales, sin superar el saldo disponible.", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && valid && currency != null && method != null, onClick = { confirm = true }) { Text("Revisar y confirmar") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
    if (picker) DateSelectionDialog(initialDate = date, onSelect = { date = it.toString(); picker = false }, onDismiss = { picker = false })
}

private fun Double.moneyCobro(): String {
    val cents = round(this * 100).toLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
