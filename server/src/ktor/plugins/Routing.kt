package me.him188.ani.danmaku.server.ktor.plugins

import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.CacheControl
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import me.him188.ani.danmaku.server.ktor.routing.authRouting
import me.him188.ani.danmaku.server.ktor.routing.danmakuRouting
import me.him188.ani.danmaku.server.ktor.routing.updatesRouting
import me.him188.ani.danmaku.server.ktor.routing.userRouting


internal fun Application.configureRouting() {
    routing {
        get("/status") {
            call.respondText("Server is running")
        }

        // Static resources
        get("/favicon.ico") {
            call.respondRedirect("/static/favicon.ico")
        }

        route({
            hidden = true
        }) {
            staticResources("/static", "static", index = null) {
                preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI)
                enableAutoHeadResponse()
                cacheControl {
                    listOf(CacheControl.MaxAge(maxAgeSeconds = 64000))
                }
            }
        }

        route("/v1") {
            route({
                hidden = true
            }) {
                danmakuRouting()
                userRouting()
                updatesRouting()
            }
            authRouting()
        }
    }
}
