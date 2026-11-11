package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.remote.atlas.sha256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** unspecified 属于定义的匹配策略，不代表成绩已核实为某个具体版本。 */
internal fun BenchmarkDefinitionEntity.acceptsVersion(actualVersion: String?): Boolean =
    version == "unspecified" || actualVersion != null &&
        (version == actualVersion || actualVersion in compatibleVersions.orEmpty())

/** 导入和旧缓存修复共用唯一关联规则；只更新派生关联，不改写原始成绩及版本。 */
internal fun BenchmarkScoreEntity.withAtlasBenchmarkDefinition(
    definitions: List<BenchmarkDefinitionEntity>,
): BenchmarkScoreEntity {
    val definition = definitions.filter {
        it.name == name && it.metric == metric && it.acceptsVersion(version)
    }.singleOrNull()
    val benchmarkId = definition?.id ?: "unmapped:" +
        Json.encodeToString(listOf(name, version, metric)).toByteArray().sha256()
    return copy(benchmarkId = benchmarkId, unit = definition?.unit, higherIsBetter = definition?.higherIsBetter)
}
