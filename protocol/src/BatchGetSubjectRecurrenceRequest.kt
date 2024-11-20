package me.him188.ani.danmaku.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BatchGetSubjectRecurrenceRequest(
    val bangumiIds: List<Int>,
)

@Serializable
data class BatchGetSubjectRecurrenceResponse(
    val recurrences: List<AnimeRecurrence?>,
)
