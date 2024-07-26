package me.him188.ani.danmaku.server.ktor.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import me.him188.ani.danmaku.server.ServerConfig
import org.koin.java.KoinJavaComponent.inject

internal fun Application.configureCors() {
    install(CORS) {
        val serverConfig by inject<ServerConfig>(ServerConfig::class.java)
        for (host in serverConfig.corsAllowHost) {
            allowHost(host)
        }
        allowHeader(HttpHeaders.ContentType)
    }
}