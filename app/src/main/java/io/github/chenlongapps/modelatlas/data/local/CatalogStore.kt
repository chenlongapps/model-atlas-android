package io.github.chenlongapps.modelatlas.data.local

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface CatalogStore {
    suspend fun hasCatalog(): Boolean
    suspend fun readCatalog(): CatalogSnapshot
    fun observeModelRows(): Flow<List<ModelListRow>>
    fun observeOfferingPriceRows(): Flow<List<ModelOfferingPriceRow>>
    fun observeModelDetail(id: String): Flow<ModelDetailRecord?>
    suspend fun replaceCatalog(snapshot: CatalogSnapshot)
}

class RoomCatalogStore @Inject constructor(private val dao: CatalogDao) : CatalogStore {
    override suspend fun hasCatalog() = dao.hasCatalog()
    override suspend fun readCatalog() = dao.readCatalog()
    override fun observeModelRows() = dao.observeModelRows()
    override fun observeOfferingPriceRows() = dao.observeOfferingPriceRows()
    override fun observeModelDetail(id: String) = dao.observeModelDetail(id)

    override suspend fun replaceCatalog(snapshot: CatalogSnapshot) = dao.replaceCatalog(snapshot)
}
