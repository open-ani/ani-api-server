package me.him188.ani.danmaku.server.ktor.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

internal val ServerJson = Json {
    encodeDefaults = true
    explicitNulls = false

    ignoreUnknownKeys = true
}

internal fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(ServerJson)
    }
}
