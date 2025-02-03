package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import me.him188.ani.danmaku.protocol.AnonymousBangumiUserToken
import me.him188.ani.danmaku.protocol.BangumiLoginRequest
import me.him188.ani.danmaku.protocol.BangumiLoginResponse
import me.him188.ani.danmaku.protocol.BangumiUserToken
import me.him188.ani.danmaku.protocol.RefreshBangumiTokenRequest
import me.him188.ani.danmaku.server.service.AuthService
import me.him188.ani.danmaku.server.service.JwtTokenManager
import me.him188.ani.danmaku.server.service.UserService
import me.him188.ani.danmaku.server.util.exception.BadRequestException
import me.him188.ani.danmaku.server.util.exception.InvalidClientVersionException
import me.him188.ani.danmaku.server.util.exception.fromException
import org.koin.ktor.ext.inject
import java.util.Locale

fun Route.authRouting() {
    val service: AuthService by inject()
    val userService: UserService by inject()
    val jwtTokenManager: JwtTokenManager by inject()

    route(
        "/login/bangumi",
        {
            tags("Bangumi OAuth")
            hidden = false
        },
    ) {
        post(
            {
                summary = "使用 Bangumi token 登录"
                description = "使用 Bangumi token 登录并获取用户会话 token。"
                operationId = "bangumiLogin"
                request {
                    body<BangumiLoginRequest> {
                        description =
                            "Bangumi token 字符串以及客户端版本与平台架构信息。 " +
                                    "clientOS参数可选值：`windows, macos, android, ios, linux, debian, ubuntu, redhat`；" +
                                    "clientArch参数可选值：`aarch64, x86, x86_64`。"

                        example("example") {
                            value = BangumiLoginRequest(
                                bangumiToken = "VAcbHKhXqcjpCOVY5KFxwYEeQCOw4i0u",
                                clientVersion = "3.0.0-beta24",
                                clientOS = "android",
                                clientArch = "aarch64",
                            )
                        }
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "成功获取用户会话 token"
                        body<BangumiLoginResponse> {
                            description = "用户会话 token 字符串"
                            example("example") {
                                value = BangumiLoginResponse(
                                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJIZWxsbyB0aGVyZSJ9.TNpICIfOzK-BvxxV72ApTiD4SlAwvzHbu_0O3FXq-s4",
                                    ANI_USER_SAMPLE,
                                )
                            }
                        }
                    }
                    HttpStatusCode.Unauthorized to {
                        description = "Bangumi token 无效"
                    }
                    HttpStatusCode.fromException(InvalidClientVersionException()) to {
                        description = "请求体中客户端版本无效"
                    }
                }
            },
        ) {
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
            val user = userService.getUser(userId)
            call.respond(BangumiLoginResponse(userToken, user))
        }
        route("/oauth") {
            get(
                {
                    summary = "获取 Bangumi OAuth 授权链接"
                    description = "获取 Bangumi OAuth 授权链接，用于获取 Bangumi token。"
                    operationId = "getBangumiOauthUrl"
                    request {
                        parameters {
                            queryParameter<String>("requestId") {
                                description = "唯一请求 ID，建议使用随机生成的 UUID"
                                required = true
                                example("example") {
                                    value = "123e4567-e89b-12d3-a456-426614174000"
                                }
                            }
                        }
                    }
                    response {
                        HttpStatusCode.MovedPermanently to {
                            description = "重定向到 Bangumi OAuth 授权页面"
                        }
                    }
                },
            ) {
                val requestId = call.parameters["requestId"] ?: throw BadRequestException("Missing parameter requestId")
                call.respondRedirect(service.getBangumiOauthUrl(requestId), permanent = false)
            }

            get(
                "/callback",
                {
                    summary = "Bangumi OAuth 回调"
                    description = "用于 Bangumi OAuth 授权回调，用户不应自行调用该接口。"
                    operationId = "bangumiOauthCallback"
                    request {
                        parameters {
                            queryParameter<String>("code") {
                                description = "Bangumi OAuth 授权码"
                                required = true
                                example("example") {
                                    value = "7b5fc66fcea59f975d8c17322ae3b5cb1faa1799"
                                }
                            }
                            queryParameter<String>("state") {
                                description = "获取 OAuth 链接时提供的请求 ID"
                                required = true
                                example("example") {
                                    value = "123e4567-e89b-12d3-a456-426614174000"
                                }
                            }
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "返回 Bangumi OAuth 授权结果网页"
                        }
                    }
                },
            ) {
                val bangumiCode = call.parameters["code"] ?: throw BadRequestException("Missing parameter code")
                val requestId = call.parameters["state"] ?: throw BadRequestException("Missing parameter state")
                val succeed = service.bangumiOauthCallback(bangumiCode, requestId)

                if (succeed) {
                    call.respondRedirect("/static/immutable/authed.v1.html", permanent = false)
                } else {
                    call.respondRedirect("/static/immutable/authFailed.v1.html", permanent = false)
                }
            }

            get(
                "/token",
                {
                    summary = "获取 Bangumi token"
                    description = "获取 Bangumi token，用于登录。"
                    operationId = "getBangumiToken"
                    request {
                        parameters {
                            queryParameter<String>("requestId") {
                                description = "获取 OAuth 链接时提供的请求 ID"
                                required = true
                                example("example") {
                                    value = "123e4567-e89b-12d3-a456-426614174000"
                                }
                            }
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "成功获取 Bangumi token"
                            body<BangumiUserToken> {
                                description =
                                    "Bangumi token 信息，包含 Bangumi 用户 ID、访问 token、刷新 token 以及 token 有效时间"
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

                },
            ) {
                val requestId = call.parameters["requestId"] ?: throw BadRequestException("Missing parameter requestId")
                val bangumiToken = service.getBangumiToken(requestId)
                call.respond(bangumiToken)
            }

            post(
                "/refresh",
                {
                    summary = "刷新 Bangumi token"
                    description = "刷新 Bangumi token。"
                    operationId = "refreshBangumiToken"
                    request {
                        body<RefreshBangumiTokenRequest> {
                            description = "上次登录时提供的刷新 token"
                            example("example") {
                                value = RefreshBangumiTokenRequest("6f91bc748d8afe18e9dfe014a3da6340efcbaee2")
                            }
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "成功刷新 Bangumi token"
                            body<AnonymousBangumiUserToken> {
                                description = "Bangumi token 信息，包含新的访问 token、刷新 token 以及 token 有效时间"
                                example("example") {
                                    value = AnonymousBangumiUserToken(
                                        accessToken = "35a2b4f1b068ccf490b2c1768b8c910723303741",
                                        refreshToken = "dfe040e6f91bc748d8afe18e9fcbaee214a3da63",
                                        expiresIn = 604800,
                                    )
                                }
                            }
                        }
                        HttpStatusCode.Unauthorized to {
                            description = "刷新 token 无效"
                        }
                    }
                },
            ) {
                val refreshToken = call.receive<RefreshBangumiTokenRequest>().refreshToken
                val newToken = service.refreshBangumiToken(refreshToken)
                call.respond(newToken)
            }
        }
    }
}

