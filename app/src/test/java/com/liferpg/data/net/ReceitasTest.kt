package com.liferpg.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReceitasTest {
    private fun meal(id: String = "52772", video: String = "https://youtu.be/t7FK8XQ-geU") = mapOf(
        "idMeal" to id, "strMeal" to "Chicken", "strInstructions" to "1. Prepare the ingredients.\r\n2. Cook thoroughly.",
        "strIngredient1" to "Chicken", "strMeasure1" to "100 g", "strYoutube" to video,
        "strSource" to "https://example.com/recipe", "strMealThumb" to "https://www.themealdb.com/images/media/meals/test.jpg")

    @Test fun emptyQueryDiscoversRecipesAndPortugueseIngredientReachesProviderInEnglish() = runTest {
        val queries = mutableListOf<String>()
        val api = object : MealDbApi {
            override suspend fun buscar(termo: String): MealDbResponse { queries += termo; return MealDbResponse(listOf(meal())) }
            override suspend fun porCategoria(categoria: String) = MealDbResponse(null)
        }
        val found = Receitas.buscar("   ", api).getOrThrow().single()
        Receitas.buscar(" Frango ", api).getOrThrow()
        Receitas.buscar("feijão", api).getOrThrow()
        Receitas.buscar("Arrabiata", api).getOrThrow()
        assertEquals(listOf("", "chicken", "bean", "Arrabiata"), queries)
        assertEquals("mealdb:52772", found.id)
        assertEquals(2, found.etapas.size)
        assertEquals("https://www.youtube.com/watch?v=t7FK8XQ-geU", found.videos.single().url)
        assertEquals("en", found.idioma)
        assertEquals(listOf("100 g Chicken"), found.ingredientes)
        assertNull(found.nutrientes)
    }

    @Test fun rejectsIncompleteRecipesAndUnsafeVideoOrSourceLinks() = runTest {
        val api = object : MealDbApi {
            override suspend fun buscar(termo: String) = MealDbResponse(listOf(
                meal().plus("strInstructions" to null), meal().plus("strIngredient1" to " "),
                meal(video = "https://youtube.com.evil.example/watch?v=t7FK8XQ-geU").plus("strSource" to "javascript:alert(1)"),
                meal(video = "https://youtube.com.evil.example/watch?v=t7FK8XQ-geU")))
            override suspend fun porCategoria(categoria: String) = MealDbResponse(null)
        }
        val found = Receitas.buscar("chicken", api).getOrThrow().single()
        assertTrue(found.videos.isEmpty())
        assertEquals("https://www.themealdb.com/meal/52772", found.fonteUrl)
    }
    @Test fun networkFailureAndEmptySearchRemainExplicitInsteadOfFakeRecipes() = runTest {
        val api = object : MealDbApi {
            override suspend fun buscar(termo: String): MealDbResponse = if (termo == "erro") throw java.io.IOException() else MealDbResponse(null)
            override suspend fun porCategoria(categoria: String) = MealDbResponse(null)
        }
        assertTrue(Receitas.buscar("erro", api).isFailure)
        assertTrue(Receitas.buscar("nothing", api).getOrThrow().isEmpty())
    }
    @Test fun cancellationIsNotConvertedIntoConnectionFailure() = runTest {
        val api = object : MealDbApi {
            override suspend fun buscar(termo: String): MealDbResponse = throw CancellationException()
            override suspend fun porCategoria(categoria: String) = MealDbResponse(null)
        }
        try { Receitas.buscar("cancel", api); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
}
