package io.github.chenlongapps.modelatlas.ui.model

import java.math.BigDecimal

/** B 是来源单位；不缩写成 T、不截断小数，也不转换成参数个数。 */
internal fun formatBillionParameters(value: BigDecimal): String = "${value.stripTrailingZeros().toPlainString()} B"
