package io.github.chenlongapps.modelatlas.data.local

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface CatalogStore {
    fun observeModelRows(): Flow<List<ModelListRow>>
    fun observeOfferingPriceRows(): Flow<List<ModelOfferingPriceRow>>
    suspend fun replaceCatalog(snapshot: CatalogSnapshot)
}

class RoomCatalogStore @Inject constructor(private val dao: CatalogDao) : CatalogStore {
    override fun observeModelRows() = dao.observeModelRows()
    override fun observeOfferingPriceRows() = dao.observeOfferingPriceRows()

    override suspend fun replaceCatalog(snapshot: CatalogSnapshot) = dao.replaceCatalog(snapshot)
}
