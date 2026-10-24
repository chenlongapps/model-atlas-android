package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.local.FavoriteDao
import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.local.ModelOfferingPriceRow
import io.github.chenlongapps.modelatlas.data.mapper.toModelSummaries
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface FavoriteRepository {
    fun observeFavorites(): Flow<List<FavoriteModel>>
    fun observeFavorite(id: String): Flow<FavoriteIdentity?>
    suspend fun add(id: String)
    suspend fun remove(id: String)
}

@Singleton
class RoomFavoriteRepository @Inject constructor(
    private val dao: FavoriteDao,
    private val clock: Clock,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : FavoriteRepository {
    override fun observeFavorites(): Flow<List<FavoriteModel>> = dao.observeFavorites().map { records ->
        records.map { record ->
            val f = record.favorite
            val summary = record.model?.let { detail ->
                val m = detail.model
                val rows = listOf(ModelListRow(m.id, m.name, m.description, m.type,
                    m.inputModalities, m.outputModalities, null, null, m.releaseDate,
                    m.contextLimitTokens, m.outputLimitTokens, m.openWeights, m.license))
                val prices = detail.offerings.mapNotNull { (offering, provider) ->
                    offering.pricing?.let { ModelOfferingPriceRow(m.id, provider.id, provider.name, offering.id, it) }
                }
                rows.toModelSummaries(mapOf(m.id to prices)).singleOrNull()
            }
            FavoriteModel(FavoriteIdentity(f.modelId, f.name, f.lab), f.favoritedAtEpochMillis, summary)
        }
    }.distinctUntilChanged().flowOn(dispatcher)

    override fun observeFavorite(id: String): Flow<FavoriteIdentity?> = dao.observeFavorite(id).map { f ->
        f?.let { FavoriteIdentity(it.modelId, it.name, it.lab) }
    }.distinctUntilChanged().flowOn(dispatcher)

    override suspend fun add(id: String) = withContext(dispatcher) { dao.add(id, clock.millis()) }
    override suspend fun remove(id: String) = withContext(dispatcher) { dao.remove(id) }
}
