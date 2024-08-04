package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import me.him188.ani.danmaku.protocol.Danmaku
import me.him188.ani.danmaku.protocol.DanmakuGetResponse
import me.him188.ani.danmaku.protocol.DanmakuInfo
import me.him188.ani.danmaku.protocol.DanmakuLocation
import me.him188.ani.danmaku.protocol.DanmakuPostRequest
import me.him188.ani.danmaku.server.service.DanmakuService
import me.him188.ani.danmaku.server.util.exception.AcquiringTooMuchDanmakusException
import me.him188.ani.danmaku.server.util.exception.BadRequestException
import me.him188.ani.danmaku.server.util.exception.EmptyDanmakuException
import me.him188.ani.danmaku.server.util.exception.fromException
import me.him188.ani.danmaku.server.util.getUserIdOrRespond
import org.koin.ktor.ext.inject
import java.awt.Color

fun Route.danmakuRouting() {
    val service: DanmakuService by inject()

    route("/danmaku/{episodeId}", {
        tags("Danmaku")
    }) {
        authenticate("auth-jwt") {
            post({
                summary = "发送弹幕"
                description = "发送一条弹幕至某一剧集，可指定弹幕时间、内容、颜色和内容。需要用户登录。"
                operationId = "postDanmaku"
                request {
                    pathParameter<String>("episodeId") {
                        description = "剧集 ID"
                        required = true
                    }
                    body<DanmakuPostRequest> {
                        description = "弹幕信息"
                        example("example") {
                            value = DanmakuPostRequest(
                                DanmakuInfo(
                                    0,
                                    Color.BLACK.rgb,
                                    "Hello, world!",
                                    DanmakuLocation.NORMAL,
                                ),
                            )
                        }
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "弹幕发送成功"
                    }
                    HttpStatusCode.BadRequest to {
                        description = "请求参数错误"
                    }
                    HttpStatusCode.Unauthorized to {
                        description = "未登录或用户 token 无效"
                    }
                    HttpStatusCode.fromException(EmptyDanmakuException()) to {
                        description = "弹幕内容为空"
                    }
                }
            }) {
                val userId = getUserIdOrRespond() ?: return@post
                val request = call.receive<DanmakuPostRequest>()
                val episodeId = call.parameters["episodeId"] ?: throw BadRequestException("Missing parameter episodeId")
                service.postDanmaku(episodeId, request.danmakuInfo, userId)
                call.respond(HttpStatusCode.OK)
            }
        }

        get({
            summary = "获取弹幕"
            description = "获取某一剧集内的弹幕，可指定某一时间范围及最大获取数量。"
            operationId = "getDanmaku"
            request {
                pathParameter<String>("episodeId") {
                    description = "剧集 ID"
                    required = true
                }
                queryParameter<Int>("maxCount") {
                    description = "最大弹幕获取数量，默认为 8000"
                    required = false
                }
                queryParameter<Long>("fromTime") {
                    description = "过滤范围开始时间，单位为毫秒，默认为 0"
                    required = false
                }
                queryParameter<Long>("toTime") {
                    description = "过滤范围结束时间，单位为毫秒，默认为 -1；值为负数时表示不限制结束时间"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "成功获取内容"
                    body<DanmakuGetResponse> {
                        description = "弹幕列表"
                        example("example") { value = exampleDanmakuGetResponse }
                    }
                }
                HttpStatusCode.BadRequest to {
                    description = "请求参数错误"
                }
                HttpStatusCode.fromException(AcquiringTooMuchDanmakusException()) to {
                    description = "请求弹幕数量过多。maxCount 参数传入值超过 8000 时会返回此错误。"
                }
            }
        }) {
            val maxCount = call.request.queryParameters["maxCount"]?.toIntOrNull()
            val fromTime = call.request.queryParameters["fromTime"]?.toLongOrNull()
            val toTime = call.request.queryParameters["toTime"]?.toLongOrNull()
            val episodeId = call.parameters["episodeId"] ?: throw BadRequestException("Missing parameter episodeId")
            val result = service.getDanmaku(episodeId, maxCount, fromTime, toTime)
            call.respond(DanmakuGetResponse(result))
        }
    }
}

private val exampleDanmakuGetResponse = DanmakuGetResponse(
    listOf(
        Danmaku(
            "ba1f213a-50bd-4e09-a4e0-de6e24b72e22",
            "3db414d0-930a-4144-84cf-b841f486215e",
            DanmakuInfo(
                0,
                Color.BLACK.rgb,
                "Hello, world!",
                DanmakuLocation.NORMAL,
            ),
        ),
    ),
)
