package io.github.chenlongapps.modelatlas.data.benchmark

import kotlinx.serialization.Serializable

/** 随同生成的首批快照打包；远程新增来源仍须审核客户端归属要求。 */
@Serializable
data class BenchmarkAttributions(val version: Long, val sources: List<BenchmarkAttribution>)

@Serializable
data class BenchmarkAttribution(val publisher: String, val url: String, val license: BenchmarkAttributionLicense)

@Serializable
data class BenchmarkAttributionLicense(val name: String, val licenseFile: String, val attribution: String)

internal fun BenchmarkAttributionLicense.validatedAssetPath(): String {
    val parts = licenseFile.split('/')
    require(parts.size >= 3 && parts.first() == "third_party" && parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it })
    return licenseFile
}
