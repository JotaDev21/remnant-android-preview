package com.liferpg.domain

import com.liferpg.data.Perfil
import com.liferpg.data.Preferencias
import org.junit.Assert.*
import org.junit.Test

class NutritionJournalTest {
    private fun profile(age: Int = 30, height: Int = 180, sex: String = "MASCULINO") = Perfil(nome="Atleta", classe="TITAN", pesoCorporalKg=80f, idade=age, alturaCm=height, sexo=sex, criadoEm=1)
    private fun preferences() = Preferencias(objetivo="GERAL",experiencia="INICIANTE",localPreferido="ACADEMIA",diasPorSemana=3,descansoPadraoSeg=90,metaProteinaG=0,rotinaAtiva=null,lembretesAtivos=false)

    @Test fun personalizedTargetsAreHonoredAndDoNotDependOnWorkoutGoal() {
        val p = preferences().copy(metasNutricaoManuais=true,metaCalorias=2300,metaProteinaG=170,metaCarboG=270,metaGorduraG=60)
        val target = NutricaoEngine.calcular(profile(),p)
        assertEquals(2300,target.calorias);assertEquals(170,target.proteinaG);assertEquals(270,target.carboG);assertEquals(60,target.gorduraG)
        assertEquals(target,NutricaoEngine.calcular(profile(),p.copy(objetivo="RESISTENCIA")))
    }
    @Test fun incompleteOrMinorProfileNeverReceivesGuessedCalories() {
        for(p in listOf(profile(0),profile(height=0),profile(sex="OUTRO"),profile(17))) {
            val targets = NutricaoEngine.calcular(p,preferences())
            assertEquals(0,targets.calorias);assertEquals(0,targets.carboG);assertEquals(0,targets.gorduraG)
        }
        assertEquals(0,NutricaoEngine.calcular(profile(17),preferences()).proteinaG)
    }
    @Test fun enduranceDoesNotCreateAnAutomaticCalorieDeficit() {
        val prefs = preferences().copy(atividadeNutri="MODERADA")
        val maintenance = NutricaoEngine.calcular(profile(),prefs)
        assertEquals(1780,maintenance.bmr)
        assertEquals(2759,maintenance.calorias)
        assertEquals(maintenance.calorias,NutricaoEngine.calcular(profile(),prefs.copy(objetivo="RESISTENCIA")).calorias)
        assertTrue(NutricaoEngine.calcular(profile(),prefs.copy(objetivoNutri="REDUZIR")).calorias < maintenance.calorias)
    }
    @Test fun plateRequiresVerifiedIdsAndRejectsInvalidPortions() {
        val valid = PratoParser.ler("""{"alimentos":[{"id":"taco:3","nome":"Arroz cozido","gramas":150,"confianca":"alta"},{"id":"inventado","nome":"Molho","gramas":20,"confianca":"alta"}],"observacao":"Confira o preparo."}""",setOf("taco:3"))
        assertEquals("taco:3",valid.componentes[0].alimentoId);assertNull(valid.componentes[1].alimentoId)
        for(weight in listOf("-1","0","3001","\"NaN\"")) {
            assertTrue(runCatching { PratoParser.ler("""{"alimentos":[{"id":"taco:3","nome":"Arroz","gramas":$weight}],"observacao":""}""",setOf("taco:3")) }.isFailure)
        }
    }
    @Test fun cameraCanReturnNoFoodWithoutInventingNutrients() {
        assertTrue(PratoParser.ler("""{"alimentos":[],"observacao":"Mostre o prato inteiro."}""",emptySet()).componentes.isEmpty())
    }
}
