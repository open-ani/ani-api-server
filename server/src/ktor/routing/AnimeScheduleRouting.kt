package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import me.him188.ani.danmaku.protocol.AnimeRecurrence
import me.him188.ani.danmaku.protocol.AnimeSchedule
import me.him188.ani.danmaku.protocol.AnimeSeason
import me.him188.ani.danmaku.protocol.AnimeSeasonId
import me.him188.ani.danmaku.protocol.AnimeSeasonIdList
import me.him188.ani.danmaku.protocol.BatchGetSubjectRecurrenceResponse
import me.him188.ani.danmaku.protocol.OnAirAnimeInfo
import me.him188.ani.danmaku.server.service.AnimeScheduleService
import me.him188.ani.danmaku.server.util.exception.BadRequestException
import org.koin.ktor.ext.inject

fun Route.animeScheduleRouting() {
    val scheduleService: AnimeScheduleService by inject()
    route(
        "/schedule",
        {
            tags("Schedule")
        },
    ) {
        get(
            "seasons",
            {
                summary = "获取新番季度列表"
                description = "获取新番季度列表"
                operationId = "getAnimeSeasons"
                response {
                    HttpStatusCode.OK to {
                        description = "获取成功"
                        body<AnimeSeasonIdList> {
                            description = "新番季度列表, 保证由新到旧排序"
                            example("example") {
                                value = AnimeSeasonIdList(
                                    listOf(
                                        AnimeSeasonId(2024, AnimeSeason.AUTUMN),
                                        AnimeSeasonId(2024, AnimeSeason.SUMMER),
                                        AnimeSeasonId(2024, AnimeSeason.SPRING),
                                        AnimeSeasonId(2024, AnimeSeason.WINTER),
                                        AnimeSeasonId(2023, AnimeSeason.AUTUMN),
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) {
            call.respond(
                HttpStatusCode.OK,
                AnimeSeasonIdList(scheduleService.getSeasonIds()),
            )
        }
        get(
            "season/{seasonId}",
            {
                summary = "获取一个季度的新番时间表"
                description = "获取一个季度的新番时间表"
                operationId = "getAnimeSeason"
                request {
                    pathParameter<String>("seasonId") {
                        description =
                            "格式为 \"{年份}q{季度序号}\". 例如 \"2024q3\". 季度序号范围为 1..3 (包含), 分别对应春季, 夏季, 秋季, 冬季"
                        example("example") {
                            value = "2024q3"
                        }
                    }
                }
                response {
                    code(HttpStatusCode.OK) {
                        description = "获取成功"
                        body<AnimeSchedule> {
                            description = "该季度的新番时间表, 无排序"
                            example("example") {
                                value = AnimeSchedule(
                                    list = listOf(
                                        OnAirAnimeInfo(
                                            bangumiId = 404480,
                                            name = "ラブライブ！スーパースター!!(第3期)",
                                            aliases = listOf(
                                                "Love Live ! Superstar!!",
                                                "Love Live! Superstar!! 第三季",
                                                "爱与演唱会!超级明星!! 第三季",
                                                "Love Live! Superstar!! 第三季",
                                                "LoveLive! SuperStar!! 第三季",
                                            ),
                                            begin = Instant.parse("2024-10-06T08:00:00Z").toString(),
                                            recurrence = AnimeRecurrence(
                                                startTime = Instant.parse("2024-10-06T08:00:00Z").toString(),
                                                intervalMillis = 604800000,
                                            ),
                                            end = null,
                                            mikanId = 3427,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                    code(HttpStatusCode.NotFound) {
                        description = "未找到对应季度"
                    }
                    code(HttpStatusCode.BadRequest) {
                        description = "seasonId 格式有误"
                    }
                }
            },
        ) {
            val seasonIdString = call.parameters["seasonId"] ?: ""
            val seasonId = AnimeSeasonId.parseOrNull(seasonIdString) ?: kotlin.run {
                call.respond(HttpStatusCode.BadRequest, "Invalid seasonId: $seasonIdString")
                return@get
            }
            val schedule = scheduleService.getAnimeSchedule(seasonId)
            if (schedule == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    schedule,
                )
            }
        }

        get(
            "subjects",
            {
                summary = "查询一些条目的连载信息"
                description = "查询一些条目的连载信息"
                operationId = "getSubjectRecurrences"
                request {
                    queryParameter<List<Int>>("ids") {
                        description = "需要查询的条目 ID 列表, 以英文逗号分隔."
                        example("example") {
                            value = listOf(404480, 123123123)
                        }
                    }
                }
                response {
                    code(HttpStatusCode.OK) {
                        description = "获取成功"
                        body<BatchGetSubjectRecurrenceResponse> {
                            description =
                                "条目的连载信息. 每个元素按顺序分别对应请求中的条目 ID, null 表示未找到对应条目."
                            example("example") {
                                value = BatchGetSubjectRecurrenceResponse(
                                    listOf(
                                        AnimeRecurrence(
                                            startTime = Instant.parse("2024-10-06T08:00:00Z").toString(),
                                            intervalMillis = 604800000,
                                        ),
                                        null,
                                    ),
                                )
                            }
                        }
                    }
                    code(HttpStatusCode.BadRequest) {
                        description = "body 内容格式有误"
                    }
                }
            },
        ) {
            val req = call.parameters.getAll("ids").orEmpty()
                .asSequence()
                .flatMap { it.split(",") }
                .map { it.toIntOrNull() ?: throw BadRequestException("Parameter `ids` contains illegal element: $it") }
                .toList()
            val recurrences = scheduleService.getSubjectRecurrences()
            call.respond(
                HttpStatusCode.OK,
                BatchGetSubjectRecurrenceResponse(
                    req.map { id ->
                        recurrences[id]
                    },
                ),
            )
        }
    }
}
