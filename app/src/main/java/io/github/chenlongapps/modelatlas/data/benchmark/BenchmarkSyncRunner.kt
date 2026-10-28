package io.github.chenlongapps.modelatlas.data.benchmark

import io.github.chenlongapps.modelatlas.data.local.BenchmarkScoreDao
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val MAX_MANIFEST_BYTES = 64 * 1024
internal const val MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024

/** 只操作评测快照，不读写模型目录或收藏。 */
internal interface BenchmarkSnapshotStore {
    suspend fun version(): Long?
    suspend fun initialize(version: Long, scores: List<BenchmarkScoreEntity>)
    suspend fun replaceIfNewer(version: Long, scores: List<BenchmarkScoreEntity>): Boolean
}

internal class RoomBenchmarkSnapshotStore(private val dao: BenchmarkScoreDao) : BenchmarkSnapshotStore {
    override suspend fun version() = dao.version()
    override suspend fun initialize(version: Long, scores: List<BenchmarkScoreEntity>) = dao.initialize(version, scores)
    override suspend fun replaceIfNewer(version: Long, scores: List<BenchmarkScoreEntity>) = dao.replaceIfNewer(version, scores)
}

/** 可独立验证的同一条生产链路；调用者负责 IO dispatcher、日志和异常边界。 */
internal class BenchmarkSyncRunner(
    private val store: BenchmarkSnapshotStore,
    private val bundled: () -> ByteArray,
    private val download: suspend (String, Int) -> ByteArray,
) {
    private val mutex = Mutex()

    suspend fun sync(manifestUrl: String): Boolean = mutex.withLock {
        if (store.version() == null) {
            val bytes = bundled()
            require(bytes.size <= MAX_SNAPSHOT_BYTES)
            val snapshot = BenchmarkJson.decodeFromString<BenchmarkSnapshot>(bytes.decodeToString(throwOnInvalidSequence = true))
            store.initialize(snapshot.version, snapshot.validated())
        }
        val localVersion = store.version() ?: return@withLock false
        if (manifestUrl.isBlank()) return@withLock false
        val manifest = download(manifestUrl, MAX_MANIFEST_BYTES)
        require(manifest.size <= MAX_MANIFEST_BYTES)
        updateBenchmarkSnapshot(localVersion, manifest,
            download = { url -> download(url, MAX_SNAPSHOT_BYTES).also { require(it.size <= MAX_SNAPSHOT_BYTES) } },
            commit = store::replaceIfNewer)
    }
}
