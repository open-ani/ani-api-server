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
import me.him188.ani.danmaku.protocol.OnAirAnimeInfo
import me.him188.ani.danmaku.server.service.AnimeScheduleService
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
                                        AnimeSeasonId(2024, AnimeSeason.WINTER),
                                        AnimeSeasonId(2024, AnimeSeason.AUTUMN),
                                        AnimeSeasonId(2024, AnimeSeason.SUMMER),
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
                response {
                    HttpStatusCode.OK to {
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
                                            begin = Instant.parse("2024-10-06T08:00:00Z"),
                                            recurrence = AnimeRecurrence(
                                                startTime = Instant.parse("2024-10-06T08:00:00Z"),
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
    }
}
