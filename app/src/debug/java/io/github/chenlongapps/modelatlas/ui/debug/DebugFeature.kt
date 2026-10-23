package io.github.chenlongapps.modelatlas.ui.debug

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListViewModel
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationItem

internal object DebugFeature {
    val navigationItem: GlassNavigationItem? = GlassNavigationItem(
        R.string.debug_nav_label, R.drawable.ic_debug, R.drawable.ic_debug_filled, "nav-debug",
    )

    @Composable
    fun Content(bottomOverlayPadding: Dp, isVisible: Boolean) {
        val viewModel: DebugViewModel = hiltViewModel()
        val catalogViewModel: ModelListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val info = remember(context) {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            DebugDeviceInfo(
                versionName = pkg.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(pkg),
                applicationId = context.packageName,
                debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
            )
        }
        // 预组合离屏页不读库；每次切入并停稳后读取，不自动联网。
        LaunchedEffect(viewModel, isVisible) { if (isVisible) viewModel.readStatistics() }
        DebugScreen(
            state, info, viewModel::readStatistics,
            onRefresh = { viewModel.refresh(onRefreshed = catalogViewModel::onCatalogRefreshedExternally) },
            bottomOverlayPadding = bottomOverlayPadding,
        )
    }
}
