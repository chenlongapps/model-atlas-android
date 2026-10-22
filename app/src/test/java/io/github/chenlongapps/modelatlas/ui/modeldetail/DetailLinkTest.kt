package io.github.chenlongapps.modelatlas.ui.modeldetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLinkTest {
    @Test fun permitsOnlyValidHttpLinksWithHostsAndWithoutCredentials() {
        listOf("https://example.org/model?a=1#weights", "http://example.org", "HTTPS://example.org/paper").forEach {
            assertTrue(it, isSafeDetailUrl(it))
        }
        listOf("", " ", "javascript:alert(1)", "intent://example.org", "file:///data/local", "content://private",
            "https:///missing", "https://user:pass@example.org", "https://example.org/bad space", "/relative").forEach {
            assertFalse(it, isSafeDetailUrl(it))
        }
    }
}
