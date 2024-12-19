package me.him188.ani.danmaku.server.service

import androidx.collection.IntList
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.server.util.error
import me.him188.ani.danmaku.server.util.info
import me.him188.ani.danmaku.server.util.logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class SubjectRelationService {
    private var cachedIndex: IntObjectMap<SubjectRelationIndex> = MutableIntObjectMap()

    fun getRelatedSubjects(subjectId: Int): IntList? {
        return cachedIndex[subjectId]?.relatedAnimeSubjectIds
    }

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (true) {
                try {
                    logger.info { "Updating subject relations" }
                    update()
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to update subject relations" }
                }
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
        val jsonlinesPath = SubjectRelationUpdater(Path.of("cache")).run {
            getNewSubjectRelations()
        }
        val fileSize = String.format("%.2f", Files.size(jsonlinesPath).toDouble() / 1024 / 1024)
        logger.info { "Downloaded new subject relations to: $jsonlinesPath, file size: $fileSize MB" }

        SubjectRelationsParser.createNewIndex(jsonlinesPath).let { index ->
            logger.info { "Index size: ${index.size}" }
            cachedIndex = index
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