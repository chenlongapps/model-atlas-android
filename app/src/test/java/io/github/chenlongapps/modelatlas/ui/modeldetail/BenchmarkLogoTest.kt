package io.github.chenlongapps.modelatlas.ui.modeldetail

import io.github.chenlongapps.modelatlas.R
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkLogoTest {
    @Test fun allElevenExplicitBenchmarkIdsUseTheCollectedArtwork() {
        mapOf(
            "artificial-analysis-intelligence-index-v5" to R.drawable.ic_benchmark_artificial_analysis,
            "gdpval-aa-v2.1" to R.drawable.ic_benchmark_artificial_analysis,
            "automationbench-v1.0.6" to R.drawable.ic_benchmark_automationbench,
            "chartography" to R.drawable.ic_benchmark_chartography,
            "deepswe-v1.1" to R.drawable.ic_benchmark_deepswe,
            "humanitys-last-exam" to R.drawable.ic_benchmark_humanitys_last_exam,
            "osworld-v2.1" to R.drawable.ic_benchmark_osworld,
            "swe-bench-pro-v2" to R.drawable.ic_benchmark_swe_bench_pro,
            "terminal-bench-science-v0.1" to R.drawable.ic_benchmark_terminal_bench_science,
            "terminal-bench-v4" to R.drawable.ic_benchmark_terminal_bench,
            "vals-index-v2.2" to R.drawable.ic_benchmark_vals_index,
        ).forEach { (id, resource) -> assertEquals(id, resource, benchmarkLogoResource(id)) }
    }

    @Test fun missingNamesUnmappedResultsAndUncollectedVersionsDoNotBorrowArtwork() {
        listOf(null, "", " ", "GDPval-AA", "gdpval-aa", "gdpval-aa-v9", "unmapped:gdpval-aa-v2.1",
            "terminal-bench-science", "terminal-bench-science-v9", "SWE-bench Pro", " osworld-v2.1 ").forEach {
            assertEquals(it, R.drawable.ic_benchmark_logo_fallback, benchmarkLogoResource(it))
        }
    }
}
