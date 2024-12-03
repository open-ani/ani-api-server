package me.him188.ani.danmaku.server.service

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.server.data.model.SubscriptionWhitelistModel
import me.him188.ani.danmaku.server.data.mongodb.MongoCollectionProvider
import me.him188.ani.danmaku.server.data.mongodb.findBy
import me.him188.ani.danmaku.server.util.ConcurrentMemoryCacheMap
import me.him188.ani.danmaku.server.util.exception.UnprocessableEntityException
import me.him188.ani.danmaku.server.util.getOrPut
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

class SubscriptionProxyService : KoinComponent {
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                Json {
                    ignoreUnknownKeys = true
                }
            }
            expectSuccess = true
        }
    }

    private val database: MongoCollectionProvider by inject()
    private val whitelistCollection by lazy { database.subscriptionWhitelist }
    private val cache = ConcurrentMemoryCacheMap<String, String>(10.minutes)
    private val mutex = Mutex()

    suspend fun getSubscriptionData(url: String): String {
        @Suppress("NAME_SHADOWING")
        val url = url.removeSuffix("/")

        val first = whitelistCollection.findBy {
            SubscriptionWhitelistModel::url eq url
        }.firstOrNull()

        if (first == null) {
            // not in whitelist
            throw UnprocessableEntityException("URL not allowed")
        }

        return mutex.withLock {
            cache.getOrPut(url) {
                httpClient.get(url).bodyAsText()
            }
        }
    }
}