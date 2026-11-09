package com.liferpg.data

import androidx.test.core.app.ApplicationProvider
import com.liferpg.data.net.BrazilianFoods
import com.liferpg.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecipeBookTest {
    private fun livro() = RecipeBook.criar(BrazilianFoods(ApplicationProvider.getApplicationContext()))

    @Test fun openingWithoutSearchOffersCompleteRecipesWithRealReferenceMacros() {
        val foods = BrazilianFoods(ApplicationProvider.getApplicationContext())
        val recipes = RecipeBook.criar(foods)
        assertEquals(100, recipes.size)
        assertEquals(100, recipes.map { it.id }.distinct().size)
        assertEquals(100, RecipeBook.filtrar(recipes, "", "todas").size)
        assertTrue(recipes.all { it.ingredientes.isNotEmpty() && it.instrucoes.isNotBlank() && it.nutrientes?.let { n -> n.calorias.isFinite() && n.calorias > 0f } == true })
        val expected = listOf("410" to 100f, "3" to 120f, "561" to 90f, "100" to 70f).sumOf { (id, g) -> foods.obter("taco:$id")!!.calorias.toDouble() * g / 100 }
        assertEquals(expected.toFloat(), recipes.first().nutrientes!!.calorias, .01f)
        assertTrue(recipes.first().ingredientes.any { "grelhado" in it })
    }

    @Test fun portugueseFiltersAndGoalOrderingKeepValidSuggestionsAvailable() {
        val recipes = livro()
        assertTrue(RecipeBook.filtrar(recipes, "feijao", "todas").isNotEmpty())
        assertTrue(RecipeBook.filtrar(recipes, "", "proteina").all { it.nutrientes!!.proteina >= 20f })
        assertTrue(RecipeBook.filtrar(recipes, "", "vegetal").none { it.nome.contains("frango", true) || it.nome.contains("atum", true) })
        val quick = RecipeBook.filtrar(recipes, "", "rapida")
        assertTrue(quick.isNotEmpty() && quick.size < recipes.size && quick.all { it.minutosMontagem <= 5 })
        assertTrue(RecipeBook.filtrar(recipes, "ingrediente inexistente", "todas").isEmpty())
        assertEquals(recipes.maxOf { it.nutrientes!!.proteina }, RecipeBook.recomendar(recipes, Nutrientes(proteina = 50f)).first().nutrientes!!.proteina, .001f)
        assertEquals(recipes.maxOf { it.nutrientes!!.carbo }, RecipeBook.recomendar(recipes, Nutrientes(carbo = 60f)).first().nutrientes!!.carbo, .001f)
    }

    @Test fun everyOfflinePlateHasCompletePreparationAndRelevantVideoReferences() {
        val recipes = livro()
        assertTrue(recipes.all { r -> r.etapas.size >= 5 && r.etapas.all { it.titulo.isNotBlank() && it.descricao.length > 80 } })
        assertTrue(recipes.all { it.utensilios.isNotEmpty() && it.dicas.isNotEmpty() && it.idioma == "pt" })
        assertTrue(recipes.take(16).all { r -> r.videos.isNotEmpty() && r.videos.all { it.tecnica && RecipeInstructions.youtube(it.url) == it.url } })
        assertTrue(recipes.all { r -> r.videos.all { it.tecnica && RecipeInstructions.youtube(it.url) == it.url } })
        val chicken = recipes.first { it.id == "rem:frango-arroz" }
        assertTrue(chicken.etapas.any { it.ponto?.contains("74 °C") == true })
        assertTrue(chicken.etapas.indexOfFirst { it.titulo.startsWith("Feijão") } < chicken.etapas.indexOfFirst { it.titulo.startsWith("Frango") })
        val overnight = recipes.first { it.id == "rem:aveia-geladeira" }
        assertEquals(5, overnight.minutosMontagem)
        assertTrue(overnight.etapas.last().descricao.contains("2 horas"))
        assertTrue(recipes.first { it.id == "rem:atum-batata" }.etapas.any { it.descricao.contains("drenagem") })
    }

    @Test fun catalogueIncludesDifferentDishesAndUsesDryPastaInsteadOfInventedCookedMacros() {
        val recipes = livro()
        assertEquals(10, RecipeBook.filtrar(recipes, "", "massas").size)
        assertEquals(12, RecipeBook.filtrar(recipes, "", "saladas").size)
        assertTrue(RecipeBook.filtrar(recipes, "", "cafe").size >= 20)
        assertTrue(RecipeBook.filtrar(recipes, "cuscuz", "todas").size >= 15)
        assertTrue(RecipeBook.filtrar(recipes, "salmão", "todas").size >= 4)
        val pasta = RecipeBook.filtrar(recipes, "macarrão frango brócolis", "massas").single()
        assertTrue(pasta.ingredientes.any { "70 g" in it && "cru" in it })
        assertTrue(pasta.etapas.any { it.titulo.contains("pese seco", true) })
        val foods = BrazilianFoods(ApplicationProvider.getApplicationContext())
        val expected = listOf("40" to 70f, "408" to 100f, "100" to 100f).sumOf { (id, g) -> foods.obter("taco:$id")!!.calorias.toDouble() * g / 100 }
        assertEquals(expected.toFloat(), pasta.nutrientes!!.calorias, .01f)
        assertTrue(recipes.map { it.nome }.distinct().size == 100)
    }
}
