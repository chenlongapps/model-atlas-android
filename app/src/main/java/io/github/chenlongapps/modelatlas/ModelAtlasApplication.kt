package io.github.chenlongapps.modelatlas

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkSync
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ModelAtlasApplication : Application() {
    @Inject lateinit var benchmarkSync: BenchmarkSync
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { benchmarkSync.start() }
    }
}
