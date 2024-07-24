package me.him188.ani.danmaku.server.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.danmaku.protocol.BangumiUserToken

interface BangumiOauthRepository {
    suspend fun add(requestId: String, token: BangumiUserToken): Boolean
    suspend fun getToken(requestId: String): BangumiUserToken?
}

class InMemoryBangumiOauthRepositoryImpl : BangumiOauthRepository {
    private val tokens = mutableMapOf<String, BangumiUserToken>()
    private val mutex = Mutex()

    override suspend fun add(requestId: String, token: BangumiUserToken): Boolean {
        mutex.withLock {
            if (tokens.containsKey(requestId)) {
                tokens.remove(requestId)
            }
            tokens[requestId] = token
            return true
        }
    }

    override suspend fun getToken(requestId: String): BangumiUserToken? {
        mutex.withLock {
            return tokens[requestId]
        }
    }
}