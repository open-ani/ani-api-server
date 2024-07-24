package me.him188.ani.danmaku.server.ktor.plugins

import io.ktor.server.application.*
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthScheme
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.routing.*

internal fun Application.configureSwaggerUI() {
    install(SwaggerUI) {
        info {
            title = "Ani"
            version = "1.0.0"
            description = "Ani API"
        }
        server {
            url = "https://danmaku.api.myani.org/"
        }
        security {
            defaultSecuritySchemeNames = setOf("auth-jwt")
            securityScheme("auth-jwt") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
            }
        }
    }

    routing {
        route("openapi.json") {
            openApiSpec()
        }
        route("swagger") {
            swaggerUI("/openapi.json")
        }
    }
}