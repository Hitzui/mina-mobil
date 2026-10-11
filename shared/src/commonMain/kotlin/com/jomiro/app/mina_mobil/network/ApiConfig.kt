package com.jomiro.app.mina_mobil.network

import com.jomiro.app.mina_mobil.getPlatform

/**
 * Único punto de configuración del API. Para producción, sustituir esta URL
 * por la dirección HTTPS del servidor.
 */
const val API_BASE_URL = "http://localhost/mina/public/api/v1"

/** Android Emulator reserva 10.0.2.2 para acceder al localhost del equipo host. */
fun resolvedApiBaseUrl(): String {
    return resolveApiBaseUrl(getPlatform().name)
}

internal fun resolveApiBaseUrl(platformName: String): String {
    val isAndroid = platformName.startsWith("Android")
    return if (isAndroid && API_BASE_URL.startsWith("http://localhost")) {
        API_BASE_URL.replaceFirst("http://localhost", "http://10.0.2.2")
    } else {
        API_BASE_URL
    }
}
