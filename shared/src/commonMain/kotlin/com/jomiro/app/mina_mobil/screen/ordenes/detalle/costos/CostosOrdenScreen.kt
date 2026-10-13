package com.jomiro.app.mina_mobil.screen.ordenes.detalle.costos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.CategoriaCostoOpcion
import com.jomiro.app.mina_mobil.data.ordenes.CostoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.MonedaCostoOpcion
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesCostoOrden
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.DatePickerField
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.toDisplayDate
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val OrderCostBlue = Color(0xFF435FF2)
private val OrderCostInk = Color(0xFF172C3D)
private val OrderCostMuted = Color(0xFF78869A)

@Composable
fun CostosOrdenScreen(
    orderId: Long,
    orderCode: String,
    orderClosed: Boolean,
    canCreate: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    modifier: Modifier = Modifier,
) {
    val repository: OrdenesRepository = koinInject()
    val scope = rememberCoroutineScope()
    var costs by remember(orderId) { mutableStateOf<List<CostoOrdenTrabajo>>(emptyList()) }
    var options by remember(orderId) { mutableStateOf<OpcionesCostoOrden?>(null) }
    var loading by remember(orderId) { mutableStateOf(true) }
    var loadingOptions by remember(orderId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CostoOrdenTrabajo?>(null) }
    var showingForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CostoOrdenTrabajo?>(null) }

    fun loadCosts() {
        scope.launch {
            loading = true
            runCatching { repository.listarCostosOrden(orderId) }
                .onSuccess { costs = it }
                .onFailure { error = it.message ?: "No se pudieron cargar los costos generales." }
            loading = false
        }
    }
    fun loadOptions() {
        if (options != null || loadingOptions) return
        scope.launch {
            loadingOptions = true
            error = null
            runCatching { repository.opcionesCostosOrden(orderId) }
                .onSuccess { options = it }
                .onFailure { error = it.message ?: "No se pudieron cargar las opciones de costos." }
            loadingOptions = false
        }
    }

    LaunchedEffect(orderId) {
        loadCosts()
        if (canCreate || canEdit) loadOptions()
    }

    if (showingForm) {
        when {
            loadingOptions -> Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { showingForm = false; error = null }) { Text("← Volver") }
                Text("Cargando categorías y monedas…", color = OrderCostMuted)
            }
            options != null -> OrderCostFormScreen(
                modifier = modifier,
                cost = editing,
                options = options!!,
                saving = saving,
                error = error,
                onBack = { if (!saving) { showingForm = false; error = null } },
                onSave = { categoryId, date, description, quantity, unitCost, currencyId, observations ->
                    scope.launch {
                        saving = true
                        error = null
                        runCatching {
                            repository.guardarCostoOrden(
                                orderId, editing, categoryId, date, description, quantity,
                                unitCost, currencyId, observations, options!!,
                            )
                        }.onSuccess { saved ->
                            costs = (costs.filterNot { it.id == saved.id } + saved).sortedByDescending { it.fecha }
                            showingForm = false
                            editing = null
                        }.onFailure { error = it.message ?: "No se pudo guardar el costo general." }
                        saving = false
                    }
                },
            )
            error != null -> Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { showingForm = false; error = null }) { Text("← Volver") }
                CostMessage(error.orEmpty(), isError = true)
            }
        }
        return
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(orderCode, color = OrderCostMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Costos generales de la orden", color = OrderCostInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Gastos de toda la orden, sin proceso concreto.", color = OrderCostMuted, fontSize = 13.sp)
            }
            if (canCreate && !orderClosed) Button(
                onClick = { editing = null; error = null; loadOptions(); showingForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = OrderCostBlue), shape = RoundedCornerShape(10.dp),
            ) { Text("+ Registrar") }
        }
        Surface(color = Color(0xFFF7F8FA), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFD9DEE7))) {
            Text(
                "Aquí van alquiler, transporte y gastos sueltos que no pertenecen a un proceso. Los consumos de cada proceso se registran en la pantalla de ese proceso.",
                Modifier.fillMaxWidth().padding(13.dp), color = OrderCostMuted, fontSize = 13.sp,
            )
        }
        error?.let { CostMessage(it, isError = true) }
        when {
            loading -> CircularProgressIndicator(color = OrderCostBlue, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            costs.isEmpty() -> CostMessage("No hay costos generales registrados para esta orden.")
            else -> costs.forEach { cost ->
                OrderCostCard(
                    cost = cost,
                    canEdit = canEdit,
                    canDelete = canDelete,
                    onEdit = { editing = cost; error = null; loadOptions(); showingForm = true },
                    onDelete = { deleting = cost },
                )
            }
        }
        if (costs.isNotEmpty()) {
            val nioValues = costs.mapNotNull { it.totalNio?.toDoubleOrNull() }
            Surface(color = Color(0xFFF5F7FA), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Total de costos generales en córdobas", color = OrderCostMuted, fontSize = 12.sp)
                    Text("NIO ${nioValues.sum().displayCost()}", color = OrderCostInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (nioValues.size < costs.size) Text("Hay costos sin equivalente en córdobas.", color = OrderCostMuted, fontSize = 12.sp)
                }
            }
        }
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleting = null },
            title = { Text("Eliminar costo") },
            text = { Text("¿Desea eliminar ‘${target.descripcion.orEmpty()}’ de esta orden?") },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    runCatching { repository.eliminarCostoOrden(orderId, target) }
                        .onSuccess { costs = costs.filterNot { it.id == target.id }; deleting = null }
                        .onFailure { error = it.message ?: "No se pudo eliminar el costo." }
                    saving = false
                }
            }) { Text(if (saving) "Eliminando…" else "Eliminar", color = Color(0xFFB33A3A)) } },
            dismissButton = { TextButton(enabled = !saving, onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun OrderCostFormScreen(
    modifier: Modifier,
    cost: CostoOrdenTrabajo?,
    options: OpcionesCostoOrden,
    saving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (Long, String, String, Double, Double, Long, String) -> Unit,
) {
    var categoryId by remember(cost, options) { mutableStateOf(cost?.categoriaId?.takeIf { id -> options.categorias.any { it.id == id } } ?: options.categorias.firstOrNull()?.id) }
    var currencyId by remember(cost, options) { mutableStateOf(cost?.monedaId?.takeIf { id -> options.monedas.any { it.id == id } } ?: options.monedas.firstOrNull { it.monedaBase }?.id ?: options.monedas.firstOrNull()?.id) }
    var date by remember(cost, options) { mutableStateOf(cost?.fecha.orEmpty().ifBlank { options.fecha }) }
    var description by remember(cost) { mutableStateOf(cost?.descripcion.orEmpty()) }
    var quantity by remember(cost) { mutableStateOf(cost?.cantidadNumero?.toString() ?: cost?.cantidad ?: "1") }
    var unitCost by remember(cost) { mutableStateOf(cost?.costoUnitario?.toString().orEmpty()) }
    var observations by remember(cost) { mutableStateOf(cost?.observaciones.orEmpty()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var currencyMenu by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val category = options.categorias.firstOrNull { it.id == categoryId }
    val currency = options.monedas.firstOrNull { it.id == currencyId }
    val quantityNumber = quantity.toDoubleOrNull()
    val unitCostNumber = unitCost.toDoubleOrNull()
    val total = if (quantityNumber != null && unitCostNumber != null) quantityNumber * unitCostNumber else null

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !saving, onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Volver") }
        Text(if (cost == null) "Registrar costo" else "Editar costo", color = OrderCostInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Complete los datos del gasto. El total se calcula multiplicando cantidad por costo unitario.", color = OrderCostMuted, fontSize = 13.sp)

        if (options.categorias.isEmpty()) {
            CostMessage("No hay categorías de costo activas disponibles. Actívelas en Configuración.", isError = true)
        } else {
            Text("Categoría", color = OrderCostInk, fontWeight = FontWeight.Medium)
            OrderCostChoice(category?.nombre ?: "Seleccione una categoría", expanded = categoryMenu, onClick = { categoryMenu = true })
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                options.categorias.forEach { option -> DropdownMenuItem(text = { Text(option.nombre) }, onClick = { categoryId = option.id; categoryMenu = false }) }
            }
        }
        DatePickerField(value = date, label = "Fecha", onValueChange = { date = it })
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(255) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Descripción") }, singleLine = true,
        )
        OutlinedTextField(
            value = quantity,
            onValueChange = { next -> if (next.matches(Regex("^\\d{0,12}(\\.\\d{0,4})?$"))) quantity = next },
            modifier = Modifier.fillMaxWidth(), label = { Text("Cantidad") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        OutlinedTextField(
            value = unitCost,
            onValueChange = { next -> if (next.matches(Regex("^\\d{0,12}(\\.\\d{0,4})?$"))) unitCost = next },
            modifier = Modifier.fillMaxWidth(), label = { Text("Costo unitario") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        if (options.monedas.isEmpty()) {
            CostMessage("No hay monedas activas disponibles. Actívelas en Configuración.", isError = true)
        } else {
            Text("Moneda", color = OrderCostInk, fontWeight = FontWeight.Medium)
            OrderCostChoice(currency?.let { "${it.codigo} — ${it.nombre}" } ?: "Seleccione una moneda", expanded = currencyMenu, onClick = { currencyMenu = true })
            DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                options.monedas.forEach { option -> DropdownMenuItem(text = { Text("${option.codigo} — ${option.nombre}") }, onClick = { currencyId = option.id; currencyMenu = false }) }
            }
        }
        Surface(color = Color(0xFFF5F7FA), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Total calculado", color = OrderCostMuted, fontSize = 12.sp)
                Text("${total?.displayCost() ?: "—"} ${currency?.codigo.orEmpty()}", color = OrderCostInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("El total en córdobas se calcula con el tipo de cambio del día del costo.", color = OrderCostMuted, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = observations,
            onValueChange = { observations = it.take(5000) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Observaciones") }, minLines = 2, maxLines = 4,
        )
        (validationError ?: error)?.let { CostMessage(it, isError = true) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = onBack) { Text("Cancelar") }
            Button(
                enabled = !saving && options.categorias.isNotEmpty() && options.monedas.isNotEmpty(),
                onClick = {
                    validationError = when {
                        categoryId == null -> "Seleccione la categoría del costo."
                        !date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> "Seleccione una fecha válida."
                        description.isBlank() -> "Describa el costo."
                        quantityNumber == null || quantityNumber <= 0 -> "La cantidad debe ser mayor que cero."
                        unitCostNumber == null || unitCostNumber < 0 -> "El costo unitario no puede ser negativo."
                        currencyId == null -> "Seleccione la moneda del costo."
                        else -> null
                    }
                    if (validationError == null && categoryId != null && quantityNumber != null && unitCostNumber != null && currencyId != null) {
                        onSave(categoryId!!, date, description, quantityNumber, unitCostNumber, currencyId!!, observations)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrderCostBlue), shape = RoundedCornerShape(10.dp),
            ) { Text(if (saving) "Guardando…" else "Guardar costo") }
        }
    }
}

@Composable
private fun OrderCostChoice(value: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFBCC7D4)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = OrderCostInk, modifier = Modifier.weight(1f))
            Text(if (expanded) "⌃" else "⌄", color = OrderCostMuted)
        }
    }
}

@Composable
private fun OrderCostCard(cost: CostoOrdenTrabajo, canEdit: Boolean, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cost.categoria ?: "Costo", color = OrderCostInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(cost.fecha?.toDisplayDate().orEmpty(), color = OrderCostMuted, fontSize = 12.sp)
            }
            Text(cost.descripcion.orEmpty(), color = OrderCostMuted, fontSize = 13.sp)
            if (cost.cantidad != null && cost.costoUnitario != null) {
                Text("${cost.cantidad} × ${cost.moneda.orEmpty()} ${cost.costoUnitario.displayCost()}", color = OrderCostMuted, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total: ${cost.moneda.orEmpty()} ${cost.total.orEmpty()}", color = OrderCostInk, fontWeight = FontWeight.Medium)
                cost.totalNio?.let { Text("NIO $it", color = OrderCostBlue, fontWeight = FontWeight.SemiBold) }
            }
            cost.observaciones?.takeIf(String::isNotBlank)?.let { Text(it, color = OrderCostMuted, fontSize = 12.sp) }
            if (canEdit || canDelete) Row(Modifier.align(Alignment.End)) {
                if (canEdit) TextButton(onClick = onEdit) { Text("Editar", color = OrderCostBlue) }
                if (canDelete) TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFB33A3A)) }
            }
        }
    }
}

@Composable
private fun CostMessage(message: String, isError: Boolean = false) {
    Surface(color = if (isError) Color(0xFFFFEEEE) else Color.White, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(13.dp), color = if (isError) Color(0xFFB33A3A) else OrderCostMuted, fontSize = 13.sp)
    }
}

private fun Double.displayCost(): String = ((this * 100).toLong() / 100.0).toString()
