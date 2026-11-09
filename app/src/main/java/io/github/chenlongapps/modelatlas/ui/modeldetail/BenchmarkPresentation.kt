package io.github.chenlongapps.modelatlas.ui.modeldetail

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import java.math.BigDecimal
import java.math.RoundingMode

/** 仅格式化数值成绩；复合文本不提取中心值或换算单位，底层原值不变。 */
internal fun formatBenchmarkScore(benchmark: ModelBenchmark): String? =
    benchmark.scoreText?.takeIf(String::isNotBlank)
        ?: benchmark.score?.takeIf(Double::isFinite)?.let {
            BigDecimal.valueOf(it).setScale(1, RoundingMode.HALF_UP).toPlainString()
        }

/** 名称 A–Z、不区分大小写；同名按原始数值降序，文本／缺失在后，平分稳定且保留源下标身份。 */
internal fun alphabeticalBenchmarkEntries(benchmarks: List<ModelBenchmark>): List<IndexedValue<ModelBenchmark>> =
    benchmarks.withIndex().sortedWith(
        compareBy<IndexedValue<ModelBenchmark>, String>(String.CASE_INSENSITIVE_ORDER) { it.value.name.trim() }
            .thenByDescending { (_, benchmark) ->
                benchmark.score?.takeIf { benchmark.scoreText.isNullOrBlank() && it.isFinite() }
            },
    )
