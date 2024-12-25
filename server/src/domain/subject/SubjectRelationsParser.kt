package me.him188.ani.danmaku.server.domain.subject

import androidx.collection.IntIntMap
import androidx.collection.IntList
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntIntMapOf
import androidx.collection.mutableIntListOf
import androidx.collection.mutableIntSetOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.server.util.toList
import me.him188.ani.danmaku.server.util.trim
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeMap
import kotlin.io.path.useLines

class SubjectRelationsParser {
    fun parseSubjectRelationsTable(input: Path): SubjectRelationsTable {
        // {"subject_id":1,"relation_type":1,"related_subject_id":296317,"order":0}
        @Serializable
        @Suppress("PropertyName")
        data class SubjectRelation(
            val subject_id: Int,
            val relation_type: Int,
            val related_subject_id: Int,
            val order: Int
        )

        val map = TreeMap<Int, MutableList<Relation>>()
        val json = Json {
            ignoreUnknownKeys = true
        }

        val mutableListLambda: (Int) -> MutableList<Relation> = { ArrayList() }
        input.useLines { lines ->
            lines.forEach { line ->
                val relation = json.decodeFromString(SubjectRelation.serializer(), line)
                map.computeIfAbsent(relation.subject_id, mutableListLambda).add(
                    Relation(
                        relatedSubjectId = relation.related_subject_id,
                        order = relation.order,
                        relationType = relation.relation_type,
                    ),
                )
            }
        }

        return SubjectRelationsTable(map)
    }

    fun parseSubjectTable(input: Path): SubjectTable {
        // {"id":1,"type":2}
        val map = TreeMap<Int, MutableList<Subject>>()
        val json = Json {
            ignoreUnknownKeys = true
        }

        val mutableListLambda: (Int) -> MutableList<Subject> = { ArrayList() }
        input.useLines { lines ->
            lines.forEach { line ->
                val subject = json.decodeFromString(Subject.serializer(), line)
                map.computeIfAbsent(subject.id, mutableListLambda).add(subject)
            }
        }

        return SubjectTable(map)
    }

    fun parseEpisodeTable(input: Path): EpisodeTable {
        val sizeMap = mutableIntIntMapOf()
//        val map = TreeMap<Int, MutableList<Episode>>()
//        val subjectToEps = mutableMapOf<Int, MutableList<Episode>>()
        val json = Json {
            ignoreUnknownKeys = true
        }

        val mutableListLambda: (Int) -> MutableList<Episode> = { ArrayList() }
        input.useLines { lines ->
            lines.forEach { line ->
                val episode = json.decodeFromString(Episode.serializer(), line)
//                map.computeIfAbsent(episode.id, mutableListLambda).add(episode)
//                subjectToEps.computeIfAbsent(episode.subjectId, mutableListLambda).add(episode)
                sizeMap[episode.subjectId] = sizeMap.getOrDefault(episode.subjectId, 0) + 1
            }
        }

        return EpisodeTable(sizeMap)
    }
}

