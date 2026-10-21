package io.github.chenlongapps.modelatlas.data.local

import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSnapshotTest {
    private val snapshot = CatalogSnapshot(
        models = listOf(
            ModelEntity("lab/a", "a", "A", 1, contextLimitTokens = 100),
            ModelEntity("lab/b", "b", "B", 1),
        ),
        providers = listOf(ProviderEntity("p", "p", "Provider", 2)),
        offerings = listOf(
            ModelOfferingEntity("p", "a", "a", "A", 2, pricing = OfferingPricingLocal(input = 1.0)),
        ),
    )

    @Test fun ignoresDictionaryOrderAndLocalFetchTimes() {
        assertTrue(snapshot.hasSameContentAs(snapshot.copy(
            models = snapshot.models.reversed().map { it.copy(fetchedAtEpochMillis = 100) },
            providers = snapshot.providers.map { it.copy(fetchedAtEpochMillis = 200) },
            offerings = snapshot.offerings.map { it.copy(fetchedAtEpochMillis = 200) },
        )))
    }

    @Test fun detectsAdditionsDeletionsMetadataProviderAndPriceChanges() {
        val changes = listOf(
            snapshot.copy(models = snapshot.models + ModelEntity("lab/c", "c", "C", 3)),
            snapshot.copy(models = snapshot.models.drop(1)),
            snapshot.copy(models = snapshot.models.map { it.copy(contextLimitTokens = 200) }),
            snapshot.copy(providers = snapshot.providers.map { it.copy(name = "Renamed") }),
            snapshot.copy(offerings = snapshot.offerings.map { it.copy(pricing = OfferingPricingLocal(input = 0.0)) }),
            snapshot.copy(offerings = snapshot.offerings.map { it.copy(pricing = null) }),
            snapshot.copy(offerings = emptyList()),
        )
        changes.forEach { assertFalse(snapshot.hasSameContentAs(it)) }
    }

    @Test fun preservesNullVersusEmptyCollectionsAndExplicitFalse() {
        assertFalse(snapshot.hasSameContentAs(snapshot.copy(
            models = snapshot.models.map { it.copy(inputModalities = emptyList()) },
        )))
        assertFalse(snapshot.hasSameContentAs(snapshot.copy(
            models = snapshot.models.map { it.copy(openWeights = false) },
        )))
    }

    @Test fun modelIdsIgnoreOrderMetadataProvidersAndOfferings() {
        assertTrue(snapshot.hasSameModelIdsAs(snapshot.copy(
            models = snapshot.models.reversed().map { it.copy(name = "Renamed", type = "embedding", contextLimitTokens = 200) },
            providers = emptyList(),
            offerings = emptyList(),
        )))
        assertTrue(snapshot.copy(models = emptyList()).hasSameModelIdsAs(
            CatalogSnapshot(emptyList(), emptyList(), emptyList()),
        ))
    }

    @Test fun modelIdsDetectAdditionsDeletionsAndEqualCountReplacements() {
        assertFalse(snapshot.hasSameModelIdsAs(snapshot.copy(models = snapshot.models + ModelEntity("lab/c", "c", "C", 3))))
        assertFalse(snapshot.hasSameModelIdsAs(snapshot.copy(models = snapshot.models.drop(1))))
        assertFalse(snapshot.hasSameModelIdsAs(snapshot.copy(models = snapshot.models.map { it.copy(id = "new/${it.id}") })))
        assertFalse(snapshot.hasSameModelIdsAs(snapshot.copy(models = emptyList())))
    }

    @Test fun catalogExistenceIncludesProvidersAndUnlinkedOfferings() {
        assertTrue(CatalogSnapshot(emptyList(), emptyList(), emptyList()).isEmpty)
        assertFalse(snapshot.copy(models = emptyList(), offerings = emptyList()).isEmpty)
        assertFalse(snapshot.copy(models = emptyList()).isEmpty)
    }
}
