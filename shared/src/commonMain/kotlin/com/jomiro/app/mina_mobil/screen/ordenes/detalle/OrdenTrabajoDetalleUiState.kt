package com.jomiro.app.mina_mobil.screen.ordenes.detalle

import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.AcuerdoLiquidacionOpcion
import com.jomiro.app.mina_mobil.data.ordenes.DepreciacionEquipoProceso
import com.jomiro.app.mina_mobil.data.ordenes.DetalleOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.EtapaOpcionProceso
import com.jomiro.app.mina_mobil.data.ordenes.FormularioEdicionOrden
import com.jomiro.app.mina_mobil.data.ordenes.LiquidacionOrdenTrabajo
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesAcuerdoOrden
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesEquipoProceso
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesLiquidacionOrden
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesIngresoOrden
import com.jomiro.app.mina_mobil.data.ordenes.OpcionesTrabajoEmpleado
import com.jomiro.app.mina_mobil.data.ordenes.TarifaTrabajoEmpleado

data class OrdenTrabajoDetalleUiState(
    val detalle: DetalleOrdenTrabajo? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val finalizandoOrden: Boolean = false,
    val finalizacionExitosa: Boolean = false,
    val errorFinalizacion: String? = null,
    val etapasProceso: List<EtapaOpcionProceso> = emptyList(),
    val cargandoEtapasProceso: Boolean = false,
    val creandoProceso: Boolean = false,
    val procesoAgregado: Boolean = false,
    val errorProceso: String? = null,
    val opcionesTrabajo: OpcionesTrabajoEmpleado? = null,
    val cargandoOpcionesTrabajo: Boolean = false,
    val tarifaTrabajo: TarifaTrabajoEmpleado? = null,
    val cargandoTarifaTrabajo: Boolean = false,
    val creandoTrabajo: Boolean = false,
    val trabajoAgregado: Boolean = false,
    val errorTrabajo: String? = null,
    val opcionesEquipo: OpcionesEquipoProceso? = null,
    val cargandoOpcionesEquipo: Boolean = false,
    val depreciacionEquipo: DepreciacionEquipoProceso? = null,
    val calculandoDepreciacionEquipo: Boolean = false,
    val asignandoEquipo: Boolean = false,
    val equipoAsignado: Boolean = false,
    val errorEquipo: String? = null,
    val formularioEdicion: FormularioEdicionOrden? = null,
    val cargandoFormularioEdicion: Boolean = false,
    val guardandoEdicion: Boolean = false,
    val edicionGuardada: Boolean = false,
    val errorEdicion: String? = null,
    val opcionesAcuerdo: OpcionesAcuerdoOrden? = null,
    val cargandoOpcionesAcuerdo: Boolean = false,
    val guardandoAcuerdo: Boolean = false,
    val acuerdoGuardado: Boolean = false,
    val errorAcuerdo: String? = null,
    val eliminandoAcuerdo: Boolean = false,
    val opcionesLiquidacion: OpcionesLiquidacionOrden? = null,
    val cargandoOpcionesLiquidacion: Boolean = false,
    val guardandoLiquidacion: Boolean = false,
    val liquidacionGuardada: Boolean = false,
    val errorLiquidacion: String? = null,
    val eliminandoLiquidacion: Boolean = false,
    val opcionesIngreso: OpcionesIngresoOrden? = null,
    val cargandoOpcionesIngreso: Boolean = false,
    val guardandoIngreso: Boolean = false,
    val ingresoGuardado: Boolean = false,
    val errorIngreso: String? = null,
    val eliminandoIngreso: Boolean = false,
)
