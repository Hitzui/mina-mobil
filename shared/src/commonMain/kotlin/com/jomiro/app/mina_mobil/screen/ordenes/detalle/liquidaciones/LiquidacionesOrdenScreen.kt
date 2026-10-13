package com.jomiro.app.mina_mobil.screen.ordenes.detalle.liquidaciones

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoLiquidacionOpcion
import com.jomiro.app.mina_mobil.data.ordenes.LiquidacionOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesLiquidacionOrden
import kotlin.math.roundToInt

private val LiquidationInk = Color(0xFF172C3D)
private val LiquidationTeal = Color(0xFF087E83)
private val LiquidationMuted = Color(0xFF71808B)

@Composable
fun LiquidacionesOrdenScreen(
    orderCode: String,
    liquidations: List<LiquidacionOrdenTrabajo>,
    options: OpcionesLiquidacionOrden?,
    loadingOptions: Boolean,
    saving: Boolean,
    deleting: Boolean,
    saved: Boolean,
    error: String?,
    canCreate: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onLoadOptions: () -> Unit,
    onSave: (LiquidacionOrdenTrabajo?, String, Double, AcuerdoLiquidacionOpcion?, String) -> Unit,
    onDelete: (LiquidacionOrdenTrabajo) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<LiquidacionOrdenTrabajo?>(null) }
    var showingForm by remember { mutableStateOf(false) }
    var deletingLiquidation by remember { mutableStateOf<LiquidacionOrdenTrabajo?>(null) }
    LaunchedEffect(saved) {
        if (saved) {
            showingForm = false
            deletingLiquidation = null
            onClearMessage()
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(orderCode, color = LiquidationMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Liquidaciones de oro", color = LiquidationInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Lotes liquidados y distribución de gramos según el acuerdo aplicado.", color = LiquidationMuted, fontSize = 13.sp)
            }
            if (canCreate) Button(
                onClick = { editing = null; onLoadOptions(); showingForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = LiquidationTeal),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Nueva") }
        }
        error?.let { LiquidationMessage(it, isError = true) }
        if (liquidations.isEmpty()) LiquidationMessage("No hay liquidaciones registradas para esta orden.")
        liquidations.forEach { item ->
            LiquidationCard(
                item,
                canEdit = canEdit,
                canDelete = canDelete,
                onEdit = { editing = item; showingForm = true },
                onDelete = { deletingLiquidation = item },
            )
        }
    }

    if (showingForm) {
        if (loadingOptions) AlertDialog(
            onDismissRequest = { showingForm = false },
            title = { Text("Liquidación de oro") },
            text = { Text("Cargando acuerdos aplicables…") },
            confirmButton = { TextButton(onClick = { showingForm = false }) { Text("Cerrar") } },
        ) else if (options != null || editing != null) LiquidationFormDialog(
            liquidation = editing,
            options = options ?: OpcionesLiquidacionOrden(editing?.fecha.orEmpty(), emptyList()),
            saving = saving,
            error = error,
            onDismiss = { showingForm = false; onClearMessage() },
            onSave = onSave,
        ) else if (error != null) AlertDialog(
            onDismissRequest = { showingForm = false },
            title = { Text("No se pudieron cargar los acuerdos") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { showingForm = false; onClearMessage() }) { Text("Cerrar") } },
        )
    }

    deletingLiquidation?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingLiquidation = null },
            title = { Text("Eliminar liquidación") },
            text = { Text("¿Deseas eliminar el lote del ${item.fecha ?: "fecha desconocida"}?") },
            confirmButton = { TextButton(enabled = !deleting, onClick = { onDelete(item) }) { Text(if (deleting) "Eliminando…" else "Eliminar") } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { deletingLiquidation = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LiquidationFormDialog(
    liquidation: LiquidacionOrdenTrabajo?,
    options: OpcionesLiquidacionOrden,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (LiquidacionOrdenTrabajo?, String, Double, AcuerdoLiquidacionOpcion?, String) -> Unit,
) {
    var date by remember(liquidation, options) { mutableStateOf(liquidation?.fecha ?: options.fecha) }
    var grams by remember(liquidation) { mutableStateOf(liquidation?.gramosRecuperados.orEmpty()) }
    var observations by remember(liquidation) { mutableStateOf(liquidation?.observaciones.orEmpty()) }
    var selectedAgreementId by remember(liquidation, options) { mutableStateOf(options.acuerdos.firstOrNull()?.id) }
    var agreementMenu by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val agreement = options.acuerdos.firstOrNull { it.id == selectedAgreementId }
    val gramsValue = grams.toDoubleOrNull()
    val preview = if (gramsValue != null && gramsValue > 0 && agreement != null) {
        val normalized = gramsValue.roundFour()
        val client = (normalized * agreement.porcentajeCliente / 100.0).roundFour()
        client to (normalized - client).roundFour()
    } else null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (liquidation == null) "Nueva liquidación" else "Editar liquidación") },
        text = {
            Column(Modifier.fillMaxWidth().height(520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    placeholder = { Text("2026-10-12") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = grams,
                    onValueChange = { next -> if (next.matches(Regex("^\\d{0,8}(\\.\\d{0,4})?$"))) grams = next },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gramos del lote") },
                    suffix = { Text("g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (liquidation == null) {
                    Text("Acuerdo porcentual aplicado", color = LiquidationInk, fontWeight = FontWeight.Medium)
                    if (options.acuerdos.isEmpty()) {
                        LiquidationMessage("Agrega un acuerdo porcentual activo a esta orden antes de liquidar.", isError = true)
                    } else {
                        Column {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { agreementMenu = true },
                                color = Color(0xFFF7F9FA),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFD7E0E3)),
                            ) {
                                Text(agreement?.label() ?: "Seleccione un acuerdo", Modifier.padding(14.dp), color = LiquidationInk)
                            }
                            DropdownMenu(expanded = agreementMenu, onDismissRequest = { agreementMenu = false }) {
                                options.acuerdos.forEach { item ->
                                    DropdownMenuItem(text = { Text(item.label()) }, onClick = { selectedAgreementId = item.id; agreementMenu = false })
                                }
                            }
                        }
                    }
                } else {
                    Text("Reparto histórico: ${liquidation.porcentajeCliente ?: "—"}% cliente / ${liquidation.porcentajeEmpresa ?: "—"}% empresa", color = LiquidationMuted)
                }
                Surface(color = Color(0xFFF4F7F8), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Reparto calculado por el sistema", color = LiquidationMuted, fontSize = 12.sp)
                        if (preview != null) {
                            Text("Cliente: ${preview.first} g  ·  Empresa: ${preview.second} g", color = LiquidationInk, fontWeight = FontWeight.SemiBold)
                            Text("Porcentajes guardados como fotografía histórica del acuerdo.", color = LiquidationMuted, fontSize = 12.sp)
                        } else Text("Indique los gramos${if (liquidation == null) " y el acuerdo" else ""} para ver el reparto.", color = LiquidationMuted, fontSize = 13.sp)
                    }
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
            TextButton(enabled = !saving && (liquidation != null || options.acuerdos.isNotEmpty()), onClick = {
                val numericGrams = grams.toDoubleOrNull()
                validationError = when {
                    !date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> "Indique una fecha válida."
                    numericGrams == null || numericGrams <= 0 || numericGrams > 99999999.9999 -> "Los gramos deben ser mayores que cero y no superar el máximo permitido."
                    liquidation == null && agreement == null -> "Seleccione un acuerdo porcentual activo."
                    else -> null
                }
                if (validationError == null && numericGrams != null) {
                    onSave(liquidation, date, numericGrams, if (liquidation == null) agreement else null, observations)
                }
            }) { Text(if (saving) "Guardando…" else "Guardar liquidación") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun LiquidationCard(
    item: LiquidacionOrdenTrabajo,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Liquidación de oro", color = LiquidationInk, fontWeight = FontWeight.SemiBold)
                    Text(item.fecha ?: "Fecha no disponible", color = LiquidationMuted, fontSize = 12.sp)
                }
                Text("${item.gramosRecuperados ?: "—"} g", color = LiquidationTeal, fontWeight = FontWeight.Bold)
            }
            Text("Acuerdo aplicado: ${item.porcentajeCliente ?: "—"}% cliente / ${item.porcentajeEmpresa ?: "—"}% empresa", color = LiquidationMuted, fontSize = 13.sp)
            Text("Cliente: ${item.gramosCliente ?: "—"} g  ·  Empresa: ${item.gramosEmpresa ?: "—"} g", color = LiquidationInk, fontSize = 13.sp)
            item.observaciones?.takeIf(String::isNotBlank)?.let { Text(it, color = LiquidationMuted, fontSize = 13.sp) }
            if (canEdit || canDelete) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (canEdit) TextButton(onClick = onEdit) { Text("Editar", color = LiquidationTeal) }
                if (canDelete) TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFB33A3A)) }
            }
        }
    }
}

@Composable
private fun LiquidationMessage(message: String, isError: Boolean = false) {
    Surface(color = if (isError) Color(0xFFFFEEEE) else Color.White, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(13.dp), color = if (isError) Color(0xFFB33A3A) else LiquidationMuted, fontSize = 13.sp)
    }
}

private fun AcuerdoLiquidacionOpcion.label(): String =
    "${tipoParticipacion ?: "Participación"} (${porcentajeCliente.prettyFour()}% / ${porcentajeEmpresa.prettyFour()}%)"

private fun Double.prettyFour(): String = ((this * 10_000).roundToInt() / 10_000.0).toString()
private fun Double.roundFour(): Double = (this * 10_000).roundToInt() / 10_000.0
