package me.him188.ani.danmaku.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.server.data.BangumiOauthRepository
import me.him188.ani.danmaku.server.data.DanmakuRepository
import me.him188.ani.danmaku.server.data.InMemoryBangumiOauthRepositoryImpl
import me.him188.ani.danmaku.server.data.InMemoryDanmakuRepositoryImpl
import me.him188.ani.danmaku.server.data.UserRepository
import me.him188.ani.danmaku.server.data.mongodb.MongoBangumiOauthRepositoryImpl
import me.him188.ani.danmaku.server.data.mongodb.MongoCollectionProvider
import me.him188.ani.danmaku.server.data.mongodb.MongoCollectionProviderImpl
import me.him188.ani.danmaku.server.data.mongodb.MongoDanmakuRepositoryImpl
import me.him188.ani.danmaku.server.service.AniClientVersionVerifierImpl
import me.him188.ani.danmaku.server.service.AnimeScheduleService
import me.him188.ani.danmaku.server.service.AuthService
import me.him188.ani.danmaku.server.service.AuthServiceImpl
import me.him188.ani.danmaku.server.service.BangumiLoginHelper
import me.him188.ani.danmaku.server.service.BangumiLoginHelperImpl
import me.him188.ani.danmaku.server.service.BangumiTrendsService
import me.him188.ani.danmaku.server.service.ClientReleaseInfoManager
import me.him188.ani.danmaku.server.service.ClientReleaseInfoManagerImpl
import me.him188.ani.danmaku.server.service.ClientVersionVerifier
import me.him188.ani.danmaku.server.service.DanmakuService
import me.him188.ani.danmaku.server.service.DanmakuServiceImpl
import me.him188.ani.danmaku.server.service.JwtTokenManager
import me.him188.ani.danmaku.server.service.JwtTokenManagerImpl
import me.him188.ani.danmaku.server.service.SubscriptionProxyService
import me.him188.ani.danmaku.server.service.TestBangumiLoginHelperImpl
import me.him188.ani.danmaku.server.service.TestClientReleaseInfoManager
import me.him188.ani.danmaku.server.service.UserService
import me.him188.ani.danmaku.server.util.DistributionSuffixParser
import me.him188.ani.danmaku.server.util.DistributionSuffixParserImpl
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
    single<UserService> { UserService() }
    single<JwtTokenManager> { JwtTokenManagerImpl() }
    single<ClientVersionVerifier> {
        AniClientVersionVerifierImpl(
            versionWhitelistRegex = listOf("[3-9].[0-9]{1,2}.[0-9]{1,2}-dev"),
        )
    }
    single<DistributionSuffixParser> { DistributionSuffixParserImpl() }
    single<SubscriptionProxyService> { SubscriptionProxyService() }

    if (config.testing) {
        single<DanmakuRepository> { InMemoryDanmakuRepositoryImpl() }
        single<UserRepository> { UserRepository() }
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
        single<UserRepository> { UserRepository() }
        single<BangumiOauthRepository> { MongoBangumiOauthRepositoryImpl() }
        single<BangumiLoginHelper> { BangumiLoginHelperImpl() }

        single<ClientReleaseInfoManager> {
            ClientReleaseInfoManagerImpl(
                bufferExpirationTime = 10.minutes.inWholeMilliseconds,
            )
        }
        single<BangumiTrendsService> { BangumiTrendsService() }
        single<AnimeScheduleService> { AnimeScheduleService() }
    }
}