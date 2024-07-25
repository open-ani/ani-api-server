package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.util.pipeline.PipelineContext
import me.him188.ani.danmaku.protocol.ReleaseClass
import me.him188.ani.danmaku.protocol.ReleaseUpdatesDetailedResponse
import me.him188.ani.danmaku.protocol.ReleaseUpdatesResponse
import me.him188.ani.danmaku.protocol.UpdateInfo
import me.him188.ani.danmaku.server.service.ClientReleaseInfoManager
import me.him188.ani.danmaku.server.service.ReleaseInfo
import me.him188.ani.danmaku.server.util.exception.BadRequestException
import me.him188.ani.danmaku.server.util.exception.InvalidClientVersionException
import me.him188.ani.danmaku.server.util.exception.fromException
import org.koin.ktor.ext.inject

fun Route.updatesRouting() {
    val clientReleaseInfoManager by inject<ClientReleaseInfoManager>()

    route("/updates", {
        tags("Updates")
        hidden = false
    }) {
        get("/incremental", {
            summary = "获取可更新的版本号列表"
            description = "返回所有大于当前版本的更新版本号。"
            commonRequestBlock()
            response {
                HttpStatusCode.OK to {
                    description = "成功获取内容"
                    body<ReleaseUpdatesResponse> {
                        description = "更新版本号列表"
                        example("example") {
                            ReleaseUpdatesResponse(listOf("3.0.0-rc01", "3.0.0-rc02", "3.0.0-rc03"))
                        }
                    }
                }
                HttpStatusCode.BadRequest to {
                    description = "请求参数错误"
                }
                HttpStatusCode.fromException(InvalidClientVersionException()) to {
                    description = "不合法的客户端版本号"
                }
            }
        }) {
            val updates = updateInfos(clientReleaseInfoManager)
            call.respond(ReleaseUpdatesResponse(updates.map { it.version.toString() }))
        }
        get("/incremental/details", {
            summary = "获取可更新的版本详情"
            description = "返回所有大于当前版本的更新版本的详细信息，包括版本号、下载地址、发布时间以及更新内容。"
            commonRequestBlock()
            response {
                HttpStatusCode.OK to {
                    description = "成功获取内容"
                    body<ReleaseUpdatesDetailedResponse> {
                        description = "更新版本详细信息列表"
                        example("example") { exampleReleaseUpdatesDetailedResponse }
                    }
                }
                HttpStatusCode.BadRequest to {
                    description = "请求参数错误"
                }
                HttpStatusCode.fromException(InvalidClientVersionException()) to {
                    description = "不合法的客户端版本号"
                }
            }
        }) {
            val updates = updateInfos(clientReleaseInfoManager)
            val clientPlatform = call.request.queryParameters["clientPlatform"]
                ?: throw BadRequestException("Missing parameter clientPlatform")
            val clientArch = call.request.queryParameters["clientArch"]
                ?: throw BadRequestException("Missing parameter clientArch")
            call.respond(
                ReleaseUpdatesDetailedResponse(
                    updates.mapNotNull {
                        val downloadUrls = try {
                            clientReleaseInfoManager.parseDownloadUrls(it.version, "$clientPlatform-$clientArch")
                        } catch (e: IllegalArgumentException) {
                            return@mapNotNull null
                        }
                        UpdateInfo(
                            it.version.toString(),
                            downloadUrls,
                            it.publishTime,
                            it.description,
                        )
                    },
                ),
            )
        }
        get("/latest", {

        }) {
            val releaseClass = call.request.queryParameters["releaseClass"]?.let {
                ReleaseClass.fromStringOrNull(it)
            } ?: ReleaseClass.STABLE

            val updates = clientReleaseInfoManager.getLatestRelease("", releaseClass)
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.updateInfos(
    clientReleaseInfoManager: ClientReleaseInfoManager
): List<ReleaseInfo> {
    val version =
        call.request.queryParameters["clientVersion"] ?: throw BadRequestException("Missing parameter clientVersion")
    val clientPlatform =
        call.request.queryParameters["clientPlatform"] ?: throw BadRequestException("Missing parameter clientPlatform")
    val clientArch =
        call.request.queryParameters["clientArch"] ?: throw BadRequestException("Missing parameter clientArch")
    val releaseClass = call.request.queryParameters["releaseClass"]?.let {
        ReleaseClass.fromStringOrNull(it)
    } ?: throw BadRequestException("Missing or invalid parameter releaseClass")

    val updates = clientReleaseInfoManager.getAllUpdateLogs(
        version,
        "$clientPlatform-$clientArch",
        releaseClass,
    )
    return updates
}

private fun OpenApiRoute.commonRequestBlock() {
    request {
        queryParameter<String>("clientVersion") {
            description = "客户端当前版本号。不合法的版本号会导致服务器返回 461 Invalid Client Version 错误。"
            required = true
        }
        queryParameter<String>("clientPlatform") {
            description = "客户端平台，例：windows, android。不合法的值会导致服务器返回空的版本号列表。"
            required = true
        }
        queryParameter<String>("clientArch") {
            description = "客户端架构，例：x86_64, aarch64。不合法的值会导致服务器返回空的版本号列表。"
            required = true
        }
        queryParameter<String>("releaseClass") {
            description =
                "更新版本的发布类型，可选值：alpha, beta, rc, stable。不合法的发布类型会导致服务器返回 400 Bad Request 错误。"
            required = true
        }
    }
}

private val exampleReleaseUpdatesDetailedResponse = ReleaseUpdatesDetailedResponse(
    listOf(
        UpdateInfo(
            "3.0.0-rc01",
            listOf("https://d.myani.org/v3.0.0-rc01/ani-3.0.0-rc01.apk"),
            1716604732,
            """
                ## 主要更新
                    - 重新设计资源选择器 #328
                       - 了解每个数据源的查询结果, 失败时点击重试 #327 #309
                       - 支持临时启用禁用数据源以应对未找到的情况
                       - 区分 BT 源和在线源并增加提示 #330
                    - 优化资源选择算法
                      - 默认隐藏生肉资源, 可在设置中恢复显示
                      - 支持番剧完结后隐藏单集 BT 资源, 默认启用, 可在设置关闭
                      - 支持优先选择季度全集资源 #304
                      - 自动优先选择本地缓存资源, 不再需要等待 #258 #260
                    ## 次要更新
                    - 提高弹幕匹配准确率 #338
                    - 自动选择数据源时不再覆盖偏好设置
                    - 自动选择数据源时不再保存不准确的字幕语言设置
                    - 在切换数据源时, 将会按顺序自动取消筛选直到显示列表不为空
                    - 在取消选择数据源的过滤时也记忆偏好设置
                    - 修复有时候选择资源时会崩溃的问题
                    - 优化数据源请求时的性能
                    - 修复标题过长挤掉按钮的问题 #311
                    - 修复会请求过多条目的问题
                    - 修复条目缓存页可能有资源泄露的问题 #190
            """.trimIndent()
        ),
    )
)
