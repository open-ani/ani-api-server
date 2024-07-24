package me.him188.ani.danmaku.server.ktor.routing

import io.bkbn.kompendium.core.metadata.PostInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.html.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import me.him188.ani.danmaku.protocol.BangumiLoginRequest
import me.him188.ani.danmaku.protocol.BangumiLoginResponse
import me.him188.ani.danmaku.protocol.BangumiUserToken
import me.him188.ani.danmaku.server.service.AuthService
import me.him188.ani.danmaku.server.service.JwtTokenManager
import me.him188.ani.danmaku.server.util.exception.BadRequestException
import me.him188.ani.danmaku.server.util.exception.InvalidClientVersionException
import me.him188.ani.danmaku.server.util.exception.fromException
import org.koin.ktor.ext.inject
import java.util.Locale

fun Route.authRouting() {
    val service: AuthService by inject()
    val jwtTokenManager: JwtTokenManager by inject()

    route("/login/bangumi") {
        post {
            val request = call.receive<BangumiLoginRequest>()
            val os = request.clientOS?.lowercase(Locale.ENGLISH)
            val arch = request.clientArch?.lowercase(Locale.ENGLISH)
            val platform: String?
            if (os != null && arch != null) {
                if (os !in BangumiLoginRequest.AllowedOSes || arch !in BangumiLoginRequest.AllowedArchs) {
                    throw BadRequestException("Bad client platform or architecture")
                }
                platform = "$os-$arch"
            } else {
                platform = null
            }
            val userId = service.loginBangumi(
                request.bangumiToken, request.clientVersion,
                clientPlatform = platform,
            )
            val userToken = jwtTokenManager.createToken(userId)
            call.respond(BangumiLoginResponse(userToken))
        }
    }

    route("/login/bangumi/oauth", {
        tags("Bangumi OAuth")
    }) {
        get({
            summary = "获取Bangumi OAuth授权链接"
            description = "获取Bangumi OAuth授权链接，用于获取Bangumi token。"
            request {
                parameters {
                    queryParameter<String>("requestId") {
                        description = "唯一请求ID，建议使用随机生成的UUID"
                        required = true
                        example("example") {
                            value = "123e4567-e89b-12d3-a456-426614174000"
                        }
                    }
                }
            }
            response {
                HttpStatusCode.MovedPermanently to {
                    description = "重定向到Banguimi OAuth授权页面"
                }
            }
        }) {
            val requestId = call.parameters["requestId"] ?: throw BadRequestException("Missing parameter requestId")
            call.respondRedirect(service.getBangumiOauthUrl(requestId), permanent = true)
        }

        get("/callback", {
            summary = "Bangumi OAuth回调"
            description = "用于Bangumi OAuth授权回调，用户不应自行调用该接口。"
            request {
                parameters {
                    queryParameter<String>("code") {
                        description = "Bangumi OAuth授权码"
                        required = true
                        example("example") {
                            value = "7b5fc66fcea59f975d8c17322ae3b5cb1faa1799"
                        }
                    }
                    queryParameter<String>("state") {
                        description = "获取OAuth链接时提供的请求ID"
                        required = true
                        example("example") {
                            value = "123e4567-e89b-12d3-a456-426614174000"
                        }
                    }
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "返回Bangumi OAuth授权结果网页"
                }
            }
        }) {
            val bangumiCode = call.parameters["code"] ?: throw BadRequestException("Missing parameter code")
            val requestId = call.parameters["state"] ?: throw BadRequestException("Missing parameter state")
            val succeed = service.bangumiOauthCallback(bangumiCode, requestId)

            call.respondHtml {
                head {
                    title("Bangumi OAuth")
                }
                body {
                    h1 { +"Bangumi OAuth" }
                    p { +if (succeed) "授权成功" else "授权失败" }
                    button {
                        onClick = "window.location.href = 'https://www.bilibili.com/video/BV1hq4y1s7VH'"
                        +"返回App"
                    }
                }
            }
        }

        get("/token", {
            summary = "获取Bangumi token"
            description = "获取Bangumi token，用于登录。"
            request {
                parameters {
                    queryParameter<String>("requestId") {
                        description = "获取OAuth链接时提供的请求ID"
                        required = true
                        example("example") {
                            value = "123e4567-e89b-12d3-a456-426614174000"
                        }
                    }
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "成功获取Bangumi token"
                    body<BangumiUserToken> {
                        description = "Bangumi token信息，包含Bangumi用户ID、访问token、刷新token以及token有效时间"
                        example("example") {
                            value = BangumiUserToken(
                                userId = 800001,
                                expiresIn = 604800,
                                accessToken = "2c1768b8c910735a2b4f1b06b233037418ccf490",
                                refreshToken = "6f91bc748d8afe18e9dfe014a3da6340efcbaee2",
                            )
                        }
                    }
                }
            }

        }) {
            val requestId = call.parameters["requestId"] ?: throw BadRequestException("Missing parameter requestId")
            val bangumiToken = service.getBangumiToken(requestId)
            call.respond(bangumiToken)
        }
    }
}

private fun Route.documentation() {
    install(NotarizedRoute()) {
        post = PostInfo.builder {
            summary("使用Bangumi token登录")
            description("使用Bangumi token登录并获取用户会话token。")
            request {
                requestType<BangumiLoginRequest>()
                description("Bangumi token字符串")
                examples(
                    "" to BangumiLoginRequest(
                        bangumiToken = "VAcbHKhXqcjpCOVY5KFxwYEeQCOw4i0u",
                        clientVersion = "3.0.0-beta24",
                        clientOS = "Android",
                        clientArch = "aarch64",
                    ),
                )
            }
            response {
                responseCode(HttpStatusCode.OK)
                responseType<BangumiLoginResponse>()
                description("用户会话token字符串")
                examples("" to BangumiLoginResponse("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJIZWxsbyB0aGVyZSJ9.TNpICIfOzK-BvxxV72ApTiD4SlAwvzHbu_0O3FXq-s4"))
            }
            canRespond {
                responseCode(HttpStatusCode.Unauthorized)
                responseType<Any>()
                description("Bangumi token无效")
            }
            canRespond {
                responseCode(HttpStatusCode.fromException(InvalidClientVersionException()))
                responseType<Any>()
                description("请求体中客户端版本无效")
            }
        }
    }
}