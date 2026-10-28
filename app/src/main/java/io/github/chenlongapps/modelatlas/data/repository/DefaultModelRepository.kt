package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.local.CatalogSnapshot
import io.github.chenlongapps.modelatlas.data.local.CatalogStore
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.toModelSummaries
import io.github.chenlongapps.modelatlas.data.mapper.toModelDetail
import io.github.chenlongapps.modelatlas.data.remote.ModelsDevApi
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import java.io.IOException
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class CatalogRateLimitException(val retryAtEpochMillis: Long, cause: Throwable? = null) :
    IOException("Catalog request throttled", cause)

@Singleton
class DefaultModelRepository @Inject constructor(
    private val api: ModelsDevApi,
    private val store: CatalogStore,
    private val mapper: CatalogMapper,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelRepository {
    private val refreshMutex = Mutex()
    private var retryNotBefore = 0L
    private var pendingSnapshot: CatalogSnapshot? = null

    override suspend fun hasLocalCatalog() = withContext(ioDispatcher) {
        store.hasCatalog()
    }

    override fun observeModels() = combine(
        store.observeModelRows(),
        store.observeOfferingPriceRows(),
    ) { rows, priceRows ->
        rows.toModelSummaries(priceRows.groupBy { it.canonicalModelId })
    }.flowOn(ioDispatcher)

    override fun observeModelDetail(id: String) = combine(store.observeModelDetail(id), store.observeBenchmarkScores(id)) { record, scores ->
        record?.toModelDetail()?.copy(benchmarks = scores.map {
            ModelBenchmark(name = it.benchmarkId, score = it.score, metric = it.metric, source = it.sourceUrl,
                date = it.evaluatedAt, dataset = it.subset, harness = it.harness, unit = it.unit,
                higherIsBetter = it.higherIsBetter, reasoningEffort = it.reasoningEffort,
                toolsEnabled = it.toolsEnabled, sourceType = it.sourceType)
        })
    }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    override suspend fun checkForUpdates(): CatalogUpdateCheckResult = refreshMutex.withLock {
        withContext(ioDispatcher) {
            val snapshot = fetchCatalog()
            val local = store.readCatalog()
            if (!snapshot.hasSameModelIdsAs(local)) {
                pendingSnapshot = snapshot
                CatalogUpdateCheckResult.UPDATE_AVAILABLE
            } else {
                val changed = !snapshot.hasSameContentAs(local)
                if (changed) store.replaceCatalog(snapshot)
                pendingSnapshot = null
                if (changed) CatalogUpdateCheckResult.UPDATED_SILENTLY else CatalogUpdateCheckResult.UNCHANGED
            }
        }
    }

    override suspend fun applyPendingUpdate(): Boolean = refreshMutex.withLock {
        withContext(ioDispatcher) {
            val snapshot = pendingSnapshot ?: return@withContext false
            store.replaceCatalog(snapshot)
            pendingSnapshot = null
            true
        }
    }

    override suspend fun refresh(): Boolean = refreshMutex.withLock {
        withContext(ioDispatcher) {
            val snapshot = fetchCatalog()
            val changed = !snapshot.hasSameContentAs(store.readCatalog())
            if (changed) store.replaceCatalog(snapshot)
            pendingSnapshot = null
            changed
        }
    }

    private suspend fun fetchCatalog(): CatalogSnapshot {
        if (clock.millis() < retryNotBefore) throw CatalogRateLimitException(retryNotBefore)
        try {
            return withContext(ioDispatcher) {
                val models = async { api.getModels() to clock.millis() }
                val providers = async { api.getProviders() to clock.millis() }
                val (modelData, modelsFetchedAt) = models.await()
                val (providerData, providersFetchedAt) = providers.await()
                mapper.map(modelData, providerData, modelsFetchedAt, providersFetchedAt)
            }
        } catch (error: HttpException) {
            if (error.code() == 429 || error.code() == 503) {
                val now = clock.millis()
                val header = error.response()?.headers()?.get("Retry-After")
                val retryAt = header?.toLongOrNull()?.takeIf { it >= 0 }
                    ?.coerceAtMost((Long.MAX_VALUE - now) / 1_000)?.let { now + it * 1_000 }
                    ?: header?.let {
                        runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
                            .getOrNull()
                    }
                retryNotBefore = maxOf(now, retryAt ?: (now + 60_000))
                throw CatalogRateLimitException(retryNotBefore, error)
            }
            throw error
        }
    }
}
