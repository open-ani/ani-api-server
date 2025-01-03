package me.him188.ani.danmaku.server.ktor.routing

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import me.him188.ani.danmaku.protocol.SubjectRelations
import me.him188.ani.danmaku.server.service.SubjectRelationService
import me.him188.ani.danmaku.server.util.toList
import org.koin.ktor.ext.inject


fun Route.subjectRelationRouting() {
    val subjectRelationService: SubjectRelationService by inject()

    route(
        "/subject-relations",
        {
            tags("Subject Relations")
        },
    ) {
        get(
            "/{subjectId}",
            {
                summary = "获取关联条目"
                description = "获取关联条目"
                operationId = "getSubjectRelations"
                request {
                    pathParameter<String>("subjectId")
                }
                response {
                    HttpStatusCode.OK to {
                        description = "成功获取关联条目"
                        body<SubjectRelations>()
                    }
                }
            },
        ) {
            val subjectId = call.parameters.getOrFail("subjectId").toInt()
            val index = subjectRelationService.getSubjectRelationIndex(subjectId)
            call.respond(
                HttpStatusCode.OK,
                if (index == null) {
                    SubjectRelations(subjectId, emptyList(), emptyList())
                } else {
                    SubjectRelations(
                        subjectId = subjectId,
                        seriesMainSubjectIds = index.seriesMainAnimeSubjectIds.toList(),
                        sequelSubjects = index.sequelAnimeSubjectIds.toList(),
//                        seriesMainSubjects = index.seriesMainAnimeSubjectIds.toList(),
                    )
                },
            )
        }
    }
}
