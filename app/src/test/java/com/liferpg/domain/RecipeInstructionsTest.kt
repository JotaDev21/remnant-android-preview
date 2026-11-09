package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class RecipeInstructionsTest {
    @Test fun standaloneStepNumbersDoNotCreateEmptyCookingSteps() {
        val steps = RecipeInstructions.etapas("Heat milk gently.\r\n\r\n2\r\nPreheat oven to 150 degrees.\r\n\r\n3\r\nChill overnight.")
        assertEquals(listOf("Heat milk gently.", "Preheat oven to 150 degrees.", "Chill overnight."), steps.map { it.descricao })
    }
    @Test fun preservesQuantitiesAndUnstructuredInstructionsWithoutInventingNewSteps() {
        assertEquals("Use 1.5 cups of water and 200 g rice.", RecipeInstructions.etapas("Use 1.5 cups of water and 200 g rice.").single().descricao)
        assertEquals(listOf("Prepare.", "Bake."), RecipeInstructions.etapas("1. Prepare. 2. Bake.").map { it.descricao })
        assertEquals(listOf("Wash rice.", "Cook rice."), RecipeInstructions.etapas("STEP 1\nWash rice.\n\nSTEP 2\nCook rice.").map { it.descricao })
        assertTrue(RecipeInstructions.etapas("   ").isEmpty())
    }
    @Test fun videoLinksAreCanonicalYouTubeOnlyAndSourceHasNoEmbeddedCredentials() {
        assertNull(RecipeInstructions.youtube("http://youtube.com/watch?v=t7FK8XQ-geU"))
        assertNull(RecipeInstructions.youtube("https://evil.example/embed/t7FK8XQ-geU"))
        assertNull(RecipeInstructions.https("https://user:password@example.com/recipe"))
        assertEquals("https://www.youtube.com/watch?v=t7FK8XQ-geU", RecipeInstructions.youtube("https://www.youtube.com/embed/t7FK8XQ-geU"))
    }
}
