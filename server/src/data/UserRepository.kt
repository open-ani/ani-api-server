package me.him188.ani.danmaku.server.data

import kotlinx.coroutines.flow.firstOrNull
import me.him188.ani.danmaku.protocol.AniUser
import me.him188.ani.danmaku.server.data.model.UserModel
import me.him188.ani.danmaku.server.data.mongodb.Field
import me.him188.ani.danmaku.server.data.mongodb.MongoCollectionProvider
import me.him188.ani.danmaku.server.data.mongodb.then
import me.him188.ani.danmaku.server.util.exception.OperationFailedException
import org.bson.conversions.Bson
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class UserRepository : KoinComponent {
    private val mongoCollectionProvider: MongoCollectionProvider by inject()
    private val userTable = mongoCollectionProvider.userTable

    suspend fun getUserIdOrNull(bangumiId: Int): String? {
        return userTable.find(
            Field("bangumiUserId") eq bangumiId,
        ).firstOrNull()?.id?.toString()
    }

    suspend fun addAndGetId(
        bangumiId: Int,
        nickname: String,
        smallAvatar: String,
        mediumAvatar: String,
        largeAvatar: String,
        clientVersion: String? = null,
    ): String? {
        val now = System.currentTimeMillis()
        val user = UserModel(
            bangumiUserId = bangumiId,
            nickname = nickname,
            smallAvatar = smallAvatar,
            mediumAvatar = mediumAvatar,
            largeAvatar = largeAvatar,
            registerTime = now,
            lastLoginTime = now,
            clientVersion = clientVersion,
        )
        return if (userTable.insertOne(user).wasAcknowledged()) {
            user.id.toString()
        } else {
            null
        }
    }

    suspend fun getBangumiId(userId: String): Int? {
        return userTable.find(
            Field.Id eq UUID.fromString(userId),
        ).firstOrNull()?.bangumiUserId
    }

    suspend fun getNickname(userId: String): String? {
        return getUserById(userId)?.nickname
    }

    suspend fun getSmallAvatar(userId: String): String? {
        return getUserById(userId)?.smallAvatar
    }

    suspend fun getMediumAvatar(userId: String): String? {
        return getUserById(userId)?.mediumAvatar
    }

    suspend fun getLargeAvatar(userId: String): String? {
        return getUserById(userId)?.largeAvatar
    }

    suspend fun getUserById(userId: String): AniUser? {
        val user = userTable.find(
            Field.Id eq UUID.fromString(userId),
        ).firstOrNull() ?: return null

        var updates: Bson? = null
        val registerTime = user.registerTime ?: AniUser.MAGIC_REGISTER_TIME.also {
            updates = updates then (Field.of(UserModel::registerTime) setTo it)
        }
        val lastLoginTime = user.lastLoginTime ?: System.currentTimeMillis().also {
            updates = updates then (Field.of(UserModel::lastLoginTime) setTo it)
        }
        updates?.let {
            if (!userTable.updateOne(Field.Id eq UUID.fromString(userId), it).wasAcknowledged()) {
                throw OperationFailedException()
            }
        }

        return AniUser(
            id = user.id.toString(),
            nickname = user.nickname,
            smallAvatar = user.smallAvatar,
            mediumAvatar = user.mediumAvatar,
            largeAvatar = user.largeAvatar,
            registerTime = registerTime,
            lastLoginTime = lastLoginTime,
            clientVersion = user.clientVersion,
            clientPlatforms = user.clientPlatforms?.toSet() ?: emptySet(),
        )
    }

    suspend fun setLastLoginTime(userId: String, time: Long): Boolean {
        return userTable.updateOne(
            Field.Id eq UUID.fromString(userId),
            Field.of(UserModel::lastLoginTime) setTo time,
        ).wasAcknowledged()
    }

    suspend fun setClientVersion(userId: String, clientVersion: String) {
        userTable.updateOne(
            Field.Id eq UUID.fromString(userId),
            Field.of(UserModel::clientVersion) setTo clientVersion,
        )
    }

    suspend fun addClientPlatform(userId: String, clientPlatform: String) {
        userTable.updateOne(
            Field.Id eq UUID.fromString(userId),
            Field.of(UserModel::clientPlatforms) addToSet clientPlatform,
        )
    }
}