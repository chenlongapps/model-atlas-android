package io.github.chenlongapps.modelatlas.data.local

import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.CatalogReleaseEntity

data class CatalogSnapshot(
    val models: List<ModelEntity>,
    val providers: List<ProviderEntity>,
    val offerings: List<ModelOfferingEntity>,
    // null 表示旧本地操作只替换三表；非 null 表示同一发布包的全量评测（空列表同样有效）。
    val scores: List<BenchmarkScoreEntity>? = null,
    val definitions: List<BenchmarkDefinitionEntity>? = null,
    val release: CatalogReleaseEntity? = null,
) {
    val isEmpty: Boolean get() = models.isEmpty() && providers.isEmpty() && offerings.isEmpty()

    /** 字典顺序和本地获取时间不属于目录内容；嵌套值对象保留原始语义。 */
    fun hasSameContentAs(other: CatalogSnapshot): Boolean =
        models.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } ==
            other.models.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } &&
            providers.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } ==
            other.providers.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } &&
            offerings.associate { (it.providerId to it.id) to it.copy(fetchedAtEpochMillis = 0) } ==
            other.offerings.associate { (it.providerId to it.id) to it.copy(fetchedAtEpochMillis = 0) } &&
            scores?.associateBy { it.id } == other.scores?.associateBy { it.id } &&
            definitions?.associateBy { it.id } == other.definitions?.associateBy { it.id } &&
            release?.dataVersion == other.release?.dataVersion
}
