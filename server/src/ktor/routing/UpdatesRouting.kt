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
import me.him188.ani.danmaku.protocol.*
import me.him188.ani.danmaku.server.service.ClientReleaseInfoManager
import me.him188.ani.danmaku.server.service.ReleaseInfo
import me.him188.ani.danmaku.server.util.DistributionSuffixParser
import me.him188.ani.danmaku.server.util.exception.BadRequestException
import me.him188.ani.danmaku.server.util.exception.InvalidClientVersionException
import me.him188.ani.danmaku.server.util.exception.NotFoundException
import me.him188.ani.danmaku.server.util.exception.fromException
import org.koin.ktor.ext.inject

fun Route.updatesRouting() {
    val clientReleaseInfoManager by inject<ClientReleaseInfoManager>()
    val distributionSuffixParser by inject<DistributionSuffixParser>()

    route("/updates", {
        tags("Updates")
        hidden = false
    }) {
        get("/incremental", {
            summary = "获取可更新的版本号列表"
            description = "返回所有大于当前版本的更新版本号。"
            operationId = "getUpdates"
            commonRequestBlock()
            response {
                HttpStatusCode.OK to {
                    description = "成功获取内容"
                    body<ReleaseUpdatesResponse> {
                        description = "更新版本号列表"
                        example("example") {
                            value = ReleaseUpdatesResponse(listOf("3.0.0-rc01", "3.0.0-rc02", "3s/incre0.0-rc03"))
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
            operationId = "getDetailedUpdates"
            commonRequestBlock()
            response {
                HttpStatusCode.OK to {
                    description = "成功获取内容"
                    body<ReleaseUpdatesDetailedResponse> {
                        description = "更新版本详细信息列表"
                        example("example") { value = exampleReleaseUpdatesDetailedResponse }
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
                    updates.mapNotNull { releaseInfo ->
                        val downloadUrls = try {
                            clientReleaseInfoManager.parseDownloadUrlsByPlatformArch(
                                releaseInfo.assetNames,
                                releaseInfo.version,
                                "$clientPlatform-$clientArch",
                            )
                        } catch (e: IllegalArgumentException) {
                            return@mapNotNull null
                        }
                        UpdateInfo(
                            releaseInfo.version.toString(),
                            downloadUrls,
                            releaseInfo.publishTime,
                            releaseInfo.description,
                        )
                    },
                ),
            )
        }
        get("/latest", {
            summary = "获取最新版本下载链接"
            description = "返回最新版本的下载链接及二维码及二维码，不包括版本更新信息。"
            operationId = "getLatestVersion"
            request {
                queryParameter<String>("releaseClass") {
                    description =
                        "版本的发布类型，可选值：alpha, beta, rc, stable，默认值为 stable。不合法的发布类型会导致服务器返回 400 Bad Request 错误。"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "成功获取内容"
                    body<LatestVersionInfo> {
                        description = "最新版本版本号，发布时间，下载链接与二维码链接"
                        example("example") { value = exampleLatestVersionInfo }
                    }
                }
                HttpStatusCode.BadRequest to {
                    description = "请求参数错误"
                }
            }
        }) {
            val releaseClass = call.request.queryParameters["releaseClass"]?.let {
                ReleaseClass.fromStringOrNull(it) ?: throw BadRequestException("Invalid release class")
            } ?: ReleaseClass.STABLE

            val latest = clientReleaseInfoManager.getLatestRelease(null, releaseClass)
                ?: throw NotFoundException("No latest release found")
            val latestVersionInfo = LatestVersionInfo(
                latest.version.toString(),
                run {
                    val urlMap = latest.assetNames.filter { !it.endsWith("qrcode.png") }.mapNotNull { assetName ->
                        val platformArch = try {
                            distributionSuffixParser.getPlatformArchFromAssetName(assetName)
                        } catch (e: IllegalArgumentException) {
                            return@mapNotNull null
                        }
                        platformArch to clientReleaseInfoManager.parseDownloadUrlsByAssetName(latest.version, assetName)
                    }.toMap().toMutableMap()
                    // For old api compatibility
                    if (urlMap.containsKey("android-arm64-v8a")) {
                        urlMap["android"] = urlMap["android-arm64-v8a"]!!
                    }
                    urlMap
                },
                latest.publishTime,
                latest.assetNames.filter { it.endsWith("qrcode.png") }.map { assetName ->
                    clientReleaseInfoManager.parseDownloadUrlsByAssetName(latest.version, assetName)
                }.flatten(),
            )

            call.respond(latestVersionInfo)
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

private val exampleLatestVersionInfo = LatestVersionInfo(
    "3.5.0",
    mapOf(
        "android" to listOf(
            "https://d.myani.org/v3.5.0/ani-3.5.0.apk",
            "https://mirror.ghproxy.com/?q=https://github.com/open-ani/ani/releases/download/v3.5.0/ani-3.5.0.apk"
        ),
        "windows-x86_64" to listOf(
            "https://d.myani.org/v3.5.0/ani-3.5.0-windows-x86_64.zip",
            "https://mirror.ghproxy.com/?q=https://github.com/open-ani/ani/releases/download/v3.5.0/ani-3.5.0-windows-x86_64.zip"
        ),
        "macos-x86_64" to listOf(
            "https://d.myani.org/v3.5.0/ani-3.5.0-macos-x86_64.dmg",
            "https://mirror.ghproxy.com/?q=https://github.com/open-ani/ani/releases/download/v3.5.0/ani-3.5.0-macos-x86_64.dmg"
        ),
        "macos-aarch64" to listOf(
            "https://d.myani.org/v3.5.0/ani-3.5.0-macos-aarch64.dmg",
            "https://mirror.ghproxy.com/?q=https://github.com/open-ani/ani/releases/download/v3.5.0/ani-3.5.0-macos-aarch64.dmg"
        ),
    ),
    1721869947,
    listOf(
        "https://d.myani.org/v3.5.0/ani-3.5.0.apk.cloudflare.qrcode.png",
        "https://mirror.ghproxy.com/?q=https://github.com/open-ani/ani/releases/download/v3.5.0/ani-3.5.0.apk.cloudflare.qrcode.png",
        "https://d.myani.org/v3.5.0/ani-3.5.0.apk.github.qrcode.png",
        "https://mirror.ghproxy.com/?q=https://github.com/open-ani/ani/releases/download/v3.5.0/ani-3.5.0.apk.github.qrcode.png"
    ),
)