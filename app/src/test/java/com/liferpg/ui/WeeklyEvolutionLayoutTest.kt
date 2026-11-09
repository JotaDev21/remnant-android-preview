package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.liferpg.data.SerieRegistro
import com.liferpg.data.sync.FriendEvolution
import com.liferpg.domain.WeeklyArchive
import com.liferpg.domain.WeeklyReview
import com.liferpg.ui.theme.LifeRpgTheme
import com.liferpg.ui.theme.TextoPrimario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w384dp-h832dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeeklyEvolutionLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val monday = LocalDate.of(2026, 10, 5).toEpochDay()
    private fun shot(name: String) {
        compose.waitForIdle()
        val image = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file = File("../../dist/qa-alpha32/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun weekSelectorOpensTheHistoricalCalendarWithoutClaimingTodaysGoal() {
        val rows = listOf(SerieRegistro(exercicioId = "bench", musculo = "PEITO", pesoKg = 80f, reps = 10,
            segundos = null, score = 1f, xpGanho = 10, epochDay = monday - 1, timestamp = 100))
        val weeks = WeeklyArchive.build(rows, monday + 3, 3, 9)
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            MotionScene(Modifier.fillMaxSize()) { Page("JORNADA / RETROSPECTIVA", "Sua semana,\ncom clareza.") { WeeklyArchivePanel(weeks, emptyList()) } }
        } } }
        compose.onNodeWithText("Anterior").performClick()
        compose.onNodeWithText("1 dia de treino. 1 exercício no seu histórico.").assertExists()
        compose.onNodeWithContentDescription("04/10: treino registrado").assertExists()
        compose.onNodeWithText("28/09 — 04/10 / SEMANA ENCERRADA").assertExists()
        shot("weekly-history")
        compose.onNodeWithText("Esta semana").performClick()
        compose.onNodeWithText("05/10 — 08/10 / EM ANDAMENTO").assertExists()
    }

    @Test fun friendsComparePersonalGoalsAndEvolutionWithoutClippingAtLargeFontScale() {
        val mine = WeeklyReview(monday, 3, 5, 20, listOf("Supino inclinado na máquina" to 8f), "")
        val ally = FriendEvolution(monday, 2, 2, mapOf("Supino inclinado na máquina" to 12f))
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,
            LocalDensity provides Density(1f, 1.3f)) {
            MotionScene(Modifier.fillMaxSize()) { Page("ARENA / ALIADO", "Evolução\ncompartilhada.") { FriendEvolutionPanel(ally, mine, emptyList()) } }
        } } }
        compose.onNodeWithText("3/5 dias · 60%").assertIsDisplayed()
        compose.onNodeWithText("2/2 dias · 100%").assertIsDisplayed()
        compose.onNodeWithText("Seu aliado está à frente no compromisso semanal.").assertExists()
        compose.onNodeWithText("Você · +8%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aliado · +12%").assertIsDisplayed()
        shot("friend-evolution-large-font")
    }

    @Test fun staleFriendDataShowsRefreshExplanationInsteadOfALeader() {
        val mine = WeeklyReview(monday, 3, 3, 20, emptyList(), "")
        val ally = FriendEvolution(monday - 7, 3, 0, emptyMap())
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            MotionScene(Modifier.fillMaxSize()) { Page("ARENA / ALIADO", "Compromisso semanal") { FriendEvolutionPanel(ally, mine, emptyList()) } }
        } } }
        compose.onNodeWithText("0/3 dias publicados pelo aliado.").assertIsDisplayed()
        compose.onNodeWithText("Seu aliado ainda precisa sincronizar esta semana. Semanas diferentes não entram no confronto.").assertExists()
        compose.onNodeWithText("Você está à frente no compromisso semanal.").assertDoesNotExist()
    }
}
