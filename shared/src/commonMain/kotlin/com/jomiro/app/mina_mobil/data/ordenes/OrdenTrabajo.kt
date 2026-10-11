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

data class DetalleOrdenTrabajo(
    val orden: OrdenTrabajo,
    val procesos: List<ProcesoOrdenTrabajo>,
    val costos: List<CostoOrdenTrabajo> = emptyList(),
    val ingresos: List<IngresoOrdenTrabajo> = emptyList(),
    val acuerdos: List<AcuerdoOrdenTrabajo> = emptyList(),
    val liquidaciones: List<LiquidacionOrdenTrabajo> = emptyList(),
    val trabajosEmpleados: List<TrabajoEmpleadoOrden> = emptyList(),
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
)

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
    val tipoParticipacion: String?,
    val modalidad: String,
    val porcentajeCliente: String?,
    val porcentajeEmpresa: String?,
    val tarifaServicio: String?,
    val moneda: String?,
    val observaciones: String?,
    val activo: Boolean,
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
