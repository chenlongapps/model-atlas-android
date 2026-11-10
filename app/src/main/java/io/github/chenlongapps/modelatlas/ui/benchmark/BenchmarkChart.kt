package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min

/** 十进制计算避免极小差值、极大有限成绩的减法溢出；范围不随可视窗口滚动变化。 */
internal data class BenchmarkChartRange(val start: BigDecimal, val end: BigDecimal) {
    fun fraction(value: Double): Float = BigDecimal.valueOf(value).subtract(start)
        .divide(end.subtract(start), MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
}

internal fun benchmarkChartRange(values: List<Double>, minScore: Double? = null, maxScore: Double? = null): BenchmarkChartRange {
    val numbers = values.filter(Double::isFinite).map(BigDecimal::valueOf)
    if (numbers.isEmpty()) return BenchmarkChartRange(BigDecimal.ZERO, BigDecimal.ONE)
    val low = numbers.min()
    val high = numbers.max()
    val span = high.subtract(low)
    val padding = if (span.signum() > 0) span.multiply(BigDecimal("0.1"))
        else low.abs().multiply(BigDecimal("0.05")).takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    val rawStart = low.subtract(padding)
    val rawEnd = high.add(padding)
    val rawStep = rawEnd.subtract(rawStart).divide(BigDecimal(4), MathContext.DECIMAL64)
    val magnitude = BigDecimal.ONE.scaleByPowerOfTen(rawStep.precision() - rawStep.scale() - 1)
    val step = listOf(1, 2, 5, 10).map { magnitude.multiply(BigDecimal(it)) }.first { it >= rawStep }
    var start = rawStart.divide(step, 0, RoundingMode.FLOOR).multiply(step)
    var end = rawEnd.divide(step, 0, RoundingMode.CEILING).multiply(step)
    minScore?.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)?.takeIf { it <= low }?.let { start = start.max(it) }
    maxScore?.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)?.takeIf { it >= high }?.let { end = end.min(it) }
    // 错误/退化定义边界不能令有效成绩范围坍缩。
    if (start >= end) {
        start = rawStart
        end = rawEnd
    }
    return BenchmarkChartRange(start, end)
}

internal fun formatChartTick(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

/** 品牌主色仅用于图表标识，不随排名/主题种子变化；黑白标识使用中性色。 */
private val LabChartColors = mapOf(
    "ai21" to Color(0xFFEE6D50), "alibaba" to Color(0xFF615CED),
    "anthropic" to Color(0xFFD97757), "cohere" to Color(0xFF39594D),
    "deepseek" to Color(0xFF4D6BFE), "google" to Color(0xFF4285F4),
    "inception" to Color(0xFF7755CC), "meta" to Color(0xFF0866FF),
    "minimax" to Color(0xFFF23F5D), "mistral" to Color(0xFFFF7000),
    "moonshotai" to Color(0xFF202020), "nvidia" to Color(0xFF76B900),
    "openai" to Color(0xFF10A37F), "perplexity" to Color(0xFF20808D),
    "poolside" to Color(0xFFEB653D), "reflection" to Color(0xFF333333),
    "stepfun" to Color(0xFF1668FF), "thinkingmachines" to Color(0xFF303030),
    "vispark" to Color(0xFF7856FF), "vivgrid" to Color(0xFF643BFF),
    "xai" to Color(0xFF202020), "xiaomi" to Color(0xFFFF6900),
    "zhipuai" to Color(0xFF3856D9),
)

internal fun labChartColor(lab: String?, track: Color): Color {
    val fallback = listOf(Color(0xFF5470C6), Color(0xFF33917C), Color(0xFF9365B8), Color(0xFFBD7045))
    val base = LabChartColors[lab] ?: fallback[((lab.orEmpty().hashCode().toLong() and 0x7fffffff) % fallback.size).toInt()]
    val target = if (track.luminance() < 0.5f) Color.White else Color.Black
    for (index in 0..10) {
        val color = lerp(base, target, index / 10f)
        if ((max(color.luminance(), track.luminance()) + 0.05f) /
            (min(color.luminance(), track.luminance()) + 0.05f) >= 3f) return color
    }
    return target
}
