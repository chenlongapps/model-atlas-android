package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.local.CatalogDao
import io.github.chenlongapps.modelatlas.data.mapper.toBenchmarkBoards
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface BenchmarkRepository {
    fun observeBoards(): Flow<List<BenchmarkBoard>>
}

class RoomBenchmarkRepository @Inject constructor(
    private val dao: CatalogDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BenchmarkRepository {
    override fun observeBoards() = dao.observeBenchmarkRankings().map { it.toBenchmarkBoards() }
        .onStart { dao.reconcileUnspecifiedBenchmarkScores() }
        .distinctUntilChanged().flowOn(ioDispatcher)
}
