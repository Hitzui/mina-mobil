package com.jomiro.app.mina_mobil.screen.ordenes.detalle.acuerdos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesAcuerdoOrden
import com.jomiro.app.mina_mobil.data.ordenes.TipoParticipacionOpcion
import com.jomiro.app.mina_mobil.data.ordenes.MonedaAcuerdoOpcion

private val AgreementInk = Color(0xFF172C3D)
private val AgreementTeal = Color(0xFF087E83)
private val AgreementMuted = Color(0xFF71808B)

@Composable
fun AcuerdosOrdenScreen(
    orderCode: String,
    agreements: List<AcuerdoOrdenTrabajo>,
    options: OpcionesAcuerdoOrden?,
    loadingOptions: Boolean,
    saving: Boolean,
    deleting: Boolean,
    saved: Boolean,
    error: String?,
    canCreate: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onLoadOptions: () -> Unit,
    onSave: (Long?, Long, Double?, Double?, Double?, Long, String, Boolean, String, Double?) -> Unit,
    onDelete: (AcuerdoOrdenTrabajo) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<AcuerdoOrdenTrabajo?>(null) }
    var showingForm by remember { mutableStateOf(false) }
    var deletingAgreement by remember { mutableStateOf<AcuerdoOrdenTrabajo?>(null) }
    var editWarning by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saved) { if (saved) { showingForm = false; deletingAgreement = null; onClearMessage() } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(orderCode, color = AgreementMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Acuerdos de participación", color = AgreementInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Condiciones pactadas para esta orden de trabajo.", color = AgreementMuted, fontSize = 13.sp)
            }
            if (canCreate) Button(
                onClick = { editing = null; onLoadOptions(); showingForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = AgreementTeal),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Nuevo") }
        }
        error?.let { MessageCard(it, error = true) }
        editWarning?.let { MessageCard(it, error = true) }
        if (agreements.isEmpty()) MessageCard("No hay acuerdos de participación para esta orden.")
        agreements.forEach { agreement ->
            AgreementCard(
                agreement,
                canEdit = canEdit,
                canDelete = canDelete,
                onEdit = {
                    if (agreement.tipoParticipacionId <= 0 || agreement.monedaId <= 0) {
                        editWarning = "Vuelve a descargar los datos para editar este acuerdo sin alterar sus referencias actuales."
                    } else {
                        editWarning = null
                        editing = agreement
                        onLoadOptions()
                        showingForm = true
                    }
                },
                onDelete = { deletingAgreement = agreement },
            )
        }
    }

    if (showingForm) {
        if (loadingOptions) AlertDialog(
            onDismissRequest = { showingForm = false },
            title = { Text("Acuerdo de participación") },
            text = { Text("Cargando tipos y monedas…") },
            confirmButton = { TextButton(onClick = { showingForm = false }) { Text("Cerrar") } },
        ) else if (options != null) AgreementFormDialog(
            agreement = editing,
            options = options,
            saving = saving,
            error = error,
            onDismiss = { showingForm = false; onClearMessage() },
            onSave = onSave,
        ) else if (error != null) AlertDialog(
            onDismissRequest = { showingForm = false },
            title = { Text("No se pudieron cargar las opciones") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { showingForm = false; onClearMessage() }) { Text("Cerrar") } },
        )
    }

    deletingAgreement?.let { agreement ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingAgreement = null },
            title = { Text("Eliminar acuerdo") },
            text = { Text("¿Deseas eliminar el acuerdo de ${agreement.tipoParticipacion ?: "participación"}?") },
            confirmButton = { TextButton(enabled = !deleting, onClick = { onDelete(agreement) }) { Text(if (deleting) "Eliminando…" else "Eliminar") } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { deletingAgreement = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AgreementFormDialog(
    agreement: AcuerdoOrdenTrabajo?,
    options: OpcionesAcuerdoOrden,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Long?, Long, Double?, Double?, Double?, Long, String, Boolean, String, Double?) -> Unit,
) {
    var typeId by remember(agreement, options) { mutableStateOf(agreement?.tipoParticipacionId?.takeIf { it > 0 } ?: options.tipos.firstOrNull()?.id) }
    var currencyId by remember(agreement, options) { mutableStateOf(agreement?.monedaId?.takeIf { it > 0 } ?: options.monedaBaseId ?: options.monedas.firstOrNull()?.id) }
    var modality by remember(agreement) { mutableStateOf(if (agreement?.modalidad == "tarifa") "tarifa" else "porcentaje") }
    var clientPercent by remember(agreement) { mutableStateOf(agreement?.porcentajeCliente.orEmpty()) }
    var companyPercent by remember(agreement) { mutableStateOf(agreement?.porcentajeEmpresa.orEmpty()) }
    var fee by remember(agreement) { mutableStateOf(agreement?.tarifaServicio.orEmpty()) }
    var observations by remember(agreement) { mutableStateOf(agreement?.observaciones.orEmpty()) }
    var active by remember(agreement) { mutableStateOf(agreement?.activo ?: true) }
    var lamaDestino by remember(agreement) { mutableStateOf(agreement?.lamaDestino ?: "sin_definir") }
    var lamaEmpresa by remember(agreement) { mutableStateOf(agreement?.lamaPorcentajeEmpresa.orEmpty()) }
    var typeMenu by remember { mutableStateOf(false) }
    var currencyMenu by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val formOptions = remember(options, agreement) {
        options.copy(
            tipos = if (agreement != null && options.tipos.none { it.id == agreement.tipoParticipacionId }) {
                options.tipos + TipoParticipacionOpcion(agreement.tipoParticipacionId, agreement.tipoParticipacion ?: "Tipo inactivo")
            } else options.tipos,
            monedas = if (agreement != null && options.monedas.none { it.id == agreement.monedaId }) {
                options.monedas + MonedaAcuerdoOpcion(agreement.monedaId, agreement.moneda ?: "", agreement.moneda ?: "Moneda actual", false)
            } else options.monedas,
        )
    }
    val selectedType = formOptions.tipos.firstOrNull { it.id == typeId }
    val selectedCurrency = formOptions.monedas.firstOrNull { it.id == currencyId }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (agreement == null) "Nuevo acuerdo" else "Editar acuerdo") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tipo de participación", color = AgreementInk, fontWeight = FontWeight.Medium)
                SelectionMenu(selectedType?.nombre ?: "Seleccione un tipo", typeMenu, { typeMenu = it }) {
                    formOptions.tipos.forEach { item -> DropdownMenuItem(text = { Text(item.nombre) }, onClick = { typeId = item.id; typeMenu = false }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Acuerdo activo")
                }
                Text("Modalidad", color = AgreementInk, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = modality == "porcentaje", onClick = { modality = "porcentaje" })
                    Text("Porcentajes", modifier = Modifier.clickable { modality = "porcentaje" })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = modality == "tarifa", onClick = { modality = "tarifa" })
                    Text("Tarifa de servicio", modifier = Modifier.clickable { modality = "tarifa" })
                }
                if (modality == "porcentaje") {
                    NumberField("Porcentaje del cliente", clientPercent, { clientPercent = it }, percent = true)
                    NumberField("Porcentaje de la empresa", companyPercent, { companyPercent = it }, percent = true)
                    Text("Los porcentajes deben sumar exactamente 100%. Se usa la moneda base.", color = AgreementMuted, fontSize = 12.sp)
                } else {
                    NumberField("Tarifa de servicio", fee, { fee = it })
                    Text("Moneda", color = AgreementInk, fontWeight = FontWeight.Medium)
                    SelectionMenu(selectedCurrency?.let { "${it.codigo} — ${it.nombre}" } ?: "Seleccione una moneda", currencyMenu, { currencyMenu = it }) {
                        formOptions.monedas.forEach { item -> DropdownMenuItem(text = { Text("${item.codigo} — ${item.nombre}") }, onClick = { currencyId = item.id; currencyMenu = false }) }
                    }
                }
                Text("Lama después del servicio", color = AgreementTeal, fontWeight = FontWeight.SemiBold)
                Text("Indique quién conserva el material y cómo se reparte el oro que se obtenga de esa lama. Es independiente de la tarifa y del oro original.", color = AgreementMuted, fontSize = 12.sp)
                listOf("sin_definir" to "Todavía no acordado", "empresa" to "La empresa conserva la lama", "cliente" to "El cliente conserva la lama").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = lamaDestino == value, onClick = { lamaDestino = value })
                        Text(label, Modifier.clickable { lamaDestino = value })
                    }
                }
                if (lamaDestino != "sin_definir") {
                    NumberField("Oro de la lama: % empresa", lamaEmpresa, { lamaEmpresa = it }, percent = true)
                    Text("100 = todo para la empresa; 0 = todo para el cliente. El resto corresponde al cliente. Después registre los lotes desde Lama y recuperación posterior. Los lotes anteriores conservan su reparto.", color = AgreementMuted, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it.take(5000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Observaciones") },
                    minLines = 2,
                    maxLines = 4,
                )
                (validationError ?: error)?.let { Text(it, color = Color(0xFFB33A3A), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val chosenType = typeId
                val chosenCurrency = if (modality == "porcentaje") options.monedaBaseId ?: currencyId else currencyId
                val client = clientPercent.toDoubleOrNull()
                val company = companyPercent.toDoubleOrNull()
                val service = fee.toDoubleOrNull()
                validationError = when {
                    chosenType == null -> "Seleccione un tipo de participación."
                    modality == "porcentaje" && (client == null || company == null) -> "Complete ambos porcentajes."
                    modality == "porcentaje" && kotlin.math.abs(client!! + company!! - 100.0) > 0.0001 -> "Los porcentajes deben sumar 100%."
                    modality == "tarifa" && (service == null || service <= 0.0) -> "La tarifa debe ser mayor que cero."
                    chosenCurrency == null -> "No hay una moneda disponible para el acuerdo."
                    lamaDestino != "sin_definir" && (lamaEmpresa.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..100.0 } != true) -> "Indique el porcentaje de oro de la lama para la empresa, de 0 a 100."
                    else -> null
                }
                if (validationError == null && chosenType != null && chosenCurrency != null) {
                    onSave(
                        agreement?.id,
                        chosenType,
                        if (modality == "porcentaje") client else null,
                        if (modality == "porcentaje") company else null,
                        if (modality == "tarifa") service else null,
                        chosenCurrency,
                        observations,
                        active,
                        lamaDestino,
                        if (lamaDestino == "sin_definir") null else lamaEmpresa.toDoubleOrNull(),
                    )
                }
            }) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, percent: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.matches(Regex("^\\d{0,3}(\\.\\d{0,4})?$"))) onValueChange(next) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = if (percent) ({ Text("%") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun SelectionMenu(label: String, expanded: Boolean, onExpanded: (Boolean) -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onExpanded(true) },
            color = Color(0xFFF7F9FA),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD7E0E3)),
        ) { Text(label, Modifier.padding(14.dp), color = AgreementInk) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }, content = content)
    }
}

