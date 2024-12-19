package me.him188.ani.danmaku.protocol

import kotlinx.serialization.Serializable

@Serializable
data class SubjectRelations(
    val subjectId: Int,
    val relatedSubjects: Set<Int>,
)
