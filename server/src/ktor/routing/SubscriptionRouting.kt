package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import kotlinx.serialization.json.JsonObject
import me.him188.ani.danmaku.server.service.SubscriptionProxyService
import org.koin.ktor.ext.inject

fun Route.subscriptions() {
    val subscriptionProxyService: SubscriptionProxyService by inject()
    route(
        "subs",
        {
            tags("Subscriptions")
        },
    ) {
        get(
            "proxy",
            {
                summary = "获取订阅数据"
                description = "获取订阅数据"
                operationId = "getSubscriptionData"
                request {
                    pathParameter<String>("url")
                }
                response {
                    HttpStatusCode.OK to {
                        description = "成功获取订阅数据"
                        body<JsonObject>()
                    }
                }
            },
        ) {
            val url = call.parameters.getOrFail("url")
            subscriptionProxyService.getSubscriptionData(url).let {
                call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                call.respond(HttpStatusCode.OK, it)
            }
        }
    }
}