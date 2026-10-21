package io.github.chenlongapps.modelatlas.data.local

import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity

data class CatalogSnapshot(
    val models: List<ModelEntity>,
    val providers: List<ProviderEntity>,
    val offerings: List<ModelOfferingEntity>,
) {
    val isEmpty: Boolean get() = models.isEmpty() && providers.isEmpty() && offerings.isEmpty()

    /** 仅比较规范模型的稳定 ID；内容、服务商和模型服务变化不属于模型增删。 */
    fun hasSameModelIdsAs(other: CatalogSnapshot): Boolean =
        models.mapTo(hashSetOf()) { it.id } == other.models.mapTo(hashSetOf()) { it.id }

    /** 字典顺序和本地获取时间不属于目录内容；嵌套值对象保留原始语义。 */
    fun hasSameContentAs(other: CatalogSnapshot): Boolean =
        models.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } ==
            other.models.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } &&
            providers.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } ==
            other.providers.associate { it.id to it.copy(fetchedAtEpochMillis = 0) } &&
            offerings.associate { (it.providerId to it.id) to it.copy(fetchedAtEpochMillis = 0) } ==
            other.offerings.associate { (it.providerId to it.id) to it.copy(fetchedAtEpochMillis = 0) }
}
