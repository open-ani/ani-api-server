package me.him188.ani.danmaku.server.service

import me.him188.ani.danmaku.protocol.BangumiUserToken
import me.him188.ani.danmaku.server.ServerConfig
import me.him188.ani.danmaku.server.data.BangumiOauthRepository
import me.him188.ani.danmaku.server.data.UserRepository
import me.him188.ani.danmaku.server.util.exception.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URLEncoder

interface AuthService {
    suspend fun loginBangumi(
        bangumiToken: String,
        clientVersion: String? = null,
        clientPlatform: String? = null
    ): String

    fun getBangumiOauthUrl(requestId: String): String
    suspend fun bangumiOauthCallback(bangumiCode: String, requestId: String): Boolean
    suspend fun getBangumiToken(requestId: String): BangumiUserToken
}

class AuthServiceImpl : AuthService, KoinComponent {
    private val bangumiLoginHelper: BangumiLoginHelper by inject()
    private val userRepository: UserRepository by inject()
    private val bangumiOauthRepository: BangumiOauthRepository by inject()
    private val clientVersionVerifier: ClientVersionVerifier by inject()
    private val serverConfig: ServerConfig by inject()

    override suspend fun loginBangumi(
        bangumiToken: String,
        clientVersion: String?,
        clientPlatform: String?
    ): String {
        val bangumiUser = bangumiLoginHelper.login(bangumiToken) ?: throw UnauthorizedException()
        if (clientVersion != null) {
            if (!clientVersionVerifier.verify(clientVersion.trim())) {
                throw InvalidClientVersionException()
            }
        }
        val userId = userRepository.getUserIdOrNull(bangumiUser.id) ?: run {
            registerAndGetId(bangumiUser)
        }
        if (!userRepository.setLastLoginTime(userId, System.currentTimeMillis())) {
            throw OperationFailedException()
        }
        if (clientVersion != null) {
            userRepository.setClientVersion(userId, clientVersion.trim())
        }
        if (clientPlatform != null) {
            userRepository.addClientPlatform(userId, clientPlatform)
        }
        return userId
    }

    override fun getBangumiOauthUrl(requestId: String): String {
        val clientId = serverConfig.bangumi.clientId
        val redirectUrl = URLEncoder.encode("https://${serverConfig.domain}/v1/login/bangumi/oauth/callback", "utf-8")
        return "https://bgm.tv/oauth/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUrl&state=$requestId"
    }

    override suspend fun bangumiOauthCallback(bangumiCode: String, requestId: String): Boolean {
        val token = bangumiLoginHelper.getToken(bangumiCode, requestId) ?: return false
        bangumiOauthRepository.add(requestId, token)
        return true
    }

    override suspend fun getBangumiToken(requestId: String): BangumiUserToken {
        val token = bangumiOauthRepository.getToken(requestId)
            ?: throw NotFoundException("The bangumi code corresponding to the request ID has not arrived or is expired")
        return token
    }

    private suspend fun registerAndGetId(user: BangumiUser): String {
        return userRepository.addAndGetId(
            bangumiId = user.id,
            nickname = user.nickname,
            smallAvatar = user.smallAvatar,
            mediumAvatar = user.mediumAvatar,
            largeAvatar = user.largeAvatar,
        ) ?: throw OperationFailedException()
    }
}