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
