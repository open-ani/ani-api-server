package me.him188.ani.danmaku.server.service

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.userAgent
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
            followRedirects = false
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

                    SubjectMetadata(id = id, nameCn = element.text())
                }
            
            val result = coroutineScope { 
                val semaphore = Semaphore(8)
                subjects.map { s ->
                    async { 
                        val lainImageUrl = semaphore.withPermit { getImageLainUrl(s.id) }
                        TrendingSubject(
                            bangumiId = s.id,
                            nameCn = s.nameCn,
                            imageLarge = lainImageUrl ?: "https://api.bgm.tv/v0/subjects/${s.id}/image?type=large",
                        )
                    }
                }
            }

            Trends(awaitAll(*result.toTypedArray()))
        }
    }
    
    private suspend fun getImageLainUrl(subjectId: Int): String? {
        val resp = withContext(Dispatchers.IO) {
            httpClient.get("https://api.bgm.tv/v0/subjects/$subjectId/image?type=large") {
                userAgent(UA)
            }
        }
        if (resp.status == HttpStatusCode.Found) {
            return resp.headers[HttpHeaders.Location]
        }
        return null
    }
    
    private data class SubjectMetadata(val id: Int, val nameCn: String)
    
    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

//suspend fun main() {
//    println(BangumiTrendsService().getTrending())
//}
