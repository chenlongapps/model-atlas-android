package io.github.chenlongapps.modelatlas.data.benchmark

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.local.BenchmarkScoreDao
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** 进程启动后独立执行，不阻塞目录与详情的 Room 观察。 */
@Singleton
class BenchmarkSync @Inject constructor(
    @ApplicationContext context: Context,
    dao: BenchmarkScoreDao,
    client: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val manifestUrl = context.getString(R.string.benchmark_manifest_url).trim()
    private val source = BenchmarkHttpSource(client)
    private val runner = BenchmarkSyncRunner(
        RoomBenchmarkSnapshotStore(dao),
        bundled = { context.assets.open("benchmarks.json").use { it.readBenchmarkBytes(MAX_SNAPSHOT_BYTES) } },
        download = source::download,
    )

    suspend fun start() = withContext(dispatcher) {
        try {
            runner.sync(manifestUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("BenchmarkSync", "Benchmark import or update failed; existing data retained", error)
        }
    }
}
