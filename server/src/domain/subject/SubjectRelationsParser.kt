package me.him188.ani.danmaku.server.domain.subject

import androidx.collection.IntList
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntListOf
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.server.util.trim
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeMap
import kotlin.io.path.useLines

class SubjectRelationsParser {
    fun parseSubjectRelationsJsonlines(input: Path): SubjectRelationsTable {
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

    fun createIndex(table: SubjectRelationsTable): IntObjectMap<SubjectRelationIndex> {
        // We'll build a map from subject to its full set of related subjects
        val index = MutableIntObjectMap<SubjectRelationIndex>()

        // A recursive function that, given a starting subject, finds all directly and indirectly related subjects.
        // Each subject search is isolated with its own visited set.
        fun collectRelated(
            subjectId: Int, visited: MutableSet<Int>,
            allowedRelations: Array<Int>
        ): IntList {
            // Mark current subject as visited to avoid cycles
            visited.add(subjectId)

            // Get directly related subjects for this subject, filtering by allowed relations
            val directlyRelated = table.contents[subjectId].orEmpty()
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

        // Build the index for each subject
        for ((subjectId, _) in table.contents) {
            // For each subject, start a fresh visited set
            index[subjectId] = SubjectRelationIndex(
                seriesMainAnimeSubjectIds = collectRelated(
                    subjectId,
                    mutableSetOf<Int>(),
                    arrayOf(
                        2,  // 前传 (Prequel)
                        3,  // 续集 (Sequel)
                    ),
                ).trim(),
                sequelAnimeSubjectIds = collectRelated(
                    subjectId,
                    mutableSetOf<Int>(),
                    arrayOf(
                        3,  // 续集 (Sequel)
                        6,  // 番外篇 (Side Story)
                        11,  // 衍生 (Derived Work)
                    ),
                ).trim(),
            )
        }

        // Remove subjects with no related anime
        index.removeIf { key, value ->
            value.sequelAnimeSubjectIds.isEmpty()
        }

        index.trim() // Trim the index to save memory
        return index
    }

    companion object {
        fun createNewIndex(jsonlinesPath: Path): IntObjectMap<SubjectRelationIndex> {
            val parser = SubjectRelationsParser()
            val table = parser.parseSubjectRelationsJsonlines(jsonlinesPath)
//            println("Table size: ${table.contents.size}")
            val index = parser.createIndex(table)
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

data class Relation(
    val relatedSubjectId: Int,
    val order: Int,
    val relationType: Int,
)
//
//data class Subject(
//    val id: Int,
//    val type: Int,
//)
//
//class SubjectTable(
//
//)

class SubjectRelationsTable(
    val contents: Map<Int, List<Relation>>
)

@ConsistentCopyVisibility
data class SubjectRelationIndex internal constructor(
    // IntList is memory efficient than List<Int>
    val seriesMainAnimeSubjectIds: IntList,// TODO: 2024/12/19 这个排序不正确, 目前是乱的
    val sequelAnimeSubjectIds: IntList,
)

@Serializable
private data class SubjectInfo(
    val id: Int,
    val name: String,
    val name_cn: String,
)

suspend fun main() {
//    val jsonlinesPath = SubjectRelationUpdater(Path.of("cache")).run {
//        getNewSubjectRelations()
//    }
//    val fileSize = String.format("%.2f", Files.size(jsonlinesPath).toDouble() / 1024 / 1024)
//    println("Downloaded new subject relations to: $jsonlinesPath, file size: $fileSize MB")

    suspend fun HttpClient.getSubjectNameById(id: Int): String {
        val response = get("https://api.bgm.tv/v0/subjects/$id") {
        }.body<SubjectInfo>()
        return response.name_cn
    }

    SubjectRelationsParser().run {
        val table = parseSubjectRelationsJsonlines(Paths.get("cache/subject-relations.jsonlines"))
        println("Table size: ${table.contents.size}")
        val index = createIndex(table)
        println("Index size: ${index.size}")
        println("index for 302523: ${index[358801]}")
    }
}
