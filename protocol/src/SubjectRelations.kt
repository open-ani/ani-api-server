package me.him188.ani.danmaku.protocol

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

@Serializable
data class SubjectRelations(
    val subjectId: Int,
    @field:Schema(
        description = """The main anime subjects of this series, sorted by order of seasons.
For example, the first element is the first season, the second element is the second season, and so on.""",
    )
    val seriesMainSubjectIds: List<Int>,
    @field:Schema(
        description = """该条目的续作 ID 列表 (递归查询), 包含正片和所有其他类型 (OVA, 特别篇, 剧场版等) 的续作.""",
    )
    val sequelSubjects: List<Int>,
//    /**
//     * 该条目的系列正片 ID 列表
//     */
//    val seriesMainSubjects: List<Int>,
)
