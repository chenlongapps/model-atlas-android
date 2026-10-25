package io.github.chenlongapps.modelatlas.ui.settings

import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class AppearanceBackupRulesTest {
    @Test fun cloudAndDeviceTransferExcludeOnlyAppearanceFile() {
        assertEquals(
            listOf(
                Triple("cloud-backup", "file", "datastore/appearance.preferences_pb"),
                Triple("device-transfer", "file", "datastore/appearance.preferences_pb"),
            ),
            exclusions(R.xml.data_extraction_rules),
        )
    }

    @Test fun legacyRulesExcludeOnlyAppearanceFile() {
        assertEquals(listOf(Triple("full-backup-content", "file", "datastore/appearance.preferences_pb")),
            exclusions(R.xml.backup_rules))
    }

    private fun exclusions(resource: Int): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getXml(resource).use { parser ->
            var parent = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "exclude" -> result += Triple(parent, parser.getAttributeValue(null, "domain"), parser.getAttributeValue(null, "path"))
                        "include" -> error("Must not change inclusion rules for other data")
                        else -> parent = parser.name
                    }
                }
                parser.next()
            }
        }
        return result
    }
}
