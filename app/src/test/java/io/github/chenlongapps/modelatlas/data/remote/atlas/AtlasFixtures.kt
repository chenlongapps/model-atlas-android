package io.github.chenlongapps.modelatlas.data.remote.atlas

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun atlasFiles(
    models: String = """{"lab/model":{"schema":2,"name":"Model","type":"llm","description":"Original","capabilities":{"reasoning":{"supported":false},"tools":{"supported":true},"temperature":false},"limit":{"context":5000000000},"benchmarks":[{"name":"Bench","score":42.125,"version":"1.0","metric":"accuracy","effort":"Adaptive/Max","variant":"no tools","dataset":"test","harness":"runner","source":"https://example.org","date":"2026-11"}]}}""",
    definitions: String = """{"bench-v1":{"name":"Bench","version":"1.1","compatible_versions":["1.0"],"metric":"accuracy","description":"Benchmark original","unit":"percent","higher_is_better":true}}""",
    translations: String = """{"model":{"descriptions":{"lab/model":"中文模型说明"}},"benchmark":{"description":{"bench-v1":"中文评测说明"}}}""",
): Map<String, ByteArray> {
    val payload = mapOf(
        "models.json" to models.toByteArray(),
        "benchmarks.json" to definitions.toByteArray(),
        "i18n/zh-CN.json" to translations.toByteArray(),
        "LICENSE.models-dev" to File("src/main/assets/atlas/LICENSE.models-dev").readBytes(),
    )
    val files = payload.mapValues { (_, bytes) -> AtlasFile(bytes.sha256(), bytes.size.toLong()) }
    val canonical = JsonObject(files.toSortedMap().mapValues { (_, file) ->
        buildJsonObject { put("sha256", file.sha256); put("size", file.size) }
    }).toString()
    val manifest = AtlasManifest(1, "sha256:${canonical.toByteArray().sha256()}", "a".repeat(40), "2026-11-06T00:00:00Z", files)
    return payload + ("manifest.json" to AtlasJson.encodeToString(manifest).toByteArray())
}

internal suspend fun atlasRelease(files: Map<String, ByteArray> = atlasFiles()): AtlasRelease =
    AtlasReleaseLoader { path, _ -> files.getValue(path) }.load()

/** 真实随包测试共用原字节及生产限长读取，不复制另一份发布样本。 */
internal suspend fun bundledAtlasRelease(): AtlasRelease = AtlasReleaseLoader { path, limit ->
    File("src/main/assets/atlas/$path").inputStream().use { it.readAtlasBytes(limit) }
}.load()
