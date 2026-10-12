package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.CategoriaCostoOpcion
import com.jomiro.app.mina_mobil.data.ordenes.CostoProcesoTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.MonedaCostoOpcion
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import com.jomiro.app.mina_mobil.data.ordenes.ProcesoOrdenTrabajo
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val CostTeal = Color(0xFF087E83)
private val CostInk = Color(0xFF172C3D)
private val CostMuted = Color(0xFF71808B)

@Composable
fun CostosProcesoScreen(
    ordenId: Long,
    process: ProcesoOrdenTrabajo,
    canCreate: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: OrdenesRepository = koinInject()
    val scope = rememberCoroutineScope()
    var costs by remember(process.id) { mutableStateOf<List<CostoProcesoTrabajo>>(emptyList()) }
    var categories by remember(process.id) { mutableStateOf<List<CategoriaCostoOpcion>>(emptyList()) }
    var currencies by remember(process.id) { mutableStateOf<List<MonedaCostoOpcion>>(emptyList()) }
    var defaultDate by remember(process.id) { mutableStateOf("") }
    var loading by remember(process.id) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CostoProcesoTrabajo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CostoProcesoTrabajo?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var formCategoryId by remember { mutableStateOf<Long?>(null) }
    var formCurrencyId by remember { mutableStateOf<Long?>(null) }
    var formDate by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                costs = repository.listarCostosProceso(ordenId, process.id)
                if (canCreate || canEdit) {
                    val options = repository.opcionesCostosProceso(ordenId, process.id)
                    categories = options.categorias
                    currencies = options.monedas
                    defaultDate = options.fecha
                }
            }.onFailure { error = it.message ?: "No se pudieron cargar los costos." }
            loading = false
        }
    }
    LaunchedEffect(ordenId, process.id) { load() }

    if (creating || editing != null) {
        CostFormScreen(
            initial = editing,
            categories = categories,
            currencies = currencies,
            categoryId = formCategoryId,
            currencyId = formCurrencyId,
            date = formDate,
            saving = saving,
            error = error,
            onDismiss = { if (!saving) { creating = false; editing = null; error = null } },
            onPickCategory = { showCategoryPicker = true },
            onPickCurrency = { showCurrencyPicker = true },
            onPickDate = { showDatePicker = true },
            onSave = { categoryId, date, description, quantity, unitCost, currencyId, observations ->
                scope.launch {
                    saving = true
                    error = null
                    runCatching {
                        repository.guardarCostoProceso(
                            ordenId, process.id, editing?.id, categoryId, date, description,
                            quantity, unitCost, currencyId, observations,
                        )
                    }.onSuccess { saved ->
                        costs = (costs.filterNot { it.id == saved.id } + saved).sortedBy { it.fecha }
                        creating = false
                        editing = null
                    }.onFailure { error = it.message ?: "No se pudo guardar el costo." }
                    saving = false
                }
            },
            modifier = modifier,
        )
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Volver", color = CostTeal) }
                Column(Modifier.weight(1f)) {
                    Text("Costos del proceso", color = CostInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("${process.codigo} · ${process.etapa.orEmpty()}", color = CostMuted, fontSize = 13.sp)
                }
                if (canCreate && process.estado != "Finalizado" && process.estado != "Cancelado") {
                    Button(onClick = {
                        formCategoryId = categories.firstOrNull()?.id
                        formCurrencyId = currencies.firstOrNull()?.id
                        formDate = defaultDate
                        creating = true
                    }, colors = ButtonDefaults.buttonColors(containerColor = CostTeal)) {
                        Text("+ Registrar")
                    }
                }
            }
            Surface(color = Color(0xFFF8F9FA), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Aquí van los consumos de este proceso: energía, agua y demás conceptos. La mano de obra, la depreciación y la materia prima no se registran aquí: las calcula el sistema desde los trabajos de empleados, los equipos asignados y el almacén.",
                    modifier = Modifier.padding(14.dp), color = CostMuted, fontSize = 13.sp,
                )
            }
            error?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 13.sp) }
            when {
                loading -> CircularProgressIndicator(color = CostTeal, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                costs.isEmpty() -> Text("No hay costos registrados en este proceso.", color = CostMuted, modifier = Modifier.padding(vertical = 16.dp))
                else -> costs.forEach { cost ->
                    CostCard(
                        cost = cost,
                        canEdit = canEdit,
                        canDelete = canDelete,
                        onEdit = {
                            formCategoryId = cost.categoriaId
                            formCurrencyId = cost.monedaId
                            formDate = cost.fecha.orEmpty()
                            editing = cost
                        },
                        onDelete = { deleting = cost },
                    )
                }
            }
        }
    }

    if (showCategoryPicker) {
        CostOptionPicker(
            title = "Seleccionar categoría",
            options = categories.map { it.id to it.nombre },
            selectedId = formCategoryId,
            onSelect = { id ->
                formCategoryId = id
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
    if (showCurrencyPicker) {
        CostOptionPicker(
            title = "Seleccionar moneda",
            options = currencies.map { it.id to "${it.codigo} · ${it.nombre}" },
            selectedId = formCurrencyId,
            onSelect = { id ->
                formCurrencyId = id
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }
    if (showDatePicker) {
        DateSelectionDialog(
            initialDate = formDate,
            onSelect = { selected ->
                formDate = selected.toString()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleting = null },
            title = { Text("Eliminar costo") },
            text = { Text("¿Desea eliminar ‘${target.descripcion}’ de este proceso?") },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        runCatching { repository.eliminarCostoProceso(ordenId, process.id, target.id) }
                            .onSuccess { costs = costs.filterNot { it.id == target.id }; deleting = null }
                            .onFailure { error = it.message ?: "No se pudo eliminar el costo." }
                        saving = false
                    }
                }) { Text("Eliminar", color = Color(0xFFB33A3A)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }

}

@Composable
private fun CostCard(
    cost: CostoProcesoTrabajo,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cost.categoria, color = CostInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(cost.fecha, color = CostMuted, fontSize = 12.sp)
            }
            Text(cost.descripcion, color = CostMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(6.dp))
            Text("${cost.cantidad.displayCost()} × ${cost.moneda} ${cost.costoUnitario.displayCost()}", color = CostMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total: ${cost.moneda} ${cost.total.displayCost()}", color = CostInk, fontWeight = FontWeight.Medium)
                cost.totalNio?.let { Text("NIO ${it.displayCost()}", color = CostTeal, fontWeight = FontWeight.SemiBold) }
            }
            if (canEdit || canDelete) {
                Row(Modifier.align(Alignment.End)) {
                    if (canEdit) TextButton(onClick = onEdit) { Text("Editar", color = CostTeal) }
                    if (canDelete) TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFB33A3A)) }
                }
            }
        }
    }
}