class SubjectRelationsIndexer(
    private val subjectRelationsTable: SubjectRelationsTable,
    private val episodeTable: EpisodeTable
) {
    fun createIndex(
    ): IntObjectMap<SubjectRelationIndex> {
        val index = MutableIntObjectMap<SubjectRelationIndex>()

        // 帮助方法：给定 subjectId，返回它的所有 relation_type = 2 (前传) 的 subject
        fun getPrequelsOfSubject(subjectId: Int): List<Relation> {
            return subjectRelationsTable.contents[subjectId]
                ?.filter { it.relationType == 2 }
                .orEmpty()
        }

        // 帮助方法：给定 subjectId，返回它的所有 relation_type = 3 (续集) 的 subject
        fun getSequelsOfSubject(subjectId: Int): List<Relation> {
            return subjectRelationsTable.contents[subjectId]
                ?.filter { it.relationType == 3 }
                .orEmpty()
        }

        // 判断一个 subject 是否拥有至少 8 集
        fun hasAtLeast8Episodes(subjectId: Int): Boolean = episodeTable.subjectToEpSize.getOrElse(subjectId) { 0 } >= 8

        /**
         * 找到从某一个 subject 出发，能追溯到的最早一季（即一直往前查找 relation = 前传 2）
         * 如果有多条前传分支，需要你根据自己业务决定如何选取：
         * 这里示例只简单地取 "第一个" 前传（或者说 ID 最小的）。
         */
        fun findEarliestSeasonSubject(subjectId: Int): Int {
            // 防止循环导致死递归
            val visited = mutableIntSetOf()
            var current = subjectId

            while (true) {
                visited.add(current)
                val possiblePrequels = getPrequelsOfSubject(current)
                if (possiblePrequels.isEmpty()) {
                    break
                }

                // 简单地取 "ID 最小" 的那个作为“唯一”前传，也可以改成 "Relation.order" 最小的
                val earliest = possiblePrequels
                    .map { it.relatedSubjectId }
                    .filterNot { it in visited }    // 避免循环
                    .minOrNull() ?: break

                current = earliest
            }
            return current
        }

        /**
         * 从最早一季开始，顺序向后找 "续集 (relation_type = 3)"，
         * 并按照 Relation.order 的升序依次加载到主线 series。
         * 同时过滤掉不满足 "集数 >= 8" 的 subject.
         *
         * 如果有多条分支（多个续集），这里示例只依 "Relation.order" 为主排序，顺序取下去；
         * 实际业务可根据需求选择：取第一个 or 全部并行。
         */
        fun buildSeriesChain(startSubjectId: Int): IntList {
            val chain = mutableIntListOf()
            val visited = mutableIntSetOf()
            val queue = ArrayDeque<Int>()
            queue.addLast(startSubjectId)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (current in visited) continue
                visited.add(current)
                // 将此节点加入 series
                chain.add(current)

                // 找到当前 subject 的所有 "续集"
                val sequels = getSequelsOfSubject(current)
                    // 过滤掉不满足 8 集的
                    .filter { hasAtLeast8Episodes(it.relatedSubjectId) }
                    // 按 Relation.order 升序
                    .sortedBy { it.order }

                // 根据你需求，决定如何处理多条分支：
                // 这里示例做法：依次放入 queue，从小到大依次 BFS。
                // 如果你只想取第一个，就 sequels.firstOrNull() 放入 queue 即可。
                sequels.forEach { sequel ->
                    if (sequel.relatedSubjectId !in visited) {
                        queue.addLast(sequel.relatedSubjectId)
                    }
                }
            }
            return chain
        }

        // 这里演示：对 table 中「所有有关系信息」的 subjectId，构建索引
        for ((subjectId, _) in subjectRelationsTable.contents) {
            // 1) 找最早一季
            val earliest = findEarliestSeasonSubject(subjectId)
            // 2) 从 earliest 一直向后找续集，得到完整主线（过滤集数 < 8 的）
            val mainAnimeChain = buildSeriesChain(earliest)

            // sequelAnimeSubjectIds 保持和原先类似的逻辑即可，
            // 或者你也可以做成类似 chain 继续搜索，这里暂时保留你的原先用法:
            val sequelAnimeSubjectIds = collectRelated(
                subjectId,
                mutableSetOf(),
                arrayOf(3, 6, 11), // 续集, 番外篇, 衍生
            ).trim()

            // 将 mainAnimeChain 也 trim 一下，得到最终 seriesMainAnimeSubjectIds
            index[subjectId] = SubjectRelationIndex(
                seriesMainAnimeSubjectIds = mainAnimeChain.trim(),
                sequelAnimeSubjectIds = sequelAnimeSubjectIds,
            )
        }

        // 最后，移除没有 sequel 的 subject (可按业务需求调整)
        index.removeIf { _, value -> value.sequelAnimeSubjectIds.isEmpty() }

        // trim 以节省内存
        index.trim()
        return index
    }

    // A recursive function that, given a starting subject, finds all directly and indirectly related subjects.
    // Each subject search is isolated with its own visited set.
    fun collectRelated(
        subjectId: Int,
        visited: MutableSet<Int>,
        allowedRelations: Array<Int>
    ): IntList {
        // Mark current subject as visited to avoid cycles
        visited.add(subjectId)

        // Get directly related subjects for this subject, filtering by allowed relations
        val directlyRelated = subjectRelationsTable.contents[subjectId].orEmpty()
            .filter { it.relationType in allowedRelations }
            .map { it.relatedSubjectId }

        // Use a set to accumulate all related subjects (direct + indirect)
        val allRelated = mutableIntListOf()

        for (relatedId in directlyRelated) {
            if (relatedId !in visited) {
                // Add this directly related subject
                allRelated.add(relatedId)
                // Recursively find all subjects related to this relatedId
                allRelated.addAll(collectRelated(relatedId, visited, allowedRelations))
            }
        }

        return allRelated
    }

    companion object {
        fun createNewIndex(cachePath: Path): IntObjectMap<SubjectRelationIndex> {
            val parser = SubjectRelationsParser()
            val subjectRelationsTable =
                parser.parseSubjectRelationsTable(cachePath.resolve("subject-relations.jsonlines"))
//            val subjectTable = parser.parseSubjectTable(cachePath.resolve("subject.jsonlines"))
            val episodeTable = parser.parseEpisodeTable(cachePath.resolve("episode.jsonlines"))


            SubjectRelationsIndexer(subjectRelationsTable, episodeTable).run {
                val index = createIndex()
//            println("Index size: ${index.size}")
//            Table size: 356626
//            Index size: 16773

//            println(
//                index.entries.sumOf {
//                    1 + it.value.relatedAnimeSubjectIds.size
//                },
//            ) // 122217
                return index
            }
        }
    }
}


