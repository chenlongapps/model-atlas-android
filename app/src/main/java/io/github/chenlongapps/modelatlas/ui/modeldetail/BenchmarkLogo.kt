package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R

/** 显式定义 ID 映射，不剥离版本或按显示名称猜测；未知和 unmapped 成绩使用独立兜底图。 */
@DrawableRes
internal fun benchmarkLogoResource(benchmarkId: String?): Int = when (benchmarkId) {
    "artificial-analysis-intelligence-index-v5", "gdpval-aa-v2.1" -> R.drawable.ic_benchmark_artificial_analysis
    "automationbench-v1.0.6" -> R.drawable.ic_benchmark_automationbench
    "chartography" -> R.drawable.ic_benchmark_chartography
    "deepswe-v1.1" -> R.drawable.ic_benchmark_deepswe
    "humanitys-last-exam" -> R.drawable.ic_benchmark_humanitys_last_exam
    "osworld-v2.1" -> R.drawable.ic_benchmark_osworld
    "swe-bench-pro-v2" -> R.drawable.ic_benchmark_swe_bench_pro
    "terminal-bench-science-v0.1" -> R.drawable.ic_benchmark_terminal_bench_science
    "terminal-bench-v4" -> R.drawable.ic_benchmark_terminal_bench
    "vals-index-v2.2" -> R.drawable.ic_benchmark_vals_index
    else -> R.drawable.ic_benchmark_logo_fallback
}

internal fun benchmarkLogoWidth(benchmarkId: String?) = when (benchmarkLogoResource(benchmarkId)) {
    R.drawable.ic_benchmark_automationbench, R.drawable.ic_benchmark_chartography,
    R.drawable.ic_benchmark_swe_bench_pro, R.drawable.ic_benchmark_vals_index -> 88.dp
    else -> 40.dp
}

@Composable
internal fun BenchmarkLogo(benchmarkId: String?, modifier: Modifier = Modifier) {
    Box(modifier.size(benchmarkLogoWidth(benchmarkId), 40.dp)
        .clip(RoundedCornerShape(10.dp)).background(Color.White)
        .testTag("benchmark-logo:${benchmarkId.orEmpty()}")) {
        // 字标也完整等比显示，不能裁成方形；名称已提供身份，不重复朗读图片。
        Image(painterResource(benchmarkLogoResource(benchmarkId)), contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(4.dp))
    }
}
