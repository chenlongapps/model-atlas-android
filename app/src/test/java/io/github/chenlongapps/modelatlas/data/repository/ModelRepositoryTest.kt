package io.github.chenlongapps.modelatlas.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRepositoryTest {
    private val repository = ModelRepository()

    @Test
    fun getModelsReturnsSampleModelsWithUniqueIds() {
        val models = repository.getModels()

        assertEquals(3, models.size)
        assertEquals(models.size, models.map { it.id }.distinct().size)
    }

    @Test
    fun sampleModelsContainAllDisplayFields() {
        val models = repository.getModels()

        assertFalse(models.isEmpty())
        assertTrue(models.all { model ->
            model.id.isNotBlank() && model.name.isNotBlank() &&
                model.provider.isNotBlank() && model.description.isNotBlank()
        })
    }
}
