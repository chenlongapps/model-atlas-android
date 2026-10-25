package io.github.chenlongapps.modelatlas.data.model

enum class ThemeMode {
    System, Light, Dark;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}

enum class ThemeColorSource { System, Preset, Custom }

/** 稳定标识和配色输入，不依赖 Compose；顺序同时用于外观网格。 */
enum class ThemePreset(val argb: Long, val muted: Boolean = false) {
    Blue(0xFF2563EB), Indigo(0xFF5365A0), Slate(0xFF64748B, true),
    Cyan(0xFF0891B2), Teal(0xFF0D9488), Green(0xFF65A30D), Sage(0xFF87977A, true),
    Yellow(0xFFEAB308), Orange(0xFFEA580C), Sand(0xFFAE8F7A, true),
    Red(0xFFDC2626), Rose(0xFFE11D48), Pink(0xFFC026D3), Purple(0xFF7C3AED),
}

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.System,
    val colorSource: ThemeColorSource = ThemeColorSource.System,
    val preset: ThemePreset = ThemePreset.Blue,
    val customHue: Float? = null,
) {
    init {
        require(customHue == null || (customHue.isFinite() && customHue in 0f..360f))
        require(colorSource != ThemeColorSource.Custom || customHue != null)
    }

    companion object {
        const val InitialCustomHue = 220f
    }
}
