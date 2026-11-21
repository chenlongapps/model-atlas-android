package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R

/**
 * LAB 前缀 → 随包彩色 logo 素材。
 *
 * 素材来自 Models.dev 的 Logos 服务，只覆盖其中 23 个实验室；键为模型稳定 ID 第一个 `/` 前的前缀，
 * 与 [`ModelSummary.lab`][io.github.chenlongapps.modelatlas.data.model.ModelSummary.lab] 一致。
 * 没有收录的 LAB 不猜测对应关系，显示兜底图。
 */
private val LabLogos: Map<String, Int> = mapOf(
    "ai21" to R.drawable.ic_lab_ai21,
    "alibaba" to R.drawable.ic_lab_alibaba,
    "anthropic" to R.drawable.ic_lab_anthropic,
    "cohere" to R.drawable.ic_lab_cohere,
    "deepseek" to R.drawable.ic_lab_deepseek,
    "google" to R.drawable.ic_lab_google,
    "inception" to R.drawable.ic_lab_inception,
    "meta" to R.drawable.ic_lab_meta,
    "minimax" to R.drawable.ic_lab_minimax,
    "mistral" to R.drawable.ic_lab_mistral,
    "moonshotai" to R.drawable.ic_lab_moonshotai,
    "nvidia" to R.drawable.ic_lab_nvidia,
    "openai" to R.drawable.ic_lab_openai,
    "perplexity" to R.drawable.ic_lab_perplexity,
    "poolside" to R.drawable.ic_lab_poolside,
    "reflection" to R.drawable.ic_lab_reflection,
    "stepfun" to R.drawable.ic_lab_stepfun,
    "thinkingmachines" to R.drawable.ic_lab_thinkingmachines,
    "vispark" to R.drawable.ic_lab_vispark,
    "vivgrid" to R.drawable.ic_lab_vivgrid,
    "xai" to R.drawable.ic_lab_xai,
    "xiaomi" to R.drawable.ic_lab_xiaomi,
    "zhipuai" to R.drawable.ic_lab_zhipuai,
)

/** LAB 有随包素材时返回资源 ID，否则返回 null，由调用方决定是否显示兜底图。 */
@DrawableRes
internal fun labLogoResOrNull(lab: String?): Int? = LabLogos[lab]

/** LAB 没有随包素材（含 LAB 缺失）时显示兜底图，不用其他实验室的素材代替。 */
@DrawableRes
internal fun labLogoRes(lab: String?): Int = labLogoResOrNull(lab) ?: R.drawable.ic_lab_logo_fallback

/** 品牌 logo 需要固定浅色底：素材保留官方配色，深色图案在深色卡片上才能看清。 */
private val LabLogoBackground: Color = Color.White

private val LabLogoChipSize: Dp = 56.dp

/**
 * 列表与详情共用的 LAB logo，放大时保持相同图形比例和圆角比例。
 *
 * 与模型名并列，仅作视觉标识；旁侧已显示同一 LAB 的文字，因此不重复提供无障碍描述。
 */
@Composable
internal fun LabLogo(
    lab: String?,
    modifier: Modifier = Modifier,
    size: Dp = LabLogoChipSize,
) {
    val hasLabLogo = labLogoResOrNull(lab) != null
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(25))
            .background(LabLogoBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(labLogoRes(lab)),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(44f / 56f)
                .testTag(if (hasLabLogo) LabLogoTag else LabLogoFallbackTag),
        )
    }
}

internal const val LabLogoTag: String = "lab-logo"
internal const val LabLogoFallbackTag: String = "lab-logo-fallback"
