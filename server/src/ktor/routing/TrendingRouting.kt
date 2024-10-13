package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import me.him188.ani.danmaku.protocol.Trending
import me.him188.ani.danmaku.protocol.TrendingSubject

val TRENDING_SUBJECTS: List<TrendingSubject> = listOf(
    TrendingSubject(
        bangumiId = 464376,
        nameCn = "败犬女主太多了！",
        imageLarge = "https://lain.bgm.tv/pic/cover/l/e4/dc/464376_NsZRw.jpg",
    ),
    TrendingSubject(
        bangumiId = 425998,
        nameCn = "Re：从零开始的异世界生活 第三季 袭击篇",
        imageLarge = "https://lain.bgm.tv/pic/cover/l/26/d6/425998_dnzr8.jpg",
    ),
)

fun Route.trending() {
    route(
        "/trending",
        {
            tags("Trending")
        },
    ) {
        get(
            {
                summary = "获取热门排行"
                description = "获取热门排行"
                operationId = "getTrending"
                response {
                    HttpStatusCode.OK to {
                        description = "成功获取热门排行"
                        body<Trending> {
                            description = "热门排行数据"
                            example("example") {
                                value = Trending(
                                    trendingSubjectIds = TRENDING_SUBJECTS,
                                )
                            }
                        }
                    }
                }
            },
        ) {
            call.respond(
                HttpStatusCode.OK,
                Trending(
                    trendingSubjectIds = TRENDING_SUBJECTS,
                ),
            )
        }
    }
}
