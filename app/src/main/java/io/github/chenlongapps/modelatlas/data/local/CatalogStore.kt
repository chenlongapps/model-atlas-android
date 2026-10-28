package io.github.chenlongapps.modelatlas.data.local

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity

interface CatalogStore {
    suspend fun hasCatalog(): Boolean
    suspend fun readCatalog(): CatalogSnapshot
    fun observeModelRows(): Flow<List<ModelListRow>>
    fun observeOfferingPriceRows(): Flow<List<ModelOfferingPriceRow>>
    fun observeModelDetail(id: String): Flow<ModelDetailRecord?>
    fun observeBenchmarkScores(id: String): Flow<List<BenchmarkScoreEntity>> = flowOf(emptyList())
    suspend fun replaceCatalog(snapshot: CatalogSnapshot)
}

class RoomCatalogStore @Inject constructor(private val dao: CatalogDao, private val scores: BenchmarkScoreDao) : CatalogStore {
    override suspend fun hasCatalog() = dao.hasCatalog()
    override suspend fun readCatalog() = dao.readCatalog()
    override fun observeModelRows() = dao.observeModelRows()
    override fun observeOfferingPriceRows() = dao.observeOfferingPriceRows()
    override fun observeModelDetail(id: String) = dao.observeModelDetail(id)
    override fun observeBenchmarkScores(id: String) = scores.observeForModel(id)

    override suspend fun replaceCatalog(snapshot: CatalogSnapshot) = dao.replaceCatalog(snapshot)
}
