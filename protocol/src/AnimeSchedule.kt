package me.him188.ani.danmaku.protocol

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

@Serializable
data class AnimeSeasonIdList(
    val list: List<AnimeSeasonId>
)

@Serializable
data class AnimeSchedule(
    val list: List<OnAirAnimeInfo>
)

@Serializable
data class OnAirAnimeInfo(
    val bangumiId: Int,
    val name: String,
    val aliases: List<String>,
    @field:Schema(type = "String", example = "2024-07-06T13:00:00.000Z")
    val begin: String? = null, // "2024-07-06T13:00:00.000Z"
    val recurrence: AnimeRecurrence? = null, // "R/2024-07-06T13:00:00.000Z/P7D"
    @field:Schema(type = "String")
    val end: String? = null, // "2024-09-14T14:00:00.000Z"
    val mikanId: Int?,
)

@Serializable
data class AnimeRecurrence(
    @field:Schema(type = "String")
    val startTime: String,
    val intervalMillis: Long,
)

@Serializable
enum class AnimeSeason(val quarterNumber: Int) {
    SPRING(1), // 1
    SUMMER(2), // 4
    AUTUMN(3), // 7
    WINTER(4), // 10
    ;

    companion object {
        fun fromQuarterNumber(number: Int) = entries.find { it.quarterNumber == number }
    }
}

@Serializable
data class AnimeSeasonId(
    val year: Int,
    val season: AnimeSeason,
) : Comparable<AnimeSeasonId> {
    // serialized
    val id: String = "${year}q${season.quarterNumber}"

    companion object {
        private val COMPARATOR = compareBy<AnimeSeasonId> { it.year }
            .thenBy { it.season }

        fun parseOrNull(string: String): AnimeSeasonId? {
            if (!string.contains("q")) {
                return null
            }
            return AnimeSeasonId(
                year = string.substringBefore('q').toIntOrNull() ?: return null,
                season = AnimeSeason.fromQuarterNumber(
                    string.substringAfter('q').toIntOrNull() ?: return null,
                ) ?: return null,
            )
        }
    }

    override fun compareTo(other: AnimeSeasonId): Int = COMPARATOR.compare(this, other)
}
