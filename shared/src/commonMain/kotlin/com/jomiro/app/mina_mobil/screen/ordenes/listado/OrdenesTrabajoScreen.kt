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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.DateSelectionDialog
import com.jomiro.app.mina_mobil.screen.ordenes.componentes.toDisplayDate
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
    canCreate: Boolean = false,
    canEdit: Boolean = false,
    onShowOrder: (Long) -> Unit,
    onEditOrder: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrdenesTrabajoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(userId) { viewModel.cargarParaUsuario(userId) }
    var showCreateOrder by remember { mutableStateOf(false) }
    LaunchedEffect(state.ordenCreadaId) {
        state.ordenCreadaId?.let { orderId ->
            showCreateOrder = false
            onShowOrder(orderId)
            viewModel.limpiarResultadoCreacion()
        }
    }

    Column(modifier.fillMaxSize()) {
        if (showCreateOrder) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CreateOrderForm(
                    state = state,
                    onClose = {
                        showCreateOrder = false
                        viewModel.limpiarResultadoCreacion()
                    },
                    onRetryOptions = viewModel::cargarOpcionesFormulario,
                    onCreate = viewModel::crearOrden,
                )
            }
        } else {
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
                        onShowOrder = onShowOrder,
                        canEditOrder = canEdit,
                        onEditOrder = onEditOrder,
                    )
                    VistaOrdenes.CALENDARIO -> OrdersCalendar(
                        state = state,
                        onPreviousMonth = { viewModel.cambiarMes(-1) },
                        onNextMonth = { viewModel.cambiarMes(1) },
                        onSelectDate = viewModel::seleccionarFecha,
                        onRetry = viewModel::reintentarCalendario,
                        onShowOrder = onShowOrder,
                        canEditOrder = canEdit,
                        onEditOrder = onEditOrder,
                    )
                }
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
                if (canCreate) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.cargarOpcionesFormulario()
                            showCreateOrder = true
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        containerColor = Teal,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("+", fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
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
}

@Composable
private fun CreateOrderForm(
    state: OrdenesTrabajoUiState,
    onClose: () -> Unit,
    onRetryOptions: () -> Unit,
    onCreate: (Long, String, Double, Long, String) -> Unit,
) {
    val today = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedUnitId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(today.toString()) }
    var weight by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClientPicker by remember { mutableStateOf(false) }
    var showUnitPicker by remember { mutableStateOf(false) }
    val options = state.opcionesFormulario
    LaunchedEffect(options) {
        if (selectedClientId == null) selectedClientId = options?.clientes?.firstOrNull()?.id
        if (selectedUnitId == null) selectedUnitId = options?.unidades?.firstOrNull()?.id
    }
    val numericWeight = weight.trim().replace(',', '.').toDoubleOrNull()
    val canSubmit = options != null && selectedClientId != null && selectedUnitId != null &&
        numericWeight != null && numericWeight > 0 && date.isNotBlank() && !state.creandoOrden &&
        !state.cargandoOpcionesFormulario

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹  Volver", color = Teal) }
            Text("Nueva orden", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Datos de la orden", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { showClientPicker = true },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = Ink),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Cliente", color = Muted, fontSize = 11.sp)
                        Text(options?.clientes?.firstOrNull { it.id == selectedClientId }?.nombre ?: "Seleccionar cliente", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = Ink),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Fecha", color = Muted, fontSize = 11.sp)
                            Text(date.toDisplayDate(), color = Ink)
                        }
                        Text("▦", color = Teal, fontSize = 18.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { character -> character.isDigit() || character == '.' || character == ',' } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Peso mineral") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = filterFieldColors(),
                    )
                    Button(
                        onClick = { showUnitPicker = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F6), contentColor = Ink),
                    ) {
                        Text(options?.unidades?.firstOrNull { it.id == selectedUnitId }?.nombre ?: "Unidad", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1000) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descripción (opcional)") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = filterFieldColors(),
                )
                if (state.cargandoOpcionesFormulario) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Teal, strokeWidth = 2.dp)
                        Text("Cargando clientes y unidades…", color = Muted, fontSize = 13.sp)
                    }
                }
                state.errorFormulario?.let { Text(it, color = Color(0xFFB33A3A), fontSize = 13.sp) }
                if (options != null && (options.clientes.isEmpty() || options.unidades.isEmpty())) {
                    Text("Se necesita al menos un cliente y una unidad de medida activa para crear la orden.", color = Muted, fontSize = 13.sp)
                }
                if (state.errorFormulario != null && options == null) {
                    TextButton(onClick = onRetryOptions, modifier = Modifier.align(Alignment.End)) { Text("Reintentar", color = Teal) }
                }
                Button(
                    onClick = {
                        val parsedWeight = numericWeight ?: return@Button
                        onCreate(selectedClientId ?: return@Button, date, parsedWeight, selectedUnitId ?: return@Button, description)
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.creandoOrden) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Crear orden")
                }
            }
        }
    }
    if (showDatePicker) DateSelectionDialog(
        initialDate = date,
        onSelect = { date = it.toString(); showDatePicker = false },
        onDismiss = { showDatePicker = false },
    )
    if (showClientPicker && options != null) OrderOptionDialog(
        title = "Seleccionar cliente",
        options = options.clientes.map { it.id to it.nombre },
        onSelect = { selectedClientId = it; showClientPicker = false },
        onDismiss = { showClientPicker = false },
    )
    if (showUnitPicker && options != null) OrderOptionDialog(
        title = "Seleccionar unidad",
        options = options.unidades.map { it.id to it.nombre },
        onSelect = { selectedUnitId = it; showUnitPicker = false },
        onDismiss = { showUnitPicker = false },
    )
}

@Composable
private fun OrderOptionDialog(
    title: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ComposablesTheme {
        ComposablesAlertDialog(visible = true, onDismissRequest = onDismiss, paneTitle = title) {
            Column {
                Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.height(260.dp).padding(top = 8.dp)) {
                    items(options, key = { it.first }) { (id, label) ->
                        TextButton(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, color = Ink, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (options.isEmpty()) item { Text("No hay opciones disponibles.", color = Muted, modifier = Modifier.padding(12.dp)) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar", color = Teal) }
            }
        }
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
    onShowOrder: (Long) -> Unit,
    canEditOrder: Boolean,
    onEditOrder: (Long) -> Unit,
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
            items(state.ordenes, key = OrdenTrabajo::id) { order ->
                OrderCard(order, onShowOrder, if (canEditOrder) onEditOrder else null)
            }
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
    onShowOrder: (Long) -> Unit,
    canEditOrder: Boolean,
    onEditOrder: (Long) -> Unit,
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
                else selectedOrders.forEach { order ->
                    OrderCard(order, onShowOrder, if (canEditOrder) onEditOrder else null)
                }
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
private fun OrderCard(order: OrdenTrabajo, onShowOrder: (Long) -> Unit, onEditOrder: ((Long) -> Unit)?) {
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
                TextButton(onClick = { onShowOrder(order.id) }) {
                    Text("◉  Ver", color = Teal, fontWeight = FontWeight.SemiBold)
                }
                if (onEditOrder != null) {
                    TextButton(enabled = order.tipoTrabajo != "tratamiento_lama", onClick = { onEditOrder(order.id) }) {
                        Text("✎  Editar", color = Teal, fontWeight = FontWeight.SemiBold)
                    }
                }
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
