package io.github.chenlongapps.modelatlas.testing

import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.mapAtlas
import io.github.chenlongapps.modelatlas.data.mapper.toModelDetail
import io.github.chenlongapps.modelatlas.data.mapper.toModelSummaries
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.remote.atlas.HttpAtlasDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/** 真实发布原字节经过生产随包加载／映射，禁止测试准备阶段联网。 */
private suspend fun bundledSnapshot() = CatalogMapper().mapAtlas(
    HttpAtlasDataSource(
        InstrumentationRegistry.getInstrumentation().targetContext,
        OkHttpClient.Builder().addInterceptor { error("Bundled UI fixtures must remain offline") }.build(),
        Dispatchers.IO,
    ).bundled(), emptyMap(), 0, 0,
)

internal data class AtlasModalityFixture(val summary: ModelSummary, val detail: ModelDetail, val rawInputs: List<String>)

/** 不用手写五类数据掩盖 MIME 投影问题。 */
internal fun realAtlasModalityFixture(): AtlasModalityFixture = runBlocking(Dispatchers.IO) {
    val model = bundledSnapshot().models.first { it.id == "google/gemini-2.0-flash" }
    val summary = listOf(ModelListRow(model.id, model.name, model.description, model.type,
        model.inputModalities, model.outputModalities, null, null)).toModelSummaries().single()
    AtlasModalityFixture(summary, ModelDetailRecord(model, emptyList()).toModelDetail()!!, requireNotNull(model.inputModalities))
}

/** 不把模型名里的 B 当作参数来源。 */
internal fun realAtlasParameterDetail(id: String): ModelDetail = runBlocking(Dispatchers.IO) {
    val model = bundledSnapshot().models.first { it.id == id }
    ModelDetailRecord(model, emptyList()).toModelDetail()!!
}
