package data.model;

import me.him188.ani.danmaku.protocol.DanmakuLocation
import org.bson.codecs.pojo.annotations.BsonId
import java.time.LocalDateTime
import java.util.UUID

data class BangumiOauthModel(
    @BsonId
    val id: UUID = UUID.randomUUID(),
    val requestId: String,  // unique index
    val createTime: LocalDateTime = LocalDateTime.now(),
    val userId: Int,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,  // in seconds; stores the expiry time of the access token, not the expiry time of this entry
)
