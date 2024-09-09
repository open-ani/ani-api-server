package me.him188.ani.danmaku.server.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration


/**
 * 线程安全的快速内存缓存.
 *
 * 不会自动清除, 只能放固定数量的 key, 例如 enum key
 *
 * @param K 会作为 map key, 必须有 stable [Any.hashCode]
 * @see getOrPut
 */
class ConcurrentMemoryCacheMap<K, V>(
    /**
     * 每条缓存的有效期
     */
    private val cacheExpiry: Duration,
) {
    private data class Cache<V>(
        val expireAt: Instant,
        val value: V,
    )

    private val cache: MutableMap<K, Cache<V>> = ConcurrentHashMap()

    val values get() = cache.values.map { it.value }

    /**
     * 获取缓存的值. 当缓存已经过期时返回 `null`.
     */
    operator fun get(key: K): V? {
        cache[key]?.let {
            if (it.expireAt > Clock.System.now()) {
                return it.value
            }
        }
        return null
    }

    operator fun set(key: K, value: V) {
        cache[key] = Cache(Clock.System.now() + cacheExpiry, value)
    }
}

/**
 * 获取缓存的值, 如果缓存不存在则调用 [defaultValue] 并将其放入缓存.
 */
inline fun <K, V> ConcurrentMemoryCacheMap<K, V>.getOrPut(key: K, defaultValue: () -> V): V {
    return get(key) ?: defaultValue().also { set(key, it) }
}

/**
 * 缓存单个值
 */
class ConcurrentMemoryCache<V>(
    cacheExpiry: Duration,
) {
    private val delegate = ConcurrentMemoryCacheMap<Unit, V>(cacheExpiry)

    fun get(): V? = delegate[Unit]
    fun set(value: V) = delegate.set(Unit, value)
}

inline fun <V> ConcurrentMemoryCache<V>.getOrPut(defaultValue: () -> V): V {
    return get() ?: defaultValue().also { set(it) }
}
