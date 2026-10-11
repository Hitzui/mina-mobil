package com.jomiro.app.mina_mobil.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiJson {
    val instance = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

fun createApiClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    defaultRequest {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
    install(ContentNegotiation) {
        json(ApiJson.instance)
    }
}
