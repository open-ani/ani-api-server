package me.him188.ani.danmaku.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.danmaku.protocol.AnonymousBangumiUserToken
import me.him188.ani.danmaku.protocol.BangumiUserToken
import me.him188.ani.danmaku.server.ServerConfig
import me.him188.ani.danmaku.server.util.exception.UnauthorizedException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger

interface BangumiLoginHelper {
    suspend fun login(bangumiToken: String): BangumiUser?
    suspend fun getToken(code: String, requestId: String): BangumiUserToken?
    suspend fun getToken(refreshToken: String): AnonymousBangumiUserToken?
}

class TestBangumiLoginHelperImpl : BangumiLoginHelper {
    override suspend fun login(bangumiToken: String): BangumiUser? {
        return when (bangumiToken) {
            "test_token_1" -> BangumiUser(1, "test", "small", "medium", "large")
            "test_token_2" -> BangumiUser(2, "test2", "small2", "medium2", "large2")
            "test_token_3" -> BangumiUser(3, "test3", "small3", "medium3", "large3")
            else -> null
        }
    }

    override suspend fun getToken(code: String, requestId: String): BangumiUserToken? {
        return when (code) {
            "test_code_1" -> BangumiUserToken(1, 3600, "test_token_1", "test_refresh_token_1")
            "test_code_2" -> BangumiUserToken(2, 3600, "test_token_2", "test_refresh_token_2")
            "test_code_3" -> BangumiUserToken(3, 3600, "test_token_3", "test_refresh_token_3")
            else -> null
        }
    }

    override suspend fun getToken(refreshToken: String): AnonymousBangumiUserToken? {
        return when (refreshToken) {
            "test_refresh_token_1" -> AnonymousBangumiUserToken("test_token_1", "test_refresh_token_1", 3600)
            "test_refresh_token_2" -> AnonymousBangumiUserToken("test_token_2", "test_refresh_token_2", 3600)
            else -> null
        }
    }
}

class BangumiLoginHelperImpl : BangumiLoginHelper, KoinComponent {
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    private val bangumiLoginUrl = "https://api.bgm.tv/v0/me"
    private val bangumiOauthTokenUrl = "https://bgm.tv/oauth/access_token"
    private val log: Logger by inject()
    private val serverConfig: ServerConfig by inject()

    override suspend fun login(bangumiToken: String): BangumiUser? {
        try {
            val response = httpClient.get(bangumiLoginUrl) {
                bearerAuth(bangumiToken)
            }
            if (!response.status.isSuccess()) {
                log.info("Failed to login to Bangumi due to: Bangumi responded with ${response.status}: ${response.bodyAsText()}")
                return null
            }
            val user = response.body<User>()
            return BangumiUser(user.id, user.nickname, user.avatar.small, user.avatar.medium, user.avatar.large)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to get Bangumi token due to:", e)
            return null
        }
    }

    override suspend fun getToken(code: String, requestId: String): BangumiUserToken? {
        try {
            val response = httpClient.post(bangumiOauthTokenUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("grant_type", "authorization_code")
                        put("client_id", serverConfig.bangumi.clientId)
                        put("client_secret", serverConfig.bangumi.clientSecret)
                        put("code", code)
                        put("redirect_uri", "https://${serverConfig.domain}/v1/login/bangumi/oauth/callback")
                        put("state", requestId)
                    }
                )
            }
            if (!response.status.isSuccess()) {
                log.error(
                    "Failed to get Bangumi token with code $code due to: " +
                            "Bangumi responded with ${response.status}: ${response.bodyAsText()}"
                )
                return null
            }
            val tokenResponse = response.body<OauthTokenResponse>()
            return BangumiUserToken(
                userId = tokenResponse.userId ?: throw IllegalStateException("Bangumi did not return user ID"),
                expiresIn = tokenResponse.expiresIn,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to get Bangumi token with code $code due to:", e)
            return null
        }
    }

    override suspend fun getToken(refreshToken: String): AnonymousBangumiUserToken {
        try {
            val response = httpClient.post(bangumiOauthTokenUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("grant_type", "refresh_token")
                        put("client_id", serverConfig.bangumi.clientId)
                        put("client_secret", serverConfig.bangumi.clientSecret)
                        put("refresh_token", refreshToken)
                    }
                )
            }
            if (!response.status.isSuccess()) {
                val message =
                    "Failed to get Bangumi token with refresh token $refreshToken due to: " +
                            "Bangumi responded with ${response.status}: ${response.bodyAsText()}"
                log.error(message)
                throw UnauthorizedException(message)
            }
            val tokenResponse = response.body<OauthTokenResponse>()
            return AnonymousBangumiUserToken(
                expiresIn = tokenResponse.expiresIn,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Failed to get Bangumi token with refresh token $refreshToken due to: ${e.message}"
            log.error("Failed to get Bangumi token with refresh token $refreshToken due to:", e)
            throw UnauthorizedException(message)
        }
    }

    @Serializable
    data class User(
        val avatar: Avatar,
        val sign: String,
        val username: String,
        val nickname: String,
        val id: Int,
        @SerialName("user_group") val userGroup: Int,
    )

    @Serializable
    data class Avatar(
        val large: String,
        val medium: String,
        val small: String,
    )

    @Serializable
    data class OauthTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("token_type") val tokenType: String,
        val scope: String?,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("user_id") val userId: Int?,
    )
}

data class BangumiUser(
    val id: Int,
    val nickname: String,
    val smallAvatar: String,
    val mediumAvatar: String,
    val largeAvatar: String,
)

//fun main() {
//    val bangumiLoginHelper = BangumiLoginHelperImpl()
//    val token = "<enter test token here>"
//    runBlocking {
//        val user = bangumiLoginHelper.login(token)
//        println(user)
//    }
//}
