package me.him188.ani.danmaku.server.data.mongodb

import com.mongodb.client.model.UpdateOptions
import data.model.BangumiOauthModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import me.him188.ani.danmaku.protocol.BangumiUserToken
import me.him188.ani.danmaku.server.data.BangumiOauthRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MongoBangumiOauthRepositoryImpl : BangumiOauthRepository, KoinComponent {
    private val mongoCollectionProvider: MongoCollectionProvider by inject()
    private val bangumiOauthTable = mongoCollectionProvider.bangumiOauthTable

    override suspend fun add(requestId: String, token: BangumiUserToken): Boolean {
        return bangumiOauthTable.updateOne(
            Field.of(BangumiOauthModel::requestId) eq requestId,
            (Field.of(BangumiOauthModel::userId) setTo token.userId) then
                    (Field.of(BangumiOauthModel::accessToken) setTo token.accessToken) then
                    (Field.of(BangumiOauthModel::refreshToken) setTo token.refreshToken) then
                    (Field.of(BangumiOauthModel::expiresIn) setTo token.expiresIn),
            UpdateOptions().upsert(true)
        ).wasAcknowledged()
    }

    override suspend fun getToken(requestId: String): BangumiUserToken? {
        return bangumiOauthTable.find(Field.of(BangumiOauthModel::requestId) eq requestId).firstOrNull()?.let {
            BangumiUserToken(
                userId = it.userId,
                accessToken = it.accessToken,
                refreshToken = it.refreshToken,
                expiresIn = it.expiresIn
            )
        }
    }
}