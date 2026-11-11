package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.CatalogSnapshot
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.CatalogReleaseEntity
import io.github.chenlongapps.modelatlas.data.local.model.ModelParametersLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelParameterComponentLocal
import io.github.chenlongapps.modelatlas.data.remote.atlas.AtlasRelease
import io.github.chenlongapps.modelatlas.data.remote.atlas.sha256
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull

/** 模型/成绩/翻译取自同一 Atlas 发布；服务商只提供报价，不补齐规范模型字段。 */
fun CatalogMapper.mapAtlas(
    release: AtlasRelease,
    providers: Map<String, ProviderDto>,
    modelsFetchedAt: Long,
    providersFetchedAt: Long,
): CatalogSnapshot {
    val catalog = map(release.models.mapValues { (id, m) ->
        ModelDto(
            id = id, name = m.name, description = m.description, family = m.family, type = m.type,
            reasoning = m.capabilities?.reasoning?.supported, toolCall = m.capabilities?.tools?.supported,
            temperature = m.capabilities?.temperature, knowledge = m.knowledge,
            releaseDate = m.releaseDate, lastUpdated = m.lastUpdated, modalities = m.modalities,
            openWeights = m.openWeights, limit = m.limit, license = m.license, weights = m.weights, links = m.links,
        )
    }, providers, modelsFetchedAt, providersFetchedAt)
    val definitions = release.benchmarks.map { (id, b) ->
        BenchmarkDefinitionEntity(
            id, b.name, b.version, b.metric, b.description, release.translations.benchmark.description[id],
            b.compatibleVersions, b.category, b.organization, b.url, b.methodology, b.unit, b.higherIsBetter,
            b.minScore, b.maxScore, popularity = b.popularity,
        )
    }
    val scores = release.models.flatMap { (modelId, model) ->
        model.benchmarks.orEmpty().mapIndexed { index, score ->
            // 只允许显式名称、指标及版本匹配；兼容版本必须由定义明确列出。
            val definition = definitions.filter {
                it.name == score.name && it.metric == score.metric && score.version != null &&
                    (it.version == score.version || score.version in it.compatibleVersions.orEmpty())
            }.singleOrNull()
            val unmatchedKey = Json.encodeToString(listOf(score.name, score.version, score.metric)).toByteArray().sha256()
            BenchmarkScoreEntity(
                id = "atlas:$modelId:${index.toString().padStart(6, '0')}",
                modelId = modelId, benchmarkId = definition?.id ?: "unmapped:$unmatchedKey",
                score = if (score.score.isString) null else score.score.doubleOrNull, metric = score.metric, unit = definition?.unit,
                higherIsBetter = definition?.higherIsBetter, subset = score.dataset, harness = score.harness,
                reasoningEffort = score.effort, sourceUrl = score.source, evaluatedAt = score.date,
                name = score.name, version = score.version, variant = score.variant,
                scoreText = score.score.takeIf { it.isString }?.content,
            )
        }
    }
    return catalog.copy(
        models = catalog.models.map { model ->
            val parameters = release.models.getValue(model.id).metadata?.modelatlas?.parameters
            model.copy(
                source = "model-atlas-data", descriptionZhCn = release.translations.model.descriptions[model.id],
                parameters = parameters?.let { p ->
                    ModelParametersLocal(p.totalBillion, p.activeBillion, p.sources, p.notes,
                        p.components?.map { ModelParameterComponentLocal(it.name, it.totalBillion, it.activeBillion, it.sources, it.notes) })
                },
            )
        },
        scores = scores,
        definitions = definitions,
        release = CatalogReleaseEntity(dataVersion = release.manifest.dataVersion,
            gitCommit = release.manifest.gitCommit, publishedAt = release.manifest.publishedAt),
    )
}
