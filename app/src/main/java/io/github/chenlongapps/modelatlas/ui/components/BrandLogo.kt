package io.github.chenlongapps.modelatlas.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val BrandLogoSize = 56.dp
internal val BrandLogoShape = RoundedCornerShape(25)

/** 固定白底以保留素材原色；圆角按短边缩放，宽字标不会因变宽而加大圆角。 */
@Composable
internal fun BrandLogo(
    @DrawableRes resourceId: Int,
    imageModifier: Modifier,
    modifier: Modifier = Modifier,
    width: Dp = BrandLogoSize,
    height: Dp = BrandLogoSize,
) {
    Box(
        modifier.size(width, height).clip(BrandLogoShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // 旁侧已提供品牌或评测名称；装饰图片不重复朗读。
        Image(painterResource(resourceId), contentDescription = null, modifier = imageModifier)
    }
}
