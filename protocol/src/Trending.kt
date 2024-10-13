package me.him188.ani.danmaku.protocol

import kotlinx.serialization.Serializable

@Serializable
data class Trending(
    val trendingSubjects: List<TrendingSubject>,
)

@Serializable
data class TrendingSubject(
    val bangumiId: Int,
    val nameCn: String,
    val imageLarge: String,
)
