package me.him188.ani.danmaku.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.server.data.*
import me.him188.ani.danmaku.server.data.mongodb.*
import me.him188.ani.danmaku.server.service.*
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import kotlin.time.Duration.Companion.minutes

fun getServerKoinModule(
    config: ServerConfig,
    topCoroutineScope: CoroutineScope,
    logger: Logger = NOPLogger.NOP_LOGGER,
) = module {
    single(named("topCoroutineScope")) { topCoroutineScope }
    single<Logger> { logger }
    single<ServerConfig> { config }

    single<DanmakuService> { DanmakuServiceImpl() }
    single<AuthService> { AuthServiceImpl() }
    single<UserService> { UserServiceImpl() }
    single<JwtTokenManager> { JwtTokenManagerImpl() }
    single<ClientVersionVerifier> {
        AniClientVersionVerifierImpl(
            versionWhitelistRegex = listOf("[3-9].[0-9]{1,2}.[0-9]{1,2}-dev"),
        )
    }

    if (config.testing) {
        single<DanmakuRepository> { InMemoryDanmakuRepositoryImpl() }
        single<UserRepository> { InMemoryUserRepositoryImpl() }
        single<BangumiOauthRepository> { InMemoryBangumiOauthRepositoryImpl() }
        single<BangumiLoginHelper> { TestBangumiLoginHelperImpl() }

        single<ClientReleaseInfoManager> { TestClientReleaseInfoManager() }
    } else {
        single<MongoCollectionProvider> {
            MongoCollectionProviderImpl().also {
                topCoroutineScope.launch {
                    it.buildIndex()
                }
            }
        }
        single<DanmakuRepository> { MongoDanmakuRepositoryImpl() }
        single<UserRepository> { MongoUserRepositoryImpl() }
        single<BangumiOauthRepository> { MongoBangumiOauthRepositoryImpl() }
        single<BangumiLoginHelper> { BangumiLoginHelperImpl() }

        single<ClientReleaseInfoManager> {
            ClientReleaseInfoManagerImpl(
                bufferExpirationTime = 10.minutes.inWholeMilliseconds,
            )
        }
    }
}