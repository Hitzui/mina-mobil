package com.jomiro.app.mina_mobil.screen.ordenes.detalle

enum class OrdenDetalleSeccion(val titulo: String, val icono: String) {
    COSTOS("Costos de la orden", "¤"),
    INGRESOS("Ingresos de la orden", "↗"),
    ACUERDOS("Acuerdos de participación", "▤"),
    LIQUIDACIONES("Liquidaciones de oro", "◈"),
}
