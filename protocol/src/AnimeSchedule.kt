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
enum class AnimeSeason(val quarterNumber: Int, val monthRange: Set<Int>) {
    WINTER(1, setOf(12, 1, 2)), // 1
    SPRING(2, setOf(3, 4, 5)), // 4
    SUMMER(3, setOf(6, 7, 8)), // 7
    AUTUMN(4, setOf(9, 10, 11)), // 10
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

    val yearMonthRanges
        get() = when (season) {
            AnimeSeason.WINTER -> listOf(year - 1 to 12, year to 11, year to 10)
            AnimeSeason.SPRING -> listOf(year to 3, year to 4, year to 5)
            AnimeSeason.SUMMER -> listOf(year to 6, year to 7, year to 8)
            AnimeSeason.AUTUMN -> listOf(year to 9, year to 10, year to 11)
        }

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

        private val monthLookUpTable = arrayOfNulls<AnimeSeason>(13).apply {
            for (season in AnimeSeason.entries) {
                for (month in season.monthRange) {
                    this[month] = season
                }
            }
        }

        fun fromDate(year: Int, month: Int): AnimeSeasonId {
            if (month == 12) {
                return AnimeSeasonId(year + 1, AnimeSeason.WINTER)
            }
            require(month in 1..12) { "Invalid month: $month" }
            return AnimeSeasonId(year, monthLookUpTable[month]!!)
//            return when (month) {
//                1, 2 -> AnimeSeasonId(year, AnimeSeason.WINTER)
//                12 -> AnimeSeasonId(year + 1, AnimeSeason.WINTER)
//                3, 4, 5 -> AnimeSeasonId(year, AnimeSeason.SPRING)
//                6, 7, 8 -> AnimeSeasonId(year, AnimeSeason.SUMMER)
//                9, 10, 11 -> AnimeSeasonId(year, AnimeSeason.AUTUMN)
//                else -> throw IllegalArgumentException("Invalid month: $month")
//            }
        }
    }

    override fun compareTo(other: AnimeSeasonId): Int = COMPARATOR.compare(this, other)
}
