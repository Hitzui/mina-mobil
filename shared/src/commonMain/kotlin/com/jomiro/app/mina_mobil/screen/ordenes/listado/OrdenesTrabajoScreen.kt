package com.jomiro.app.mina_mobil.screen.ordenes.listado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.OrdenTrabajo
import com.jomiro.app.mina_mobil.data.clientes.Cliente
import com.composables.ui.components.AlertDialog as ComposablesAlertDialog
import com.composables.ui.theme.ComposablesTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

private val Ink = Color(0xFF172C3D)
private val Teal = Color(0xFF087E83)
private val Muted = Color(0xFF71808B)
private val CalendarWeekdays = listOf("L", "M", "X", "J", "V", "S", "D")
private val MonthNames = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OrdenesTrabajoScreen(
    userId: Long,
    modifier: Modifier = Modifier,
    viewModel: OrdenesTrabajoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(userId) { viewModel.cargarParaUsuario(userId) }
    var showAddOrderNotice by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refrescando,
            onRefresh = viewModel::refrescar,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            when (state.vista) {
                VistaOrdenes.LISTADO -> OrdersList(
                    state = state,
                    onRetry = viewModel::reintentarListado,
                    onLoadMore = viewModel::cargarMas,
                    onCodeChange = viewModel::actualizarFiltroCodigo,
                    onClientChange = viewModel::actualizarFiltroCliente,
                    onFromChange = viewModel::actualizarFiltroFechaDesde,
                    onToChange = viewModel::actualizarFiltroFechaHasta,
                    onApplyFilters = viewModel::aplicarFiltros,
                    onClearFilters = viewModel::limpiarFiltros,
                )
                VistaOrdenes.CALENDARIO -> OrdersCalendar(
                    state = state,
                    onPreviousMonth = { viewModel.cambiarMes(-1) },
                    onNextMonth = { viewModel.cambiarMes(1) },
                    onSelectDate = viewModel::seleccionarFecha,
                    onRetry = viewModel::reintentarCalendario,
                )
            }
        }

        NavigationBar(
            containerColor = Color.White,
            contentColor = Teal,
            tonalElevation = 3.dp,
        ) {
            NavigationBarItem(
                selected = state.vista == VistaOrdenes.LISTADO,
                onClick = { viewModel.mostrarVista(VistaOrdenes.LISTADO) },
                icon = { Text("☷", fontSize = 21.sp) },
                label = { Text("Listado") },
                alwaysShowLabel = true,
            )
            Box(Modifier.width(72.dp).height(72.dp), contentAlignment = Alignment.TopCenter) {
                FloatingActionButton(
                    onClick = { showAddOrderNotice = true },
                    modifier = Modifier.padding(top = 2.dp),
                    containerColor = Teal,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                ) {
                    Text("+", fontSize = 32.sp, fontWeight = FontWeight.Light)
                }
            }
            NavigationBarItem(
                selected = state.vista == VistaOrdenes.CALENDARIO,
                onClick = { viewModel.mostrarVista(VistaOrdenes.CALENDARIO) },
                icon = { Text("▦", fontSize = 20.sp) },
                label = { Text("Calendario") },
                alwaysShowLabel = true,
            )
        }
    }
    if (showAddOrderNotice) {
        MaterialAlertDialog(
            onDismissRequest = { showAddOrderNotice = false },
            title = { Text("Nueva orden") },
            text = { Text("El formulario para registrar una orden se habilitará en el siguiente paso.") },
            confirmButton = { TextButton(onClick = { showAddOrderNotice = false }) { Text("Entendido") } },
        )
    }
}

