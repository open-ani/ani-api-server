package me.him188.ani.danmaku.server.service

import androidx.collection.IntList
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntListOf
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.server.util.trim
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.TreeMap
import java.util.zip.ZipFile
import kotlin.io.path.useLines

@Serializable
@Suppress("PropertyName") // 不能挪到 property 上, K2 IDE bug
private data class Latest(
    val browser_download_url: String,
    val size: Long
)

class SubjectRelationUpdater(
    private val cacheDir: Path,
) : AutoCloseable {
    val json = Json {
        ignoreUnknownKeys = true
    }
    private val ktorClient = HttpClient {
        expectSuccess = true
//        install(HttpTimeout) {
//            requestTimeoutMillis = 10_000 // Set appropriate timeouts
//            connectTimeoutMillis = 5_000
//        }
        install(ContentNegotiation) {
            json(
                json,
            )
        }
    }

    suspend fun getNewSubjectRelations(): Path = withContext(Dispatchers.IO) {
        val latestUrl = "https://raw.githubusercontent.com/bangumi/Archive/refs/heads/master/aux/latest.json"

        // 1. 获取 latest.json 信息
        val latest = ktorClient.get(latestUrl).bodyAsText().let {
            json.decodeFromString(Latest.serializer(), it)
        }

        // 确保缓存目录存在
        if (Files.notExists(cacheDir)) {
            Files.createDirectories(cacheDir)
        }

        // 下载文件位置
        val zipPath = cacheDir.resolve("relations.zip")

        // 2. 下载 zip 文件（流式下载以防内存溢出）
        ktorClient.prepareRequest(latest.browser_download_url).execute { httpResponse ->
            val channel = httpResponse.bodyAsChannel()
            withContext(Dispatchers.IO) {
                Files.newOutputStream(zipPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                    .use { output ->
                        val buffer = ByteArray(8192)
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
            }
        }

        // 3. 校验文件大小
        val actualSize = Files.size(zipPath)
        if (actualSize != latest.size) {
            throw IllegalStateException("File size mismatch: expected=${latest.size}, actual=$actualSize")
        }

        // 4. 解压文件
        // 先清理 cacheDir 下的旧文件（如果有）
        Files.list(cacheDir).use { stream ->
            stream.filter { it.fileName.toString() != "relations.zip" }.forEach { path ->
                if (Files.isDirectory(path)) {
                    path.toFile().deleteRecursively()
                } else {
                    Files.deleteIfExists(path)
                }
            }
        }

        // 解压
        unzip(zipPath, cacheDir)

        // 5. 返回解压后的 subject-relations.jsonlines 文件路径
        val jsonLinesPath = cacheDir.resolve("subject-relations.jsonlines")
        if (!Files.exists(jsonLinesPath)) {
            throw IllegalStateException("subject-relations.jsonlines not found after unzip.")
        }

        jsonLinesPath
    }

    private fun unzip(zipFile: Path, targetDir: Path) {
        ZipFile(zipFile.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = targetDir.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(outFile)
                } else {
                    if (outFile.parent != null && Files.notExists(outFile.parent)) {
                        Files.createDirectories(outFile.parent)
                    }
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                            .use { output ->
                                input.copyTo(output)
                            }
                    }
                }
            }
        }
    }

    override fun close() {
        ktorClient.close()
    }
}

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
