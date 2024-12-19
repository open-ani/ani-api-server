package me.him188.ani.danmaku.protocol

import kotlinx.serialization.Serializable

@Serializable
data class SubjectRelations(
    val subjectId: Int,
    /**
     * 该条目的续作 ID 列表
     */
    val sequelSubjects: List<Int>,
//    /**
//     * 该条目的系列正片 ID 列表
//     */
//    val seriesMainSubjects: List<Int>,
)
