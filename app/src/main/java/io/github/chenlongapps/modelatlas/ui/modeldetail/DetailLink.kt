package io.github.chenlongapps.modelatlas.ui.modeldetail

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.chenlongapps.modelatlas.R
import java.net.URI

/** 目录链接是外部输入，只允许有主机名且不带用户信息的 http(s) 地址。 */
internal fun isSafeDetailUrl(url: String): Boolean = try {
    val uri = URI(url)
    (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null
} catch (_: Exception) {
    false
}

@Composable
internal fun DetailLink(label: String, url: String) {
    val handler = LocalUriHandler.current
    var failed by rememberSaveable(url) { mutableStateOf(false) }
    val safe = isSafeDetailUrl(url)
    Column {
        TextButton(
            enabled = safe,
            onClick = {
                failed = try {
                    handler.openUri(url)
                    false
                } catch (_: ActivityNotFoundException) {
                    true
                } catch (_: IllegalArgumentException) {
                    true
                } catch (_: SecurityException) {
                    true
                }
            },
        ) { Text(stringResource(R.string.detail_open_link, label)) }
        Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!safe || failed) Text(
            stringResource(if (safe) R.string.detail_link_failed else R.string.detail_link_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
