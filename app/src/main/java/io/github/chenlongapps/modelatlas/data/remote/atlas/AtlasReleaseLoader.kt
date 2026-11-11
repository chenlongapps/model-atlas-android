package io.github.chenlongapps.modelatlas.data.remote.atlas

import java.security.MessageDigest
import java.nio.charset.CharacterCodingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull

internal const val ATLAS_BASE_URL = "https://chenlongapps.github.io/model-atlas-data/"
internal const val ATLAS_MANIFEST_LIMIT = 64 * 1024
internal const val ATLAS_FILE_LIMIT = 16 * 1024 * 1024
internal val ATLAS_FILES = listOf("models.json", "benchmarks.json", "i18n/zh-CN.json", "LICENSE.models-dev")

/** 同一 manifest 下全部校验成功才交付；发布中途换包会校验失败，不提交半份数据。 */
internal class AtlasReleaseLoader(private val read: suspend (path: String, limit: Int) -> ByteArray) {
    suspend fun load(): AtlasRelease {
        try {
            return loadValidated()
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Invalid ModelAtlas Data release", error)
        } catch (error: CharacterCodingException) {
            throw SerializationException("Invalid ModelAtlas Data UTF-8", error)
        }
    }

    private suspend fun loadValidated(): AtlasRelease {
        val header = read("manifest.json", ATLAS_MANIFEST_LIMIT)
        require(header.size <= ATLAS_MANIFEST_LIMIT)
        val manifest = decodeAtlasJson<AtlasManifest>(header)
        require(manifest.schemaVersion == 1)
        require(manifest.gitCommit.matches(Regex("[0-9a-f]{40}")))
        require(manifest.publishedAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
        require(manifest.files.keys.containsAll(ATLAS_FILES))
        require(manifest.files.size <= 100)
        manifest.files.forEach { (path, file) ->
            require(path in ATLAS_FILES || path.matches(Regex("i18n/[A-Za-z0-9-]+\\.json")))
            require(file.size in 0..ATLAS_FILE_LIMIT.toLong())
            require(file.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        val canonicalFiles = JsonObject(manifest.files.toSortedMap().mapValues { (_, file) ->
            buildJsonObject { put("sha256", JsonPrimitive(file.sha256)); put("size", file.size) }
        }).toString()
        require(manifest.dataVersion == "sha256:${canonicalFiles.toByteArray().sha256()}")
        val bytes = ATLAS_FILES.associateWith { path ->
            val expected = manifest.files.getValue(path)
            read(path, expected.size.toInt()).also {
                require(it.size.toLong() == expected.size && it.sha256() == expected.sha256) { "Atlas checksum mismatch: $path" }
            }
        }
        // 许可必须是当前客户端随包展示的上游许可；发生变化时先审核，不静默分发。
        require(bytes.getValue("LICENSE.models-dev").sha256() == "dc2dc41c9fea2fd3c41c21c6f484844cab367800b88ff24781f980ad3a9d160a")
        val models = decodeAtlasJson<Map<String, AtlasModelDto>>(bytes.getValue("models.json"))
        val benchmarks = decodeAtlasJson<Map<String, AtlasBenchmarkDto>>(bytes.getValue("benchmarks.json"))
        val translations = decodeAtlasJson<AtlasTranslationsDto>(bytes.getValue("i18n/zh-CN.json"))
        require(models.size <= 100_000 && benchmarks.size <= 10_000)
        require(models.values.sumOf { it.benchmarks?.size ?: 0 } <= 100_000)
        models.forEach { (id, model) ->
            require(id.matches(Regex("[^/\\s]+/[^/\\s]+")) && model.schema == 2 && model.name.isNotBlank())
            model.benchmarks?.forEach {
                require(it.name.isNotBlank())
                require(if (it.score.isString) it.score.content.isNotBlank() else it.score.doubleOrNull?.isFinite() == true)
            }
        }
        benchmarks.forEach { (id, definition) ->
            require(id.isNotBlank() && definition.name.isNotBlank() && definition.version.isNotBlank() && definition.metric.isNotBlank())
            require(definition.minScore == null || definition.minScore.isFinite())
            require(definition.maxScore == null || definition.maxScore.isFinite())
            require(definition.popularity == null || definition.popularity.isFinite())
        }
        require(translations.model.descriptions.keys.all { it in models })
        require(translations.benchmark.description.keys.all { it in benchmarks })
        return AtlasRelease(manifest, models, benchmarks, translations)
    }
}

internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
