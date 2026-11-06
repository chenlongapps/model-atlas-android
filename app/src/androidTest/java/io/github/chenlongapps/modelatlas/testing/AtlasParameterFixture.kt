package io.github.chenlongapps.modelatlas.testing

import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.benchmark.readBenchmarkBytes
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.mapAtlas
import io.github.chenlongapps.modelatlas.data.mapper.toModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.remote.atlas.AtlasReleaseLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 页面测试读取真实发布原字节并经过生产映射；不把模型名里的 B 当作参数来源。 */
internal fun realAtlasParameterDetail(id: String): ModelDetail = runBlocking(Dispatchers.IO) {
    val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    val release = AtlasReleaseLoader { path, limit -> assets.open("atlas/$path").use { it.readBenchmarkBytes(limit) } }.load()
    val model = CatalogMapper().mapAtlas(release, emptyMap(), 0, 0).models.first { it.id == id }
    ModelDetailRecord(model, emptyList()).toModelDetail()!!
}
