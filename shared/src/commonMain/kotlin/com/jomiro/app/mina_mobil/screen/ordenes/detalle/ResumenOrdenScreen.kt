package com.jomiro.app.mina_mobil.screen.ordenes.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ordenes.DetalleOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.ProcesoOrdenTrabajo
import kotlin.math.roundToLong

private val SummaryInk = Color(0xFF172C3D)
private val SummaryMuted = Color(0xFF71808B)
private val SummaryTeal = Color(0xFF087E83)

@Composable
fun ResumenOrdenScreen(
    detalle: DetalleOrdenTrabajo,
    puedeVerCostos: Boolean,
    puedeVerIngresos: Boolean,
    modifier: Modifier = Modifier,
) {
    val processCosts = detalle.procesos.associate { process -> process.id to directProcessCost(detalle, process) }
    val directTotal = processCosts.values.sumOf { it.amount }
    val directMissing = processCosts.values.sumOf { it.missing }
    val generalCosts = detalle.costos.filter { it.proceso.isNullOrBlank() }
    val generalTotal = generalCosts.mapNotNull { it.totalNio?.toDoubleOrNull() }.sum()
    val generalMissing = generalCosts.count { it.totalNio?.toDoubleOrNull() == null }
    val incomeTotal = detalle.ingresos.mapNotNull { it.totalNio?.toDoubleOrNull() }.sum()
    val incomeMissing = detalle.ingresos.count { it.totalNio?.toDoubleOrNull() == null }
    val pendingOverhead = if (directMissing == 0 && directTotal > 0 && detalle.procesos.isNotEmpty()) 0.0 else generalTotal
    val allocations = distributeGeneralCosts(detalle.procesos, processCosts, generalTotal, pendingOverhead == 0.0)
    val totalKnown = directTotal + generalTotal
    val statusCounts = detalle.procesos.groupingBy { it.estado }.eachCount()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Resumen operativo y financiero", color = SummaryInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("${detalle.orden.codigo} · ${detalle.orden.cliente ?: "Sin cliente"}", color = SummaryMuted, fontSize = 13.sp)

        if ((puedeVerCostos && (directMissing > 0 || generalMissing > 0 || pendingOverhead > 0)) || (puedeVerIngresos && incomeMissing > 0)) {
            Surface(color = Color(0xFFFFF4D6), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Datos incompletos", color = Color(0xFF795900), fontWeight = FontWeight.Bold)
                    if (puedeVerCostos && directMissing > 0) Text("$directMissing costo(s) de proceso sin equivalente NIO.", color = Color(0xFF795900), fontSize = 13.sp)
                    if (puedeVerCostos && generalMissing > 0) Text("$generalMissing costo(s) general(es) sin equivalente NIO.", color = Color(0xFF795900), fontSize = 13.sp)
                    if (puedeVerIngresos && incomeMissing > 0) Text("$incomeMissing ingreso(s) sin equivalente NIO.", color = Color(0xFF795900), fontSize = 13.sp)
                    if (puedeVerCostos && pendingOverhead > 0) Text(
                        "NIO ${pendingOverhead.displaySummary()} de gastos generales no se pudieron distribuir. Complete los equivalentes NIO de los costos directos o registre costos en los procesos.",
                        color = Color(0xFF795900), fontSize = 13.sp,
                    )
                    Text("Los importes conocidos no representan un total completo mientras haya datos pendientes.", color = Color(0xFF795900), fontSize = 12.sp)
                }
            }
        }

        if (puedeVerIngresos) SummaryMetric("Ingresos registrados", "NIO ${incomeTotal.displaySummary()}", "${detalle.ingresos.size} registro(s)")
        if (puedeVerCostos) {
            SummaryMetric("Costos directos de procesos", "NIO ${directTotal.displaySummary()}", "Mano de obra, equipos, materiales y otros costos")
            SummaryMetric("Costos generales", "NIO ${generalTotal.displaySummary()}", "Incluye $generalMissing registro(s) sin equivalente NIO")
            SummaryMetric("Costos conocidos de la orden", "NIO ${totalKnown.displaySummary()}", "Los gastos generales se cuentan una sola vez")
        }
        detalle.resultadoFinanciero?.let { financial ->
            Text("Rentabilidad de la orden", color = SummaryInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            SummaryMetric(
                "Resultado real conocido · económico, no caja",
                financial.resultadoRealNio?.let { "${financial.moneda} ${it.displaySummary()}" } ?: "Sin ingresos registrados",
                "Ingresos registrados + oro propio valorizado − costo total. No registres ese mismo oro otra vez como ingreso.",
            )
            SummaryMetric(
                "Resultado proyectado según acuerdo",
                financial.resultadoProyectadoNio?.let { "${financial.moneda} ${it.displaySummary()}" } ?: "Sin acuerdo valorable",
                "Tarifa pactada y oro propio liquidado/valorizado − costo total",
            )
            SummaryMetric(
                "Participación de oro de la empresa",
                "${financial.moneda} ${financial.oroEmpresaNio.displaySummary()}",
                "${financial.oroEmpresaGramos.displaySummary()} g · cotización histórica por fecha de liquidación",
            )
            if (financial.datosIncompletos) SummaryNotice(
                "Resultado provisional: hay costos, ingresos, tarifa o liquidaciones pendientes de conversión/valoración o confirmación.",
            )
        }
        SummaryMetric(
            "Avance de procesos",
            "${detalle.procesos.count { it.estado.equals("Finalizado", true) }} de ${detalle.procesos.size} finalizados",
            statusCounts.entries.joinToString(" · ") { "${it.key}: ${it.value}" }.ifBlank { "Sin procesos registrados" },
        )

        Text("Costos y equipos por proceso", color = SummaryInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (detalle.procesos.isEmpty()) SummaryNotice("La orden aún no tiene procesos registrados.")
        detalle.procesos.forEach { process ->
            val costs = processCosts[process.id] ?: ProcessCost(0.0, 0)
            val allocated = allocations[process.id] ?: 0.0
            val equipment = detalle.equipos.filter { it.codigoProceso == process.codigo }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${process.codigo} · ${process.etapa ?: "Proceso"}", color = SummaryInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(process.estado, color = SummaryTeal, fontSize = 12.sp)
                    }
                    Text("${process.fechaInicio?.take(10) ?: "Sin inicio"} – ${process.fechaFin?.take(10) ?: "En curso"}", color = SummaryMuted, fontSize = 12.sp)
                    if (puedeVerCostos) {
                        Text("Directo: NIO ${costs.amount.displaySummary()}", color = SummaryMuted, fontSize = 13.sp)
                        Text("General asignado: NIO ${allocated.displaySummary()}", color = SummaryMuted, fontSize = 13.sp)
                        Text("Total con general: NIO ${(costs.amount + allocated).displaySummary()}", color = SummaryInk, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (equipment.isEmpty()) "Equipos: sin equipo asignado"
                        else "Equipos: " + equipment.joinToString { listOfNotNull(it.codigoEquipo, it.nombreEquipo).joinToString(" · ") },
                        color = SummaryMuted, fontSize = 13.sp,
                    )
                }
            }
        }
        Text("La asignación de gastos generales es informativa: no crea ni duplica movimientos de costo.", color = SummaryMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SummaryMetric(title: String, value: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = SummaryMuted, fontSize = 12.sp)
            Text(value, color = SummaryInk, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(detail, color = SummaryMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SummaryNotice(message: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = SummaryMuted)
    }
}

private data class ProcessCost(val amount: Double, val missing: Int)

private fun directProcessCost(detail: DetalleOrdenTrabajo, process: ProcesoOrdenTrabajo): ProcessCost {
    val works = detail.trabajosEmpleados.filter { it.codigoProceso == process.codigo }
    val equipment = detail.equipos.filter { it.codigoProceso == process.codigo }
    val manualCosts = process.costos
    val materials = process.materiales
    val amount = works.mapNotNull { it.totalNio?.toDoubleOrNull() }.sum() +
        equipment.sumOf { it.costoTotalNio } +
        manualCosts.mapNotNull { it.totalNio }.sum() +
        materials.mapNotNull { it.totalNio }.sum()
    val missing = works.count { it.totalNio?.toDoubleOrNull() == null } +
        manualCosts.count { it.totalNio == null } +
        materials.count { it.totalNio == null }
    return ProcessCost(amount, missing)
}

private fun distributeGeneralCosts(
    processes: List<ProcesoOrdenTrabajo>,
    direct: Map<Long, ProcessCost>,
    totalGeneral: Double,
    complete: Boolean,
): Map<Long, Double> {
    if (!complete || totalGeneral <= 0.0) return processes.associate { it.id to 0.0 }
    val directTotal = direct.values.sumOf { it.amount }
    if (directTotal <= 0.0) return processes.associate { it.id to 0.0 }
    val allocations = processes.associate { process ->
        process.id to roundSummary(totalGeneral * (direct[process.id]?.amount ?: 0.0) / directTotal)
    }.toMutableMap()
    val residual = roundSummary(totalGeneral - allocations.values.sum())
    if (residual != 0.0) {
        val largest = processes.maxByOrNull { direct[it.id]?.amount ?: 0.0 }
        if (largest != null) allocations[largest.id] = roundSummary((allocations[largest.id] ?: 0.0) + residual)
    }
    return allocations
}

private fun roundSummary(value: Double): Double = (value * 100.0).roundToLong() / 100.0
private fun Double.displaySummary(): String = roundSummary(this).toString()
