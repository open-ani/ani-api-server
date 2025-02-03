package me.him188.ani.danmaku.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BangumiLoginRequest(
    val bangumiToken: String,
    val clientVersion: String? = null,

    /**
     * @since 3.0.0-beta27
     */
    val clientOS: String? = null,
    /**
     * @since 3.0.0-beta27
     */
    val clientArch: String? = null,
) {
    companion object {
        val AllowedOSes = listOf(
            "windows", "macos", "android", "ios",
            "linux", "debian", "ubuntu", "redhat",
        )
        val AllowedArchs = listOf(
            "aarch64", "x86", "x86_64", "arm64-v8a", "armeabi-v7a",
        )
    }
}

@Serializable
data class BangumiLoginResponse(
    val token: String,
    val user: AniUser
)

@Serializable
data class BangumiUserToken(
    val userId: Int,
    val expiresIn: Long,  // in seconds
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class RefreshBangumiTokenRequest(
    val refreshToken: String,
)

@Serializable
data class AnonymousBangumiUserToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,  // in seconds
)