@Composable
private fun CostFormScreen(
    initial: CostoProcesoTrabajo?,
    categories: List<CategoriaCostoOpcion>,
    currencies: List<MonedaCostoOpcion>,
    categoryId: Long?,
    currencyId: Long?,
    date: String,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPickCategory: () -> Unit,
    onPickCurrency: () -> Unit,
    onPickDate: () -> Unit,
    onSave: (Long, String, String, Double, Double, Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var description by remember(initial?.id) { mutableStateOf(initial?.descripcion.orEmpty()) }
    var quantity by remember(initial?.id) { mutableStateOf(initial?.cantidad?.toString() ?: "1") }
    var unitCost by remember(initial?.id) { mutableStateOf(initial?.costoUnitario?.toString().orEmpty()) }
    var observations by remember(initial?.id) { mutableStateOf(initial?.observaciones.orEmpty()) }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val selectedCurrency = currencies.firstOrNull { it.id == currencyId }
    val quantityNumber = quantity.toDoubleOrNull()
    val unitCostNumber = unitCost.toDoubleOrNull()
    val valid = categoryId != null && currencyId != null && date.length >= 10 && description.isNotBlank() &&
        quantityNumber != null && quantityNumber > 0 && unitCostNumber != null && unitCostNumber >= 0
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("‹ Volver", color = CostTeal) }
            Text(if (initial == null) "Registrar costo" else "Editar costo", color = CostInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        }
        CostSelectButton("Categoría", selectedCategory?.nombre ?: "Seleccionar categoría", onPickCategory)
        CostSelectButton("Fecha", date.ifBlank { "Seleccionar fecha" }, onPickDate)
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 255) description = it },
            label = { Text("Descripción") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = unitCost, onValueChange = { unitCost = it }, label = { Text("Costo unitario") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        CostSelectButton("Moneda", selectedCurrency?.let { "${it.codigo} · ${it.nombre}" } ?: "Seleccionar moneda", onPickCurrency)
        Text("Total estimado: ${selectedCurrency?.codigo.orEmpty()} ${((quantityNumber ?: 0.0) * (unitCostNumber ?: 0.0)).displayCost()}", color = CostTeal, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = observations, onValueChange = { observations = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        error?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar", color = CostMuted) }
            Button(
                enabled = valid && !saving,
                onClick = {
                    onSave(categoryId!!, date, description, quantityNumber!!, unitCostNumber!!, currencyId!!, observations.takeIf(String::isNotBlank))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CostTeal),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Guardar")
            }
        }
    }
}

@Composable
private fun CostSelectButton(label: String, value: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = CostInk),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(label, color = CostMuted, fontSize = 11.sp)
                Text(value, color = CostInk, maxLines = 1)
            }
            Text("⌄", color = CostTeal, fontSize = 18.sp)
        }
    }
}

@Composable
private fun CostOptionPicker(
    title: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (id, label) ->
                    TextButton(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (id == selectedId) "✓  $label" else label, color = if (id == selectedId) CostTeal else CostInk)
                    }
                }
                if (options.isEmpty()) Text("No hay opciones disponibles.", color = CostMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = CostTeal) } },
    )
}

private fun Double.displayCost(): String = ((this * 100).toLong() / 100.0).toString()
