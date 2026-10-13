package com.jomiro.app.mina_mobil.data.ordenes

data class OrdenTrabajo(
    val id: Long,
    val codigo: String,
    val fecha: String?,
    val cliente: String?,
    val descripcion: String?,
    val pesoMineral: Double?,
    val unidadPeso: String?,
    val estado: String,
    val codigoEstado: Int,
)

data class PaginaOrdenes(
    val ordenes: List<OrdenTrabajo>,
    val paginaActual: Int,
    val ultimaPagina: Int,
    val total: Int,
)

data class ClienteOpcionOrden(val id: Long, val nombre: String)

data class UnidadPesoOpcion(val id: Long, val nombre: String)

data class OpcionesFormularioOrden(
    val clientes: List<ClienteOpcionOrden>,
    val unidades: List<UnidadPesoOpcion>,
)

data class FormularioEdicionOrden(
    val orden: OrdenTrabajo,
    val clienteId: Long,
    val unidadPesoId: Long?,
    val tieneProcesos: Boolean,
    val clientes: List<ClienteOpcionOrden>,
    val unidades: List<UnidadPesoOpcion>,
)

data class DetalleOrdenTrabajo(
    val orden: OrdenTrabajo,
    val procesos: List<ProcesoOrdenTrabajo>,
    val costos: List<CostoOrdenTrabajo> = emptyList(),
    val ingresos: List<IngresoOrdenTrabajo> = emptyList(),
    val acuerdos: List<AcuerdoOrdenTrabajo> = emptyList(),
    val liquidaciones: List<LiquidacionOrdenTrabajo> = emptyList(),
    val trabajosEmpleados: List<TrabajoEmpleadoOrden> = emptyList(),
    val equipos: List<EquipoProcesoOrden> = emptyList(),
)

data class EquipoProcesoOrden(
    val id: Long,
    val codigoProceso: String?,
    val equipoId: Long,
    val codigoEquipo: String?,
    val nombreEquipo: String?,
    val inicio: String?,
    val fin: String?,
    val dias: Double,
    val depreciacionDiaria: Double,
    val depreciacionTotal: Double,
    val observaciones: String?,
)

data class EquipoOpcionProceso(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val valorAdquisicion: Double,
    val valorResidual: Double,
    val vidaUtilMeses: Int,
)

data class OpcionesEquipoProceso(val equipos: List<EquipoOpcionProceso>, val inicioPredeterminado: String)

data class DepreciacionEquipoProceso(
    val codigo: String,
    val valorAdquisicion: Double,
    val valorResidual: Double,
    val vidaUtilMeses: Int,
    val dias: Double,
    val depreciacionDiaria: Double,
    val depreciacionTotal: Double,
    val disponible: Boolean,
    val solapamiento: String?,
)

data class TrabajoEmpleadoOrden(
    val id: Long,
    val codigoProceso: String?,
    val empleado: String?,
    val tipoPago: String?,
    val fecha: String?,
    val descripcion: String?,
    val cantidad: String?,
    val unidad: String?,
    val moneda: String?,
    val total: String?,
    val totalNio: String?,
)

data class ProcesoOrdenTrabajo(
    val id: Long,
    val codigo: String,
    val etapa: String?,
    val estado: String,
    val fechaInicio: String?,
    val fechaFin: String?,
    val pesoEntrada: Double?,
    val pesoSalida: Double?,
    val observaciones: String?,
    val costos: List<CostoProcesoTrabajo> = emptyList(),
    val materiales: List<MaterialProcesoTrabajo> = emptyList(),
)

data class MaterialProcesoTrabajo(
    val id: Long,
    val productoId: Long,
    val producto: String,
    val unidad: String?,
    val fecha: String,
    val cantidad: Double,
    val costoUnitario: Double,
    val totalNio: Double?,
    val observaciones: String?,
)

data class ProductoMateriaPrima(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val unidad: String,
    val existencia: Double,
    val costoPromedio: Double,
)

data class OpcionesMaterialProceso(val productos: List<ProductoMateriaPrima>, val fecha: String)

data class CostoProcesoTrabajo(
    val id: Long,
    val categoriaId: Long,
    val categoria: String,
    val fecha: String,
    val descripcion: String,
    val cantidad: Double,
    val costoUnitario: Double,
    val total: Double,
    val monedaId: Long,
    val moneda: String,
    val totalNio: Double?,
    val observaciones: String?,
)

data class CategoriaCostoOpcion(val id: Long, val nombre: String)
data class MonedaCostoOpcion(val id: Long, val codigo: String, val nombre: String, val monedaBase: Boolean)
data class OpcionesCostoProceso(val categorias: List<CategoriaCostoOpcion>, val monedas: List<MonedaCostoOpcion>, val fecha: String)

data class EtapaOpcionProceso(val id: Long, val nombre: String, val orden: Int)

data class CostoOrdenTrabajo(
    val id: Long,
    val fecha: String?,
    val proceso: String?,
    val categoria: String?,
    val descripcion: String?,
    val cantidad: String?,
    val moneda: String?,
    val total: String?,
    val totalNio: String?,
)

data class IngresoOrdenTrabajo(
    val id: Long,
    val fecha: String?,
    val tipo: String?,
    val descripcion: String?,
    val moneda: String?,
    val total: String?,
    val totalNio: String?,
)

data class AcuerdoOrdenTrabajo(
    val id: Long,
    val tipoParticipacionId: Long = 0,
    val tipoParticipacion: String?,
    val modalidad: String,
    val porcentajeCliente: String?,
    val porcentajeEmpresa: String?,
    val tarifaServicio: String?,
    val moneda: String?,
    val monedaId: Long = 0,
    val observaciones: String?,
    val activo: Boolean,
)

data class TipoParticipacionOpcion(val id: Long, val nombre: String)
data class MonedaAcuerdoOpcion(val id: Long, val codigo: String, val nombre: String, val monedaBase: Boolean)
data class OpcionesAcuerdoOrden(
    val tipos: List<TipoParticipacionOpcion>,
    val monedas: List<MonedaAcuerdoOpcion>,
    val monedaBaseId: Long?,
)

data class LiquidacionOrdenTrabajo(
    val id: Long,
    val fecha: String?,
    val gramosRecuperados: String?,
    val gramosCliente: String?,
    val gramosEmpresa: String?,
    val porcentajeCliente: String?,
    val porcentajeEmpresa: String?,
    val observaciones: String?,
)

data class AcuerdoLiquidacionOpcion(
    val id: Long,
    val tipoParticipacion: String?,
    val porcentajeCliente: Double,
    val porcentajeEmpresa: Double,
)

data class OpcionesLiquidacionOrden(
    val fecha: String,
    val acuerdos: List<AcuerdoLiquidacionOpcion>,
)