@Composable
private fun OrdersList(
    state: OrdenesTrabajoUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onCodeChange: (String) -> Unit,
    onClientChange: (Long?) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,
) {
    when {
        state.cargando && state.ordenes.isEmpty() -> LoadingOrders()
        state.error != null && state.ordenes.isEmpty() -> ErrorOrders(state.error, onRetry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OrderFilters(state, onCodeChange, onClientChange, onFromChange, onToChange, onApplyFilters, onClearFilters)
                Text("${state.total} ${if (state.total == 1) "orden" else "órdenes"}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
            }
            if (state.error != null) item { Text(state.error, color = Color(0xFFB33A3A)) }
            if (state.ordenes.isEmpty()) item { EmptyOrders("No se encontraron órdenes con esos filtros.") }
            items(state.ordenes, key = OrdenTrabajo::id) { order -> OrderCard(order) }
            if (state.hayMas) {
                item {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.cargandoMas,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    ) {
                        if (state.cargandoMas) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Cargar más órdenes")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderFilters(
    state: OrdenesTrabajoUiState,
    onCodeChange: (String) -> Unit,
    onClientChange: (Long?) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    var clientPickerOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(17.dp),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filtrar órdenes", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.filtroCodigo,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Código de orden") },
                shape = RoundedCornerShape(12.dp),
                colors = filterFieldColors(),
            )
            Button(
                onClick = { clientPickerOpen = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = Ink),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cliente", color = Muted, fontSize = 11.sp)
                        Text(state.clientes.firstOrNull { it.id == state.filtroClienteId }?.nombre ?: "Todos los clientes", color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("⌄", color = Teal, fontSize = 20.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateFilterButton("Fecha desde", state.filtroFechaDesde, onFromChange, Modifier.weight(1f))
                DateFilterButton("Fecha hasta", state.filtroFechaHasta, onToChange, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClear) { Text("Limpiar", color = Muted) }
                Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Buscar") }
            }
        }
    }
    if (clientPickerOpen) {
        ClientSelectionDialog(
            clients = state.clientes,
            selectedId = state.filtroClienteId,
            onSelect = { onClientChange(it); clientPickerOpen = false },
            onDismiss = { clientPickerOpen = false },
        )
    }
}

@Composable
private fun DateFilterButton(label: String, value: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Button(
        onClick = { showPicker = true },
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = Ink),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
                Text(value.takeIf(String::isNotBlank)?.toDisplayDate() ?: "Elegir fecha", color = Ink, fontSize = 12.sp, maxLines = 1)
            }
            Text("▦", color = Teal, fontSize = 18.sp)
        }
    }
    if (showPicker) {
        DateSelectionDialog(
            initialDate = value.takeIf(String::isNotBlank),
            onSelect = { onDateSelected(it.toString()); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ClientSelectionDialog(
    clients: List<Cliente>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matchingClients = clients.filter { it.nombre.contains(query.trim(), ignoreCase = true) }
    ComposablesTheme {
        ComposablesAlertDialog(
            visible = true,
            onDismissRequest = onDismiss,
            paneTitle = "Seleccionar cliente",
        ) {
            Column {
                Text("Seleccionar cliente", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Buscar cliente") }, shape = RoundedCornerShape(12.dp),
                    colors = filterFieldColors(),
                )
                LazyColumn(Modifier.height(300.dp).padding(top = 8.dp)) {
                    item {
                        TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (selectedId == null) "✓  Todos los clientes" else "Todos los clientes", color = Teal)
                        }
                    }
                    items(matchingClients, key = Cliente::id) { client ->
                        TextButton(onClick = { onSelect(client.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (client.id == selectedId) "✓  ${client.nombre}" else client.nombre, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (matchingClients.isEmpty()) item { Text("No se encontraron clientes.", color = Muted, modifier = Modifier.padding(12.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun DateSelectionDialog(initialDate: String?, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    var selectedDate by remember { mutableStateOf(initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today) }
    var displayedMonth by remember { mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1)) }
    ComposablesTheme {
        ComposablesAlertDialog(
            visible = true,
            onDismissRequest = onDismiss,
            paneTitle = "Seleccionar fecha",
        ) {
            Column {
                Text("Seleccionar fecha", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    MonthButton("‹") { displayedMonth = displayedMonth.minus(1, DateTimeUnit.MONTH) }
                    Text("${MonthNames[displayedMonth.month.ordinal].replaceFirstChar { it.uppercase() }} ${displayedMonth.year}", color = Ink, fontWeight = FontWeight.SemiBold)
                    MonthButton("›") { displayedMonth = displayedMonth.plus(1, DateTimeUnit.MONTH) }
                }
                Row(Modifier.fillMaxWidth()) {
                    CalendarWeekdays.forEach { day ->
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            Text(day, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                val firstDay = LocalDate(displayedMonth.year, displayedMonth.month, 1)
                val daysInMonth = firstDay.daysUntil(firstDay.plus(1, DateTimeUnit.MONTH))
                val calendarDays: List<LocalDate?> = buildList {
                    repeat(firstDay.dayOfWeek.ordinal) { add(null) }
                    for (day in 0 until daysInMonth) add(firstDay.plus(day, DateTimeUnit.DAY))
                    while (size % 7 != 0) add(null)
                }
                calendarDays.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).height(40.dp).padding(2.dp)) {
                                if (date != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize().clickable { selectedDate = date },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (date == selectedDate) Teal else Color.Transparent,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(date.day.toString(), color = if (date == selectedDate) Color.White else Ink, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) }
                    TextButton(onClick = { onSelect(selectedDate) }) { Text("Aceptar", color = Teal) }
                }
            }
        }
    }
}

private fun String.toDisplayDate(): String = runCatching {
    val date = LocalDate.parse(this)
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"
}.getOrElse { this }

@Composable
private fun filterFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal,
    unfocusedBorderColor = Color(0xFFDCE4E7),
    focusedLabelColor = Teal,
)

@Composable
private fun OrdersCalendar(
    state: OrdenesTrabajoUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(17.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(13.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonthButton("‹", onPreviousMonth)
                    Text(
                        "${MonthNames[state.mes.month.ordinal].replaceFirstChar { it.uppercase() }} ${state.mes.year}",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    MonthButton("›", onNextMonth)
                }

                Row(Modifier.fillMaxWidth()) {
                    CalendarWeekdays.forEach { day ->
                        Box(Modifier.weight(1f).height(25.dp), contentAlignment = Alignment.Center) {
                            Text(day, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val firstDay = LocalDate(state.mes.year, state.mes.month, 1)
                val nextMonth = firstDay.plus(1, DateTimeUnit.MONTH)
                val daysInMonth = firstDay.daysUntil(nextMonth)
                val cells: List<LocalDate?> = buildList {
                    repeat(firstDay.dayOfWeek.ordinal) { add(null) }
                    for (day in 0 until daysInMonth) add(firstDay.plus(day, DateTimeUnit.DAY))
                    while (size % 7 != 0) add(null)
                }
                val ordersByDate = state.ordenesDelMes.filter { it.fecha != null }.groupBy { it.fecha }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).height(48.dp).padding(2.dp)) {
                                if (date != null) {
                                    val count = ordersByDate[date.toString()]?.size ?: 0
                                    val isSelected = date == state.fechaSeleccionada
                                    Surface(
                                        modifier = Modifier.fillMaxSize().clickable { onSelectDate(date) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Teal else if (count > 0) Teal.copy(alpha = 0.08f) else Color.Transparent,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(
                                                date.day.toString(),
                                                color = if (isSelected) Color.White else Ink,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || count > 0) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                            if (count > 0) {
                                                Text(
                                                    count.toString(),
                                                    color = if (isSelected) Color.White else Teal,
                                                    fontSize = 9.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        when {
            state.cargandoMes && state.ordenesDelMes.isEmpty() -> LoadingOrders()
            state.errorCalendario != null -> ErrorOrders(state.errorCalendario, onRetry)
            else -> {
                val selectedOrders = state.ordenesDelMes.filter { it.fecha == state.fechaSeleccionada.toString() }
                Text(
                    "${state.fechaSeleccionada.day} de ${MonthNames[state.fechaSeleccionada.month.ordinal]} · " +
                        "${selectedOrders.size} ${if (selectedOrders.size == 1) "orden" else "órdenes"}",
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 9.dp),
                )
                if (selectedOrders.isEmpty()) EmptyOrders("No hay órdenes para este día.")
                else selectedOrders.forEach { OrderCard(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MonthButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF1F5F6),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = Teal, fontSize = 23.sp) }
    }
}

@Composable
private fun OrderCard(order: OrdenTrabajo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(order.codigo, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(order.cliente ?: "Sin cliente", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusBadge(order.codigoEstado, order.estado)
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Fecha: ${formatDate(order.fecha)}", color = Muted, fontSize = 12.sp)
                val weight = order.pesoMineral
                if (weight != null) {
                    Text("Peso: $weight ${order.unidadPeso.orEmpty()}".trim(), color = Muted, fontSize = 12.sp)
                }
            }
            order.descripcion?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    description,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { }) { Text("✎  Editar", color = Teal, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun StatusBadge(code: Int, label: String) {
    val (background, foreground) = when (code) {
        0 -> Color(0xFFFCE8E8) to Color(0xFFB33A3A)
        1 -> Color(0xFFE8EEFF) to Color(0xFF4361EE)
        2 -> Color(0xFFFFF3DC) to Color(0xFF9A6710)
        3 -> Color(0xFFE5F4F0) to Color(0xFF267A5B)
        else -> Color(0xFFF0F2F4) to Muted
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(label, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun LoadingOrders() {
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Teal)
    }
}

@Composable
private fun EmptyOrders(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 25.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorOrders(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Color(0xFFB33A3A), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Reintentar") }
    }
}

private fun formatDate(value: String?): String {
    if (value == null) return "—"
    val parts = value.split('-')
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else value
}
