package io.github.chenlongapps.modelatlas.ui.modellist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelLicenseFormatTest {
    @Test fun keepsShortLicenseNamesWithoutInventingAliases() {
        assertEquals("MIT", shortLicenseLabel("MIT"))
        assertEquals("Apache-2.0", shortLicenseLabel(" Apache-2.0 "))
        assertEquals("BSD-3-Clause", shortLicenseLabel("BSD-3-Clause"))
        assertEquals("custom-license", shortLicenseLabel("custom-license"))
        assertEquals("1234567890123456", shortLicenseLabel("1234567890123456"))
    }

    @Test fun omitsMissingBlankAndLongLicenseNamesWithoutTruncation() {
        assertNull(shortLicenseLabel(null))
        assertNull(shortLicenseLabel(" \n "))
        assertNull(shortLicenseLabel("12345678901234567"))
        assertNull(shortLicenseLabel("Very Long Model Community License Agreement"))
    }
}
