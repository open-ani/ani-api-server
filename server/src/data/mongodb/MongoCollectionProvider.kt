package me.him188.ani.danmaku.server.data.mongodb

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import data.model.BangumiOauthModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.server.ServerConfig
import me.him188.ani.danmaku.server.data.model.DanmakuModel
import me.him188.ani.danmaku.server.data.model.UserModel
import me.him188.ani.danmaku.server.ktor.plugins.ServerJson
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes


interface MongoCollectionProvider {
    val danmakuTable: MongoCollection<DanmakuModel>
    val userTable: MongoCollection<UserModel>
    val bangumiOauthTable: MongoCollection<BangumiOauthModel>
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

    init {
        val coroutineScope = get<CoroutineScope>(named("topCoroutineScope"))
        coroutineScope.launch {
            buildIndex()
        }
    }

    private val db = client.getDatabase("ani-production")
    override val danmakuTable = db.getCollection<DanmakuModel>("danmaku")
    override val userTable = db.getCollection<UserModel>("user")
    override val bangumiOauthTable = db.getCollection<BangumiOauthModel>("bangumi-oauth")

    private suspend fun buildIndex() {
        if (danmakuTable.listIndexes().toList().isNotEmpty()) {
            danmakuTable.createIndex(
                Indexes.ascending(DanmakuModel::episodeId.name),
            )
        }
        if (userTable.listIndexes().toList().isNotEmpty()) {
            userTable.createIndex(
                Indexes.ascending(UserModel::bangumiUserId.name),
                IndexOptions().unique(true),
            )
        }
        if (bangumiOauthTable.listIndexes().toList().isNotEmpty()) {
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
