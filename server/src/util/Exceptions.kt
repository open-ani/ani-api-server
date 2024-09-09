package me.him188.ani.danmaku.server.util


val Throwable.causes: Sequence<Throwable>
    get() = sequence {
        var rootCause: Throwable? = this@causes
        while (rootCause?.cause != null) {
            yield(rootCause.cause!!)
            rootCause = rootCause.cause
        }
    }

inline fun <reified T> Throwable.findCauseByType(): T? {
    return findCause { it is T } as T?
}


fun Throwable.findCause(predicate: (Throwable) -> Boolean): Throwable? {
    return causes.firstOrNull(predicate)
}
