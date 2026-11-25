package io.github.chenlongapps.modelatlas.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelParameters
import java.math.BigDecimal

/** B 是来源单位；不缩写成 T、不截断小数，也不转换成参数个数。 */
internal fun formatParameterCount(parameters: ModelParameters?): String? {
    val total = parameters?.totalBillion?.let { "${it.parameterDigits()}B" }
    val active = parameters?.activeBillion?.let { "A${it.parameterDigits()}B" }
    return when {
        total == null -> active
        active == null -> total
        else -> "$total-$active"
    }
}

@Composable
internal fun parameterCountDescription(parameters: ModelParameters?): String {
    val total = parameters?.totalBillion
    val active = parameters?.activeBillion
    val noData = stringResource(R.string.no_data)
    if (total == null && active == null) return noData
    return stringResource(R.string.parameter_count_spoken,
        total?.let { "${it.parameterDigits()} B" } ?: noData,
        active?.let { "${it.parameterDigits()} B" } ?: noData)
}

private fun BigDecimal.parameterDigits(): String = stripTrailingZeros().toPlainString()
