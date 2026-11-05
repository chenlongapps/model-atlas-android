package io.github.chenlongapps.modelatlas.testing

import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.benchmark.readBenchmarkBytes
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.mapAtlas
import io.github.chenlongapps.modelatlas.data.mapper.toModelDetail
import io.github.chenlongapps.modelatlas.data.mapper.toModelSummaries
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.remote.atlas.AtlasReleaseLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal data class AtlasModalityFixture(val summary: ModelSummary, val detail: ModelDetail, val rawInputs: List<String>)

/** 真实发布原字节经过生产解析／映射，不用手写五类数据掩盖 MIME 兼容问题。 */
internal fun realAtlasModalityFixture(): AtlasModalityFixture = runBlocking(Dispatchers.IO) {
    val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    val release = AtlasReleaseLoader { path, limit -> assets.open("atlas/$path").use { it.readBenchmarkBytes(limit) } }.load()
    val model = CatalogMapper().mapAtlas(release, emptyMap(), 0, 0).models.first { it.id == "google/gemini-2.0-flash" }
    val summary = listOf(ModelListRow(model.id, model.name, model.description, model.type,
        model.inputModalities, model.outputModalities, null, null)).toModelSummaries().single()
    AtlasModalityFixture(summary, ModelDetailRecord(model, emptyList()).toModelDetail()!!, requireNotNull(model.inputModalities))
}