data class Relation(
    val relatedSubjectId: Int,
    val order: Int,
    val relationType: Int,
)

@Serializable
data class Subject(
    val id: Int,
    val type: Int,
    val name_cn: String,
)

class SubjectTable(
    val contents: Map<Int, List<Subject>>
) {
    fun getSubjectNameById(id: Int): String {
        return contents[id]?.firstOrNull()?.name_cn.toString()
    }
}

@Serializable
data class Episode(
    val id: Int,
    @SerialName("subject_id")
    val subjectId: Int,
    val sort: Float, // "1", "22.5"
    val airdate: String, // yyyy-MM-dd
    val type: Int, // 0: 主集, 1: 特别篇
)

class EpisodeTable(
//    val contents: Map<Int, List<Episode>>,
//    val subjectToEp: Map<Int, List<Episode>>,
    val subjectToEpSize: IntIntMap,
)

class SubjectRelationsTable(
    val contents: Map<Int, List<Relation>>
)

@ConsistentCopyVisibility
data class SubjectRelationIndex internal constructor(
    // IntList is memory efficient than List<Int>
    /**
     * The main anime subjects of this series, sorted by order of seasons.
     * For example, the first element is the first season, the second element is the second season, and so on.
     */
    val seriesMainAnimeSubjectIds: IntList,
    val sequelAnimeSubjectIds: IntList,
)

suspend fun main() {
//    val jsonlinesPath = SubjectRelationUpdater(Path.of("cache")).run {
//        getNewSubjectRelations()
//    }
//    val fileSize = String.format("%.2f", Files.size(jsonlinesPath).toDouble() / 1024 / 1024)
//    println("Downloaded new subject relations to: $jsonlinesPath, file size: $fileSize MB")

    SubjectRelationsParser().run {
        val subjectRelations = parseSubjectRelationsTable(Paths.get("cache/subject-relations.jsonlines"))
        val subjectTable = parseSubjectTable(Paths.get("cache/subject.jsonlines"))
        val episodeTable = parseEpisodeTable(Paths.get("cache/episode.jsonlines"))

        println("Table size: ${subjectRelations.contents.size}")
        SubjectRelationsIndexer(subjectRelations, episodeTable).run {
            val index = createIndex()
            println("Index size: ${index.size}")
            println(episodeTable.subjectToEpSize[302523])
            println(
                "series for 302523: ${
                    index[302523]?.seriesMainAnimeSubjectIds?.toList()
                        ?.map { it.toString() + subjectTable.getSubjectNameById(it) }
                }",
            )
        }
        awaitCancellation()
    }
}
