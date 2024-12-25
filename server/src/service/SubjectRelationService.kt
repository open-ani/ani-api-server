package me.him188.ani.danmaku.server.service

import androidx.collection.IntObjectMap
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.server.domain.subject.SubjectRelationIndex
import me.him188.ani.danmaku.server.domain.subject.SubjectRelationUpdater
import me.him188.ani.danmaku.server.domain.subject.SubjectRelationsIndexer
import me.him188.ani.danmaku.server.util.error
import me.him188.ani.danmaku.server.util.info
import me.him188.ani.danmaku.server.util.logger
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class SubjectRelationService {
    private val cachedIndex =
        MutableSharedFlow<IntObjectMap<SubjectRelationIndex>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun getSubjectRelationIndex(subjectId: Int): SubjectRelationIndex? {
        return cachedIndex.first()[subjectId]
    }

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (true) {
                suspend {
                    try {
                        logger.info { "Updating subject relations" }
                        update()
                    } catch (e: Throwable) {
                        logger.error(e) { "Failed to update subject relations, retrying in 1 minute" }
                    }
                }.asFlow()
                    .retry {
                        delay(1.minutes)
                        true
                    }
                    .first()

                val nextUpdateDelay = millisUntilNextUpdate().milliseconds + 1.hours
                logger.info { "Next update in $nextUpdateDelay" }
                delay(nextUpdateDelay)
            }
        }
    }

    private fun millisUntilNextUpdate(): Long {
        val zone = ZoneId.of("Asia/Shanghai") // GMT+8
        val now = ZonedDateTime.now(zone)

        // 寻找当前周的周三 05:00，如果已过，则移动到下周三
        var target = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            .withHour(5)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // 如果当前时间已经超过了本周的周三 05:00，则调整到下周三
        if (!target.isAfter(now)) {
            target = target.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
        }

        val duration = Duration.between(now, target)
        return duration.toMillis()
    }

    suspend fun update() {
        val cachePath = SubjectRelationUpdater(Path.of("cache")).run {
            getNewSubjectRelations()
        }
//        val fileSize = String.format("%.2f", Files.size(cachePath.resolve("subject-relations.jsonlines")).toDouble() / 1024 / 1024)
//        logger.info { "Downloaded new subject relations to: $cachePath, file size: $fileSize MB" }

        SubjectRelationsIndexer.createNewIndex(cachePath).let { index ->
            logger.info { "Index size: ${index.size}" }
            cachedIndex.emit(index)
        }
    }

    private companion object {
        private val logger = logger<SubjectRelationService>()
    }
}

suspend fun main() {
    SubjectRelationService()
    awaitCancellation()
}