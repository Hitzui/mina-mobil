package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.MaterialProcesoTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesMaterialProceso
import com.jomiro.app.mina_mobil.data.ordenes.OrdenesRepository
import com.jomiro.app.mina_mobil.data.ordenes.ProductoMateriaPrima
import com.jomiro.app.mina_mobil.data.ordenes.ProcesoOrdenTrabajo
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val MaterialTeal = Color(0xFF087E83)
private val MaterialInk = Color(0xFF172C3D)
private val MaterialMuted = Color(0xFF71808B)

@Composable
fun MaterialesProcesoScreen(
    ordenId: Long,
    process: ProcesoOrdenTrabajo,
    canCreate: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: OrdenesRepository = koinInject()
    val scope = rememberCoroutineScope()
    var materials by remember(process.id) { mutableStateOf<List<MaterialProcesoTrabajo>>(emptyList()) }
    var options by remember(process.id) { mutableStateOf<OpcionesMaterialProceso?>(null) }
    var loading by remember(process.id) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MaterialProcesoTrabajo?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                materials = repository.listarMaterialesProceso(ordenId, process.id)
                if (canCreate) options = repository.opcionesMaterialProceso(ordenId, process.id)
            }.onFailure { error = it.message ?: "No se pudieron cargar los consumos." }
            loading = false
        }
    }
    LaunchedEffect(ordenId, process.id) { load() }

    if (showForm && options != null) {
        MaterialFormDialog(
            options = options!!,
            saving = saving,
            error = error,
            onDismiss = { if (!saving) { showForm = false; error = null } },
            onSave = { product, date, quantity, observations ->
                scope.launch {
                    saving = true
                    error = null
                    runCatching { repository.registrarMaterialProceso(ordenId, process.id, product.id, date, quantity, observations) }
                        .onSuccess {
                            materials = listOf(it) + materials
                            options = repository.opcionesMaterialProceso(ordenId, process.id)
                            showForm = false
                        }
                        .onFailure { error = it.message ?: "No se pudo registrar el consumo." }
                    saving = false
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleteTarget = null },
            title = { Text("Eliminar consumo") },
            text = { Text("¿Desea devolver ${target.cantidad.materialNumber()} ${target.unidad.orEmpty()} de ${target.producto} al almacén?") },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        runCatching { repository.eliminarMaterialProceso(ordenId, process.id, target) }
                            .onSuccess {
                                materials = materials.filterNot { it.id == target.id }
                                options = if (canCreate) repository.opcionesMaterialProceso(ordenId, process.id) else options
                                deleteTarget = null
                            }
                            .onFailure { error = it.message ?: "No se pudo eliminar el consumo." }
                        saving = false
                    }
                }) { Text("Eliminar", color = Color(0xFFB33A3A)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver", color = MaterialTeal) }
            Column(Modifier.weight(1f)) {
                Text("Materia prima consumida", color = MaterialInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("${process.codigo} · ${process.etapa.orEmpty()}", color = MaterialMuted, fontSize = 13.sp)
            }
            if (canCreate && process.estado != "Finalizado" && process.estado != "Cancelado") {
                Button(
                    onClick = { error = null; showForm = true },
                    enabled = options?.productos?.isNotEmpty() != false,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTeal),
                ) { Text("+ Registrar consumo") }
            }
        }
        Surface(color = Color(0xFFF8F9FA), shape = RoundedCornerShape(12.dp)) {
            Text(
                "Cemento, químicos, reactivos: lo que se gasta en este proceso. Al registrar el consumo el material se descuenta del almacén y su costo entra al total de este proceso con el valor del costo promedio del almacén, no con un precio escrito aquí. Si el material aún no tiene costo cargado, el consumo sumará cero.",
                modifier = Modifier.padding(14.dp), color = MaterialMuted, fontSize = 13.sp,
            )
        }
        error?.takeIf { !showForm }?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 13.sp) }
        when {
            loading -> CircularProgressIndicator(color = MaterialTeal, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            materials.isEmpty() -> Text("No hay consumos registrados en este proceso.", color = MaterialMuted, modifier = Modifier.padding(vertical = 16.dp))
            else -> materials.forEach { material ->
                MaterialCard(material, canDelete) { deleteTarget = material }
            }
        }
    }
}

@Composable
private fun MaterialCard(material: MaterialProcesoTrabajo, canDelete: Boolean, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(material.producto, color = MaterialInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(material.fecha, color = MaterialMuted, fontSize = 12.sp)
            }
            Text("Cantidad: ${material.cantidad.materialNumber()} ${material.unidad.orEmpty()}", color = MaterialMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Costo unitario: NIO ${material.costoUnitario.moneyNumber()}", color = MaterialMuted, fontSize = 12.sp)
                Text("Total NIO: ${material.totalNio?.moneyNumber() ?: "—"}", color = MaterialTeal, fontWeight = FontWeight.SemiBold)
            }
            material.observaciones?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (canDelete) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("Eliminar", color = Color(0xFFB33A3A)) }
            }
        }
    }
}

@Composable
private fun MaterialFormDialog(
    options: OpcionesMaterialProceso,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (ProductoMateriaPrima, String, Double, String?) -> Unit,
) {
    var productId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(options.fecha) }
    var quantity by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val product = options.productos.firstOrNull { it.id == productId }
    val qty = quantity.toDoubleOrNull()
    val available = product?.existencia ?: 0.0
    val valid = product != null && date.length >= 10 && qty != null && qty > 0 && qty <= available + 0.0005

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar consumo") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectMaterialField("Material", product?.let { "${it.codigo} · ${it.nombre} (${it.unidad})" } ?: "Seleccione", menuExpanded, { menuExpanded = !menuExpanded }) {
                    options.productos.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.codigo} · ${item.nombre} (${item.unidad}) · ${item.existencia.materialNumber()} disponible") },
                            onClick = { productId = item.id; quantity = ""; menuExpanded = false },
                        )
                    }
                }
                product?.let {
                    Text("Disponible: ${available.materialNumber()} ${it.unidad}", color = MaterialMuted, fontSize = 12.sp)
                    Text("Costo promedio: NIO ${it.costoPromedio.moneyNumber()} por ${it.unidad}", color = MaterialMuted, fontSize = 12.sp)
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (product != null && qty != null && qty > 0) {
                    Text("Costo aproximado: NIO ${(qty * product.costoPromedio).moneyNumber()}", color = MaterialTeal, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(value = observations, onValueChange = { observations = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
                error?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !saving, onClick = {
                onSave(product!!, date, qty!!, observations.takeIf(String::isNotBlank))
            }) { if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp) else Text("Registrar", color = MaterialTeal) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SelectMaterialField(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick))
        DropdownMenu(expanded = expanded, onDismissRequest = onClick, content = content)
    }
}

private fun Double.materialNumber(): String = ((this * 1000).toLong() / 1000.0).toString()
private fun Double.moneyNumber(): String = ((this * 100).toLong() / 100.0).toString()
