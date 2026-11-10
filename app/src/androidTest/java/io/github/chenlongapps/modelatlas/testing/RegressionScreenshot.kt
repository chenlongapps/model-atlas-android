package io.github.chenlongapps.modelatlas.testing

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** 定向验收时显式开启截图，不改变普通测试的执行成本。 */
internal fun saveRegressionScreenshot(name: String) {
    if (InstrumentationRegistry.getArguments().getString("captureRegression") != "true") return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("regression-screenshots"))
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally {
        bitmap.recycle()
    }
}
