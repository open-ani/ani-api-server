package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.bkbn.kompendium.core.metadata.GetInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.him188.ani.danmaku.protocol.AniUser
import me.him188.ani.danmaku.server.service.UserService
import me.him188.ani.danmaku.server.util.getUserIdOrRespond
import org.koin.ktor.ext.inject

fun Route.userRouting() {
    val service: UserService by inject()

    route("/me", {
        tags("User")
    }) {
        authenticate("auth-jwt") {
            get({
                summary = "查看当前用户信息"
                description = "查看当前携带的 token 对应用户的信息，包含其 Ani ID，Bangumi 昵称以及 Bangumi 头像 URL。"
                operationId = "getUser"
                response {
                    HttpStatusCode.OK to {
                        description = "成功获取用户信息"
                        body<AniUser> {
                            description = "用户信息"
                            example("example") {
                                value = AniUser(
                                    id = "762e10b5-37c2-4a2b-a39b-b3033a5979f8",
                                    nickname = "Him188",
                                    smallAvatar = "https://example.com/avatarSmall.jpg",
                                    mediumAvatar = "https://example.com/avatarMedium.jpg",
                                    largeAvatar = "https://example.com/avatarLarge.jpg",
                                    registerTime = 1714404248957,
                                    lastLoginTime = 1714404248957,
                                    clientVersion = "3.0.0-beta22",
                                    clientPlatforms = setOf("macos-aarch64", "android-aarch64", "windows-x86_64"),
                                )
                            }
                        }
                    }
                    HttpStatusCode.Unauthorized to {
                        description = "未登录或用户 token 无效"
                    }
                    HttpStatusCode.NotFound to {
                        description = "用户 token 对应的用户不存在"
                    }
                }
            }) {
                val userId = getUserIdOrRespond() ?: return@get
                val user = service.getUser(userId)
                call.respond(user)
            }
        }
    }
}
