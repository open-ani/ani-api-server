package me.him188.ani.danmaku.server.data.model

import org.bson.codecs.pojo.annotations.BsonId
import java.util.UUID

class SubscriptionWhitelistModel(
    @BsonId
    val id: String = UUID.randomUUID().toString(),
    val url: String,
)
