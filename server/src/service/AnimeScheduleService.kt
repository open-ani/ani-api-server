package me.him188.ani.danmaku.server.service

import androidx.collection.IntObjectMap
import androidx.collection.mutableIntObjectMapOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.him188.ani.danmaku.protocol.AnimeRecurrence
import me.him188.ani.danmaku.protocol.AnimeSchedule
import me.him188.ani.danmaku.protocol.AnimeSeason
import me.him188.ani.danmaku.protocol.AnimeSeasonId
import me.him188.ani.danmaku.protocol.OnAirAnimeInfo
import me.him188.ani.danmaku.protocol.yearMonths
import me.him188.ani.danmaku.server.util.ConcurrentMemoryCache
import me.him188.ani.danmaku.server.util.ConcurrentMemoryCacheMap
import me.him188.ani.danmaku.server.util.getOrPut
import me.him188.ani.danmaku.server.util.query.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


class AnimeScheduleService(
    private val fetcher: AnimeScheduleFetcher = BangumiDataAnimeScheduleFetcher,
) {
    private val subjectRecurrencesCache = ConcurrentMemoryCache<IntObjectMap<AnimeRecurrence>>(60.minutes)
    private val seasonsCache = ConcurrentMemoryCache<List<AnimeSeasonId>>(60.minutes)
    private val scheduleCache = ConcurrentMemoryCacheMap<AnimeSeasonId, AnimeSchedule>(60.minutes)
    private val lock = Mutex()
    private val getSubjectRecurrencesLock = Mutex()

    suspend fun getSubjectRecurrences(): IntObjectMap<AnimeRecurrence> =
        getSubjectRecurrencesLock.withLock {
            subjectRecurrencesCache.getOrPut {
                fetcher.fetchSubjectRecurrences()
            }
        }

    suspend fun getAnimeSchedule(seasonId: AnimeSeasonId): AnimeSchedule? = scheduleCache.getOrPut(seasonId) {
        // 不要锁, 否则失去并发
        scheduleCache[seasonId]?.let { return it }

        fetcher.fetchSchedule(seasonId) ?: return null
    }

    suspend fun getSeasonIds(): List<AnimeSeasonId> = seasonsCache.getOrPut {
        lock.withLock {
            seasonsCache.get()?.let { return it }

            fetcher.fetchSeasonIds().sortedDescending()
        }
    }

}

interface AnimeScheduleFetcher {
    suspend fun fetchSeasonIds(): List<AnimeSeasonId>
    suspend fun fetchSchedule(season: AnimeSeasonId): AnimeSchedule?
    suspend fun fetchSubjectRecurrences(): IntObjectMap<AnimeRecurrence>
}

