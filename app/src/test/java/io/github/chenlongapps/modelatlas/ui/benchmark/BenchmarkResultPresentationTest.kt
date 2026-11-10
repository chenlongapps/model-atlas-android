package io.github.chenlongapps.modelatlas.ui.benchmark

import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoardEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkResultPresentationTest {
    private val entry = BenchmarkBoardEntry("a", "lab/a", "Claude Fable 5.2", "lab", 72.4, null)

    @Test fun appendsOnlyTheExplicitEffortWithoutChangingSourceData() {
        val result = entry.copy(reasoningEffort = " Max ", variant = "thinking", harness = "Agent X")
        assertEquals("Claude Fable 5.2 · Max", formatBoardModelName(result))
        assertEquals("Claude Fable 5.2 · high", formatBoardModelName(result.copy(reasoningEffort = "high")))
        assertEquals(" Max ", result.reasoningEffort)
        assertEquals("Claude Fable 5.2", result.modelName)
    }

    @Test fun absentOrBlankEffortDoesNotAddASeparatorOrUseOtherConditions() {
        for (effort in listOf(null, "", "  ", "\n\t")) {
            assertEquals(entry.modelName, formatBoardModelName(entry.copy(
                reasoningEffort = effort, variant = "thinking", harness = "Agent X", dataset = "public", toolsEnabled = true,
            )))
        }
    }
}
