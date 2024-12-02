package me.him188.ani.danmaku.server.data.mongodb

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import data.model.BangumiOauthModel
import kotlinx.coroutines.flow.toList
import me.him188.ani.danmaku.server.ServerConfig
import me.him188.ani.danmaku.server.data.model.DanmakuModel
import me.him188.ani.danmaku.server.data.model.SubscriptionWhitelistModel
import me.him188.ani.danmaku.server.data.model.UserModel
import me.him188.ani.danmaku.server.ktor.plugins.ServerJson
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes


interface MongoCollectionProvider {
    val danmakuTable: MongoCollection<DanmakuModel>
    val userTable: MongoCollection<UserModel>
    val bangumiOauthTable: MongoCollection<BangumiOauthModel>
    val subscriptionWhitelist: MongoCollection<SubscriptionWhitelistModel>
}

class MongoCollectionProviderImpl : MongoCollectionProvider, KoinComponent {
    private val connectionString: String = get<ServerConfig>().mongoDbConnectionString
        ?: throw IllegalStateException("MongoDB connection string is not set")

    private val client = MongoClient.create(
        MongoClientSettings.builder().apply {
            applyConnectionString(ConnectionString(connectionString))
            codecRegistry(
                CodecRegistries.fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    ServerCodecRegistry(ServerJson),
                ),
            )
            uuidRepresentation(UuidRepresentation.STANDARD)
        }.build(),
    )

    private val db = client.getDatabase("ani-production")
    override val danmakuTable = db.getCollection<DanmakuModel>("danmaku")
    override val userTable = db.getCollection<UserModel>("user")
    override val bangumiOauthTable = db.getCollection<BangumiOauthModel>("bangumi-oauth")
    override val subscriptionWhitelist: MongoCollection<SubscriptionWhitelistModel> =
        db.getCollection<SubscriptionWhitelistModel>("sub-whitelist")

    suspend fun buildIndex() {
        if (danmakuTable.listIndexes().toList().size == 1) {
            danmakuTable.createIndex(
                Indexes.ascending(DanmakuModel::episodeId.name),
            )
        }
        if (userTable.listIndexes().toList().size == 1) {
            userTable.createIndex(
                Indexes.ascending(UserModel::bangumiUserId.name),
                IndexOptions().unique(true),
            )
        }
        if (bangumiOauthTable.listIndexes().toList().size == 1) {
            bangumiOauthTable.createIndex(
                Indexes.ascending(BangumiOauthModel::requestId.name),
                IndexOptions().unique(true),
            )
            bangumiOauthTable.createIndex(
                Indexes.ascending(BangumiOauthModel::createTime.name),
                IndexOptions().expireAfter(1.minutes.inWholeSeconds, TimeUnit.SECONDS),
            )
        }
    }
}