//https://github.com/bangumi-data/bangumi-data
object BangumiDataAnimeScheduleFetcher : AnimeScheduleFetcher {
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
            install(UserAgent) {
                agent = "open-ani/animeko/4.0.0 (server) (https://github.com/open-ani/animeko)"
            }
            install(HttpCache)
            followRedirects = true
            expectSuccess = true
        }
    }

    private suspend fun <T> HttpResponse.bodyDeserialized(deserializationStrategy: DeserializationStrategy<T>): T {
        return bodyAsChannel().let { channel ->
            channel.toInputStream().use {
                json.decodeFromStream(deserializationStrategy, it)
            }
        }
    }

    override suspend fun fetchSeasonIds(): List<AnimeSeasonId> = withContext(Dispatchers.IO) {
        val resp =
            httpClient.get("https://raw.githubusercontent.com/bangumi-data/bangumi-data/refs/heads/master/dist/data.json") {
                accept(ContentType.Application.Json)
            }.bodyDeserialized(RawBangumiData.serializer())

        resp.items.mapTo(HashSet()) {
            val localDateTime = Instant.parse(it.begin).toLocalDateTime(TimeZone.of("Asia/Tokyo"))
            AnimeSeasonId.fromDate(localDateTime.year, localDateTime.monthNumber)
        }.toList()
    }

    override suspend fun fetchSchedule(season: AnimeSeasonId): AnimeSchedule = withContext(Dispatchers.IO) {
        val resp = season.yearMonths.flatMap { (year, month) ->
            getMonthDataOrNull(year, month) ?: emptyList()
        }

        AnimeSchedule(
            seasonId = season,
            list = resp.mapNotNull { anime ->
                OnAirAnimeInfo(
                    bangumiId = anime.sites.find { it.site == "bangumi" }?.id?.toIntOrNull() ?: return@mapNotNull null,
                    name = anime.title.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                    aliases = anime.titleTranslate.values.flatten(),
                    begin = parseToInstant(anime.begin)?.toString(),
                    recurrence = parseRecurrence(anime.broadcast),
                    end = parseToInstant(anime.end)?.toString(),
                    mikanId = anime.sites.find { it.site == "mikan" }?.id?.toIntOrNull(),
                )
            },
        )
    }

    override suspend fun fetchSubjectRecurrences(): IntObjectMap<AnimeRecurrence> = withContext(Dispatchers.IO) {
        val resp =
            httpClient.get("https://raw.githubusercontent.com/bangumi-data/bangumi-data/refs/heads/master/dist/data.json") {
                accept(ContentType.Application.Json)
            }.bodyDeserialized(RawBangumiData.serializer())

        mutableIntObjectMapOf<AnimeRecurrence>().apply {
            for (item in resp.items) {
                val bangumiId = item.sites.find { it.site == "bangumi" }?.id?.toIntOrNull() ?: continue
                put(
                    bangumiId,
                    parseRecurrence(item.broadcast) ?: continue,
                )
            }
        }
    }

    private suspend fun getMonthDataOrNull(year: Int, month: Int): List<BangumiDataAnime>? {
        try {
            return httpClient.get(
                "https://raw.githubusercontent.com/bangumi-data/bangumi-data/refs/heads/master/data/items" +
                        "/$year" +
                        "/${month.toString().padStart(2, '0')}" +
                        ".json",
            ) {
                accept(ContentType.Application.Json)
            }.bodyDeserialized(ListSerializer(BangumiDataAnime.serializer()))
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound || e.response.status == HttpStatusCode.InternalServerError) {
                return null
            }
            throw e
        }
    }

    private fun parseToInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    // Function to parse the recurrence string and return a Recurrence instance
    private fun parseRecurrence(recurrenceString: String): AnimeRecurrence? {
        val parts = recurrenceString.split("/")
        if (parts.size != 3) return null

        return AnimeRecurrence(
            startTime = Instant.parse(parts[1]).toString(),
            intervalMillis = Duration.parse(parts[2]).inWholeMilliseconds,
        )
    }


    private fun parseToAnimeYearSeasonOrNull(value: String): AnimeSeasonId? {
        if (!value.contains('q')) {
            return null
        }
        val year = value.substringBefore('q').toIntOrNull() ?: return null
        val season = value.substringAfterLast('q').toIntOrNull() ?: return null
        val quarter = AnimeSeason.fromQuarterNumber(season) ?: return null
        return AnimeSeasonId(year, quarter)
    }
}

@Serializable
private data class BangumiDataAnime(
    val title: String,
    val titleTranslate: Map<String, List<String>> = emptyMap(),
    val begin: String = "",
    val broadcast: String = "",
    val end: String = "",
    val sites: List<BangumiDataAnimeSite>
)

@Serializable
private data class BangumiDataAnimeSite(
    val site: String = "", // name
    val id: String = "",
)

suspend fun main() {
//    println(AnimeScheduleService().getSeasonIds())
//    println(AnimeScheduleService().getAnimeSchedule(AnimeSeasonId(2024, AnimeSeason.WINTER)))
    println(AnimeScheduleService().getSubjectRecurrences())
}

///////////////////////////////////////////////////////////////////////////
// Raw
///////////////////////////////////////////////////////////////////////////

@Serializable
private data class RawBangumiData(
    val items: List<BangumiDataAnime>
)
