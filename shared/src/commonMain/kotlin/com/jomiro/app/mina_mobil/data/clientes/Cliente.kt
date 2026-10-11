package com.jomiro.app.mina_mobil.data.clientes

data class Cliente(
    val id: Long,
    val nombre: String,
    val telefono: String?,
    val direccion: String?,
    val activo: Boolean,
)

data class PaginaClientes(
    val clientes: List<Cliente>,
    val paginaActual: Int,
    val ultimaPagina: Int,
    val total: Int,
)
