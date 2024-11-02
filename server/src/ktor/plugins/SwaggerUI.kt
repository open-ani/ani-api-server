package me.him188.ani.danmaku.server.ktor.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthScheme
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.swagger.v3.oas.models.media.Schema
import kotlinx.datetime.Instant

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
        schemas {
            overwrite<Instant>(
                Schema<String>().apply {
                    name = "Iso8601 Instant"
                    example = "2020-12-09T09:16:56.000124Z"
                },
            )
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