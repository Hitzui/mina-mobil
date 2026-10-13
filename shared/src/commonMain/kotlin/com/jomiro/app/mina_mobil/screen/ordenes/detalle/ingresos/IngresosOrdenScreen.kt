package com.jomiro.app.mina_mobil.screen.ordenes.detalle.ingresos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.jomiro.app.mina_mobil.data.ordenes.IngresoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesIngresoOrden
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.DatePickerField
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.toDisplayDate

private val IncomeInk = Color(0xFF172C3D)
private val IncomeBlue = Color(0xFF435FF2)
private val IncomeMuted = Color(0xFF78869A)

@Composable
fun IngresosOrdenScreen(
    orderCode: String,
    incomes: List<IngresoOrdenTrabajo>,
    options: OpcionesIngresoOrden?,
    loadingOptions: Boolean,
    saving: Boolean,
    deleting: Boolean,
    saved: Boolean,
    error: String?,
    canCreate: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onLoadOptions: () -> Unit,
    onSave: (IngresoOrdenTrabajo?, Long, String, String, Double, Long?, Long, Double, String, OpcionesIngresoOrden) -> Unit,
    onDelete: (IngresoOrdenTrabajo) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<IngresoOrdenTrabajo?>(null) }
    var showingForm by remember { mutableStateOf(false) }
    var deletingIncome by remember { mutableStateOf<IngresoOrdenTrabajo?>(null) }
    LaunchedEffect(saved) {
        if (saved) {
            showingForm = false
            deletingIncome = null
            editing = null
            onClearMessage()
        }
    }

    if (showingForm) {
        when {
            loadingOptions -> Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { showingForm = false; onClearMessage() }) { Text("← Volver") }
                Text("Cargando opciones de ingresos…", color = IncomeMuted)
            }
            options != null -> IncomeFormScreen(
                modifier = modifier,
                income = editing,
                options = options,
                saving = saving,
                error = error,
                onBack = { showingForm = false; onClearMessage() },
                onSave = onSave,
            )
            error != null -> Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { showingForm = false; onClearMessage() }) { Text("← Volver") }
                IncomeMessage(error, isError = true)
            }
        }
        return
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(orderCode, color = IncomeMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Ingresos de la orden", color = IncomeInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Lo que se le ha cobrado al cliente por esta orden.", color = IncomeMuted, fontSize = 13.sp)
            }
            if (canCreate) Button(
                onClick = { editing = null; onLoadOptions(); showingForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = IncomeBlue),
                shape = RoundedCornerShape(10.dp),
            ) { Text("+ Registrar") }
        }
        Surface(color = Color(0xFFF3F5FF), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFDCE1FF))) {
            Text(
                "Aquí van los servicios, la participación y las ventas cobradas por la orden. Los gastos se registran aparte y no se restan de estos ingresos.",
                Modifier.fillMaxWidth().padding(13.dp), color = IncomeMuted, fontSize = 13.sp,
            )
        }
        error?.let { IncomeMessage(it, isError = true) }
        if (incomes.isEmpty()) IncomeMessage("No hay ingresos registrados para esta orden.")
        incomes.forEach { income ->
            IncomeCard(
                income,
                canEdit = canEdit,
                canDelete = canDelete,
                onEdit = { editing = income; onLoadOptions(); showingForm = true },
                onDelete = { deletingIncome = income },
            )
        }
        if (incomes.isNotEmpty()) {
            val nioValues = incomes.mapNotNull { it.totalNio?.toDoubleOrNull() }
            Surface(color = Color(0xFFF5F7FA), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Total facturado en córdobas", color = IncomeMuted, fontSize = 12.sp)
                    Text(moneyText(nioValues.sum()) + " NIO", color = IncomeInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (nioValues.size < incomes.size) {
                        Text("Hay ingresos sin tipo de cambio; no están incluidos en este total.", color = IncomeMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    deletingIncome?.let { income ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingIncome = null },
            title = { Text("Eliminar ingreso") },
            text = { Text("¿Desea eliminar el ingreso del ${income.fecha?.toDisplayDate() ?: "fecha desconocida"}?") },
            confirmButton = { TextButton(enabled = !deleting, onClick = { onDelete(income) }) { Text(if (deleting) "Eliminando…" else "Eliminar") } },
            dismissButton = { TextButton(enabled = !deleting, onClick = { deletingIncome = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun IncomeFormScreen(
    modifier: Modifier,
    income: IngresoOrdenTrabajo?,
    options: OpcionesIngresoOrden,
    saving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (IngresoOrdenTrabajo?, Long, String, String, Double, Long?, Long, Double, String, OpcionesIngresoOrden) -> Unit,
) {
    var typeId by remember(income, options) { mutableStateOf(income?.tipoId?.takeIf { id -> options.tipos.any { it.id == id } } ?: options.tipos.firstOrNull()?.id) }
    var date by remember(income, options) { mutableStateOf(income?.fecha ?: options.fecha) }
    var description by remember(income) { mutableStateOf(income?.descripcion.orEmpty()) }
    var quantity by remember(income) { mutableStateOf(income?.cantidad.orEmpty().ifBlank { "1" }) }
    var unitId by remember(income) { mutableStateOf(income?.unidadId) }
    var currencyId by remember(income, options) { mutableStateOf(income?.monedaId?.takeIf { id -> options.monedas.any { it.id == id } } ?: options.monedas.firstOrNull { it.monedaBase }?.id ?: options.monedas.firstOrNull()?.id) }
    var unitPrice by remember(income) { mutableStateOf(income?.precioUnitario.orEmpty()) }
    var observations by remember(income) { mutableStateOf(income?.observaciones.orEmpty()) }
    var typeMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }
    var currencyMenu by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val type = options.tipos.firstOrNull { it.id == typeId }
    val unit = options.unidades.firstOrNull { it.id == unitId }
    val currency = options.monedas.firstOrNull { it.id == currencyId }
    val amount = quantity.toDoubleOrNull()
    val price = unitPrice.toDoubleOrNull()
    val total = if (amount != null && price != null) (amount * price).roundMoney() else null

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !saving, onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Volver") }
        Text(if (income == null) "Registrar ingreso" else "Editar ingreso", color = IncomeInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Complete los datos del cobro. El total se calcula multiplicando cantidad por precio unitario.", color = IncomeMuted, fontSize = 13.sp)

        if (options.tipos.isEmpty()) {
            IncomeMessage("No hay tipos de ingreso activos disponibles. En Offline, actualice la descarga de datos; en Online, active un tipo en Configuración.", isError = true)
        } else {
            Text("Tipo de ingreso", color = IncomeInk, fontWeight = FontWeight.Medium)
            IncomeChoice(type?.nombre ?: "Seleccione un tipo", expanded = typeMenu, onClick = { typeMenu = true })
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                options.tipos.forEach { option -> DropdownMenuItem(text = { Text(option.nombre) }, onClick = { typeId = option.id; typeMenu = false }) }
            }
        }
        DatePickerField(value = date, label = "Fecha", onValueChange = { date = it })
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(255) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Descripción (opcional)") }, singleLine = true,
        )
        OutlinedTextField(
            value = quantity,
            onValueChange = { next -> if (next.matches(Regex("^\\d{0,12}(\\.\\d{0,4})?$"))) quantity = next },
            modifier = Modifier.fillMaxWidth(), label = { Text("Cantidad") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        Text("Unidad (opcional)", color = IncomeInk, fontWeight = FontWeight.Medium)
        IncomeChoice(unit?.nombre ?: "Sin unidad", expanded = unitMenu, onClick = { unitMenu = true })
        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
            DropdownMenuItem(text = { Text("Sin unidad") }, onClick = { unitId = null; unitMenu = false })
            options.unidades.forEach { option -> DropdownMenuItem(text = { Text(option.nombre) }, onClick = { unitId = option.id; unitMenu = false }) }
        }
        OutlinedTextField(
            value = unitPrice,
            onValueChange = { next -> if (next.matches(Regex("^\\d{0,12}(\\.\\d{0,2})?$"))) unitPrice = next },
            modifier = Modifier.fillMaxWidth(), label = { Text("Precio unitario") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        Text("Moneda", color = IncomeInk, fontWeight = FontWeight.Medium)
        IncomeChoice(currency?.let { "${it.codigo} — ${it.nombre}" } ?: "Seleccione una moneda", expanded = currencyMenu, onClick = { currencyMenu = true })
        DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
            options.monedas.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.codigo} — ${option.nombre}") },
                    onClick = { currencyId = option.id; currencyMenu = false },
                )
            }
        }
        Surface(color = Color(0xFFF5F7FA), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total calculado", color = IncomeMuted, fontSize = 12.sp)
                Text("${total?.let(::moneyText) ?: "—"} ${currency?.codigo.orEmpty()}", color = IncomeInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (currency?.monedaBase == true) "El total en córdobas será el mismo." else "El equivalente en córdobas se calcula con el tipo de cambio vigente para esta fecha.", color = IncomeMuted, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = observations,
            onValueChange = { observations = it.take(5000) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Observaciones") }, minLines = 2, maxLines = 4,
        )
        (validationError ?: error)?.let { IncomeMessage(it, isError = true) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = onBack) { Text("Cancelar") }
            Button(
                enabled = !saving && options.tipos.isNotEmpty(),
                onClick = {
                    validationError = when {
                        typeId == null -> "Seleccione el tipo de ingreso."
                        !date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> "Seleccione una fecha válida."
                        amount == null || amount <= 0 -> "La cantidad debe ser mayor que cero."
                        price == null || price < 0 -> "El precio unitario no puede ser negativo."
                        currencyId == null -> "Seleccione la moneda del ingreso."
                        else -> null
                    }
                    if (validationError == null && typeId != null && amount != null && price != null && currencyId != null) {
                        onSave(income, typeId!!, date, description, amount, unitId, currencyId!!, price, observations, options)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IncomeBlue), shape = RoundedCornerShape(10.dp),
            ) { Text(if (saving) "Guardando…" else "Guardar ingreso") }
        }
    }
}

@Composable
private fun IncomeChoice(value: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFBCC7D4)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = IncomeInk, modifier = Modifier.weight(1f))
            Text(if (expanded) "⌃" else "⌄", color = IncomeMuted)
        }
    }
}

@Composable
private fun IncomeCard(income: IngresoOrdenTrabajo, canEdit: Boolean, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(income.tipo ?: "Ingreso", color = IncomeInk, fontWeight = FontWeight.SemiBold)
                    Text(income.fecha?.toDisplayDate() ?: "Fecha no disponible", color = IncomeMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${income.total ?: "—"} ${income.moneda.orEmpty()}", color = IncomeBlue, fontWeight = FontWeight.Bold)
                    income.totalNio?.let { Text("$it NIO", color = IncomeMuted, fontSize = 12.sp) }
                }
            }
            income.descripcion?.takeIf(String::isNotBlank)?.let { Text(it, color = IncomeMuted, fontSize = 13.sp) }
            if (income.cantidad != null) {
                Text("Cantidad: ${income.cantidad} ${income.unidad.orEmpty()} · Precio unitario: ${income.precioUnitario ?: "—"} ${income.moneda.orEmpty()}", color = IncomeMuted, fontSize = 12.sp)
            }
            income.observaciones?.takeIf(String::isNotBlank)?.let { Text(it, color = IncomeMuted, fontSize = 13.sp) }
            if (canEdit || canDelete) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (canEdit) TextButton(onClick = onEdit) { Text("Editar", color = IncomeBlue) }
                if (canDelete) TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFB33A3A)) }
            }
        }
    }
}

@Composable
private fun IncomeMessage(message: String, isError: Boolean = false) {
    Surface(color = if (isError) Color(0xFFFFEEEE) else Color.White, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(13.dp), color = if (isError) Color(0xFFB33A3A) else IncomeMuted, fontSize = 13.sp)
    }
}

private fun Double.roundMoney(): Double = kotlin.math.round(this * 100) / 100
private fun moneyText(value: Double): String = "%.2f".format(value)
