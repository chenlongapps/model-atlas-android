package io.github.chenlongapps.modelatlas.data.local

import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity

data class CatalogSnapshot(
    val models: List<ModelEntity>,
    val providers: List<ProviderEntity>,
    val offerings: List<ModelOfferingEntity>,
)
