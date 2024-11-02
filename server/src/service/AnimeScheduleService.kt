package me.him188.ani.danmaku.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.protocol.AnimeRecurrence
import me.him188.ani.danmaku.protocol.AnimeSchedule
import me.him188.ani.danmaku.protocol.AnimeSeason
import me.him188.ani.danmaku.protocol.AnimeSeasonId
import me.him188.ani.danmaku.protocol.OnAirAnimeInfo
import me.him188.ani.danmaku.server.util.ConcurrentMemoryCache
import me.him188.ani.danmaku.server.util.ConcurrentMemoryCacheMap
import me.him188.ani.danmaku.server.util.getOrPut
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


class AnimeScheduleService(
    private val fetcher: AnimeScheduleFetcher = BgmlistAnimeScheduleFetcher,
) {
    private val seasonsCache = ConcurrentMemoryCache<List<AnimeSeasonId>>(60.minutes)
    private val scheduleCache = ConcurrentMemoryCacheMap<AnimeSeasonId, AnimeSchedule>(60.minutes)
    private val lock = Mutex()

    suspend fun getAnimeSchedule(seasonId: AnimeSeasonId): AnimeSchedule? = scheduleCache.getOrPut(seasonId) {
        lock.withLock {
            scheduleCache[seasonId]?.let { return it }

            fetcher.fetchSchedule(seasonId) ?: return null
        }
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
}

// https://bgmlist.com/archive/2024q4
object BgmlistAnimeScheduleFetcher : AnimeScheduleFetcher {
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

    override suspend fun fetchSeasonIds(): List<AnimeSeasonId> {
        val resp = httpClient.get("https://bgmlist.com/_next/data/AvTmOI7jFk3EFuTLj3Ooa/archive.json") {
            accept(ContentType.Application.Json)
        }.body<BgmlistPagedResponse<BgmlistArchive>>()
        return resp.pageProps.seasons.mapNotNull {
            parseToAnimeYearSeasonOrNull(it)
        }
    }

    override suspend fun fetchSchedule(season: AnimeSeasonId): AnimeSchedule? {
        val seasonStr = "${season.year}q${season.season.quarterNumber}"
        val resp = try {
            httpClient.get("https://bgmlist.com/_next/data/AvTmOI7jFk3EFuTLj3Ooa/archive/${seasonStr}.json?season=${seasonStr}") {
                accept(ContentType.Application.Json)
            }.body<BgmlistPagedResponse<BgmlistAnimeSchedule>>()
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound || e.response.status == HttpStatusCode.InternalServerError) {
                return null
            }
            throw e
        }

        return AnimeSchedule(
            list = resp.pageProps.items.mapNotNull { anime ->
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
private data class BgmlistPagedResponse<T>(
    val pageProps: T
)

@Serializable
private data class BgmlistArchive(
    val seasons: List<String>,
)

@Serializable
private data class BgmlistAnimeSchedule(
    val items: List<BgmlistAnime>
)

@Serializable
private data class BgmlistAnime(
    val title: String,
    val titleTranslate: Map<String, List<String>> = emptyMap(),
    val begin: String = "",
    val broadcast: String = "",
    val end: String = "",
    val sites: List<BgmlistSite>
)

@Serializable
private data class BgmlistSite(
    val site: String = "", // name
    val id: String = "",
)

suspend fun main() {
//    println(AnimeScheduleService().getSeasons())
    println(AnimeScheduleService().getAnimeSchedule(AnimeSeasonId(2024, AnimeSeason.WINTER)))
}
