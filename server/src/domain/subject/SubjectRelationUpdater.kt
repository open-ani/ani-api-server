package me.him188.ani.danmaku.server.domain.subject

import io.ktor.client.HttpClient
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile

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
        install(ContentNegotiation.Plugin) {
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

        cacheDir
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


@Serializable
@Suppress("PropertyName") // 不能挪到 property 上, K2 IDE bug
private data class Latest(
    val browser_download_url: String,
    val size: Long
)
