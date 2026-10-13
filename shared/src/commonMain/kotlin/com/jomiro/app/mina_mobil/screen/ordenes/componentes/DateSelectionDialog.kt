package com.jomiro.app.mina_mobil.screen.ordenes.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.ui.components.AlertDialog as ComposablesAlertDialog
import com.composables.ui.theme.ComposablesTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private val DateInk = Color(0xFF172C3D)
private val DateTeal = Color(0xFF087E83)
private val DateMuted = Color(0xFF71808B)
private val weekdays = listOf("L", "M", "X", "J", "V", "S", "D")
private val months = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

@Composable
fun DateSelectionDialog(initialDate: String?, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var selectedDate by remember { mutableStateOf(initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today) }
    var displayedMonth by remember { mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1)) }
    ComposablesTheme {
        ComposablesAlertDialog(visible = true, onDismissRequest = onDismiss, paneTitle = "Seleccionar fecha") {
            Column {
                Text("Seleccionar fecha", color = DateInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    MonthButton("‹") { displayedMonth = displayedMonth.minus(1, DateTimeUnit.MONTH) }
                    Text("${months[displayedMonth.month.ordinal].replaceFirstChar { it.uppercase() }} ${displayedMonth.year}", color = DateInk, fontWeight = FontWeight.SemiBold)
                    MonthButton("›") { displayedMonth = displayedMonth.plus(1, DateTimeUnit.MONTH) }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { day ->
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            Text(day, color = DateMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                        color = if (date == selectedDate) DateTeal else Color.Transparent,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(date.day.toString(), color = if (date == selectedDate) Color.White else DateInk, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = DateMuted) }
                    TextButton(onClick = { onSelect(selectedDate) }) { Text("Aceptar", color = DateTeal) }
                }
            }
        }
    }
}

@Composable
private fun MonthButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = DateTeal, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}