@Composable
private fun AgreementCard(
    agreement: AcuerdoOrdenTrabajo,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(agreement.tipoParticipacion ?: "Tipo de participación", color = AgreementInk, fontWeight = FontWeight.SemiBold)
                Text(if (agreement.activo) "Activo" else "Inactivo", color = if (agreement.activo) AgreementTeal else AgreementMuted, fontSize = 12.sp)
            }
            if (agreement.modalidad == "tarifa") Text("Tarifa de servicio: ${agreement.tarifaServicio ?: "—"} ${agreement.moneda.orEmpty()}", color = AgreementInk)
            else Text("Cliente: ${agreement.porcentajeCliente ?: "—"}% · Empresa: ${agreement.porcentajeEmpresa ?: "—"}%", color = AgreementInk)
            agreement.observaciones?.takeIf(String::isNotBlank)?.let { Text(it, color = AgreementMuted, fontSize = 13.sp) }
            if (agreement.lamaDestino != "sin_definir") Text("Lama: conserva ${agreement.lamaDestino} · Oro posterior empresa ${agreement.lamaPorcentajeEmpresa ?: "pendiente"}% / cliente ${agreement.lamaPorcentajeEmpresa?.toDoubleOrNull()?.let { 100 - it } ?: "pendiente"}%", color = AgreementTeal, fontSize = 13.sp)
            if (canEdit || canDelete) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (canEdit) TextButton(onClick = onEdit) { Text("Editar", color = AgreementTeal) }
                    if (canDelete) TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFB33A3A)) }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, error: Boolean = false) {
    Surface(color = if (error) Color(0xFFFFEEEE) else Color.White, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = if (error) Color(0xFFB33A3A) else AgreementMuted)
    }
}
