package com.jomiro.app.mina_mobil

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform