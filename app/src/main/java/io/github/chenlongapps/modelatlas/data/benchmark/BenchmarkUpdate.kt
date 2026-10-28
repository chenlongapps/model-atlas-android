package io.github.chenlongapps.modelatlas.data.benchmark

import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import java.security.MessageDigest

/** 只在完整校验后调用提交函数；版本比较在事务中再次执行。 */
internal suspend fun updateBenchmarkSnapshot(
    localVersion: Long,
    manifestBytes: ByteArray,
    download: suspend (String) -> ByteArray,
    commit: suspend (Long, List<BenchmarkScoreEntity>) -> Boolean,
): Boolean {
    val manifest = BenchmarkJson.decodeFromString<BenchmarkManifest>(manifestBytes.decodeToString(throwOnInvalidSequence = true))
    manifest.validate()
    if (manifest.version <= localVersion) return false
    val bytes = download(manifest.url)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(digest.equals(manifest.sha256, ignoreCase = true))
    val snapshot = BenchmarkJson.decodeFromString<BenchmarkSnapshot>(bytes.decodeToString(throwOnInvalidSequence = true))
    require(snapshot.version == manifest.version)
    return commit(snapshot.version, snapshot.validated())
}
