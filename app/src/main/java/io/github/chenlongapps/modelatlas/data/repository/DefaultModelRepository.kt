package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.local.CatalogStore
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.toModelSummaries
import io.github.chenlongapps.modelatlas.data.remote.ModelsDevApi
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

    override fun observeModels() = combine(
        store.observeModelRows(),
        store.observeOfferingPriceRows(),
    ) { rows, priceRows ->
        rows.toModelSummaries(priceRows.groupBy { it.canonicalModelId })
    }.flowOn(ioDispatcher)

    override suspend fun refresh() = refreshMutex.withLock {
        if (clock.millis() < retryNotBefore) throw CatalogRateLimitException(retryNotBefore)
        try {
            withContext(ioDispatcher) {
                val models = async { api.getModels() to clock.millis() }
                val providers = async { api.getProviders() to clock.millis() }
                val (modelData, modelsFetchedAt) = models.await()
                val (providerData, providersFetchedAt) = providers.await()
                val snapshot = mapper.map(modelData, providerData, modelsFetchedAt, providersFetchedAt)
                store.replaceCatalog(snapshot)
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
