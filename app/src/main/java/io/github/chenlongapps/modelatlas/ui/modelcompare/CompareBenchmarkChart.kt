package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkChartRange
import io.github.chenlongapps.modelatlas.ui.benchmark.benchmarkChartRange
import java.math.BigDecimal

internal data class CompareBenchmarkChart(val range: BenchmarkChartRange) {
    val zero: Float get() = range.fraction(0.0)
}

/** 文本成绩没有明确的中心数值；只对两个有限数值使用共同的零基准刻度。 */
internal fun compareBenchmarkChart(group: CompareBenchmarkGroup): CompareBenchmarkChart? {
    val values = listOf(group.first, group.second).map { it.chartScore() ?: return null }
    val percentage = (group.key.unit == "%" || group.key.unit.equals("percent", ignoreCase = true)) &&
        values.all { it in 0.0..100.0 }
    val range = if (percentage) BenchmarkChartRange(BigDecimal.ZERO, BigDecimal.valueOf(100)) else {
        benchmarkChartRange(values + 0.0, minScore = 0.0.takeIf { values.all { it >= 0 } },
            maxScore = 0.0.takeIf { values.all { it <= 0 } && values.any { it < 0 } })
    }
    return CompareBenchmarkChart(range)
}

internal fun ModelBenchmark.chartScore(): Double? =
    score?.takeIf { scoreText.isNullOrBlank() && it.isFinite() }
