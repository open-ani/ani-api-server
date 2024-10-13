package me.him188.ani.danmaku.server.service

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.protocol.TrendingSubject
import me.him188.ani.danmaku.protocol.Trends
import me.him188.ani.danmaku.server.util.ConcurrentMemoryCache
import me.him188.ani.danmaku.server.util.getOrPut
import org.jsoup.Jsoup
import kotlin.time.Duration.Companion.minutes

class BangumiTrendsService {
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                Json {
                    ignoreUnknownKeys = true
                }
            }
        }
    }

    private val cache = ConcurrentMemoryCache<Trends>(60.minutes)
    private val lock = Mutex()

    /**
     * Either from cache or fetch from Bangumi (locked)
     */
    suspend fun getTrends(): Trends = cache.getOrPut {
        lock.withLock {
            cache.get()?.let { return it }

            val subjects = httpClient
                .get("https://bangumi.tv/anime/browser/?sort=trends")
                .bodyAsChannel()
                .toInputStream().let {
                    Jsoup.parse(it, "UTF-8", "https://bangumi.tv")
                }
                .select("#browserItemList li.item h3 a")
                .mapNotNull { element ->
                    val id = element.attr("href").substringAfterLast("/").toIntOrNull()
                        ?: return@mapNotNull null

                    TrendingSubject(
                        bangumiId = id,
                        nameCn = element.text(),
                        imageLarge = "https://api.bgm.tv/v0/subjects/${id}/image?type=large",
                    )
                }

            Trends(subjects)
        }
    }
}

//suspend fun main() {
//    println(BangumiTrendsService().getTrending())
//}
