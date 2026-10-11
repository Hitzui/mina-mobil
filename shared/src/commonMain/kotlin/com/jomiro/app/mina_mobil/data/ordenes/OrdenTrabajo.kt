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

data class DetalleOrdenTrabajo(
    val orden: OrdenTrabajo,
    val procesos: List<ProcesoOrdenTrabajo>,
    val costos: List<CostoOrdenTrabajo> = emptyList(),
    val ingresos: List<IngresoOrdenTrabajo> = emptyList(),
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
