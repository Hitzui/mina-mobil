package com.jomiro.app.mina_mobil.screen.ordenes.componentes

import kotlinx.datetime.LocalDate

fun String.toDisplayDate(): String = runCatching {
    val date = LocalDate.parse(this)
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"
}.getOrElse { this }
