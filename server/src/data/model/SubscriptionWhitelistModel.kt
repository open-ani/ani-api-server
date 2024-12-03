package me.him188.ani.danmaku.server.data.model

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class SubscriptionWhitelistModel(
    @BsonId
    val id: ObjectId = ObjectId(),
    val url: String,
)
