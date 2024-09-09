package me.him188.ani.danmaku.server.service

import io.ktor.server.plugins.NotFoundException
import me.him188.ani.danmaku.protocol.AniUser
import me.him188.ani.danmaku.server.data.UserRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UserService : KoinComponent {
    private val userRepository: UserRepository by inject()

    suspend fun getBangumiId(userId: String): Int {
        return userRepository.getBangumiId(userId) ?: throw NotFoundException()
    }

    suspend fun getNickname(userId: String): String {
        return userRepository.getNickname(userId) ?: throw NotFoundException()
    }

    suspend fun getAvatar(userId: String, size: AvatarSize): String {
        return when (size) {
            AvatarSize.SMALL -> userRepository.getSmallAvatar(userId)
            AvatarSize.MEDIUM -> userRepository.getMediumAvatar(userId)
            AvatarSize.LARGE -> userRepository.getLargeAvatar(userId)
        } ?: throw NotFoundException()
    }

    suspend fun getUser(userId: String): AniUser {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException()
        return user
    }
}

enum class AvatarSize {
    SMALL, MEDIUM, LARGE
}