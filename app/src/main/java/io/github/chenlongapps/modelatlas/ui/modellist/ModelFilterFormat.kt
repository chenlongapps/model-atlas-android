package io.github.chenlongapps.modelatlas.ui.modellist

import java.math.BigDecimal

/** 筛选是精确门槛，不能沿用卡片最多一位小数的近似值。 */
internal fun formatFilterTokenCount(value: Long): String = when {
    value >= 1_000_000 -> BigDecimal.valueOf(value).movePointLeft(6).stripTrailingZeros().toPlainString() + "M"
    value >= 1_000 -> BigDecimal.valueOf(value).movePointLeft(3).stripTrailingZeros().toPlainString() + "K"
    else -> value.toString()
}
