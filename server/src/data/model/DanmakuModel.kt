package me.him188.ani.danmaku.server.data.model

import me.him188.ani.danmaku.protocol.DanmakuLocation
import org.bson.codecs.pojo.annotations.BsonId
import java.util.UUID


data class DanmakuModel(
    @BsonId
    val id: UUID = UUID.randomUUID(),
    val senderId: String,
    val episodeId: String, // index
    val playTime: Long,
    val location: DanmakuLocation,
    val text: String,
    val color: Int,
    // NOTE: 不要加字段. 新加的字段的默认值并不能在反序列化时起作用, 也就会导致无法反序列化旧数据
    val sendTime: Long = System.currentTimeMillis(),
    val complaintCount: Int = 0,
)