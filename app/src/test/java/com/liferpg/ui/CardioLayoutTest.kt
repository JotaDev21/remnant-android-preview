package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.liferpg.data.CardioSession
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardioLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = CardioClock(1_800_000, 3, 1_791_480_000_000)
    private val start = CardioEngine.start(CardioActivity.RUN, CardioGoal.TIME, 20.0, now.copy(elapsed = 0))
    private fun content(block: @Composable () -> Unit) { compose.setContent {
        LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario, LocalDensity provides Density(1f, 1.3f)) {
            MotionScene(Modifier.fillMaxSize().background(Fundo)) { block() }
        } }
    } }
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file = File("../../dist/qa-alpha36/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun firstUseExplainsDistanceAndStartsTheChosenActivityWithoutFakeHistory() {
        var chosen: CardioActivity? = null
        content { CardioContent(null, emptyList(), false, null, true, now, null, { a, _, _ -> chosen = a }, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Seu ritmo.\nSua jornada.").assertIsDisplayed()
        shot("cardio-dashboard-large-font")
        compose.onNodeWithText("Iniciar corrida").performScrollTo().performClick(); assertEquals(CardioActivity.RUN, chosen)
        compose.onNodeWithText("Distância informada por você ou pelo painel da máquina.").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText("Seu primeiro movimento abre o histórico. Tempo, distância e ritmo ficam reunidos aqui.").assertIsDisplayed()
    }
    @Test fun activeTimerHasReachablePauseAndFinishAndLargeFontFitsAnHourSession() {
        var paused = false; var finished = false
        content { Column(Modifier.fillMaxWidth().padding(CardioTokens.inset)) {
            CardioLive(start, now.copy(elapsed = 4_500_000), false, { paused = true }, {}, { finished = true })
        } }
        compose.onNodeWithText("1:15:00").assertIsDisplayed()
        compose.onNodeWithText("Pausar").assertIsDisplayed().performClick(); assertTrue(paused)
        compose.onNodeWithText("Concluir").assertIsDisplayed().performClick(); assertTrue(finished)
        compose.onNodeWithText("Meta de tempo alcançada.").assertIsDisplayed()
        shot("cardio-active-large-font")
    }
    @Test fun finishingKeepsDraftOnFailureAndSubmitIsReachableWithLargeFont() {
        var submittedDistance: Double? = null; var submittedNote = ""
        var sending by mutableStateOf(false)
        var failure by mutableStateOf<String?>(null)
        val paused = start.copy(status = "PAUSED", accumulatedMs = 1_800_000)
        content { CardioFinishSheet(paused, now, sending, failure, {}, { _, distance, _, note, _ -> submittedDistance = distance; submittedNote = note; sending = true }) }
        compose.onNodeWithText("Distância em km · opcional").performScrollTo().performTextInput("5,0")
        compose.onNodeWithText("Anotações · opcional").performScrollTo().performTextInput("Corrida no parque")
        compose.onNodeWithText("Salvar cardio").assertIsDisplayed().performClick()
        assertEquals(5.0, submittedDistance!!, .001); assertEquals("Corrida no parque", submittedNote)
        compose.onNodeWithText("Salvando…").assertIsNotEnabled()
        compose.runOnIdle { sending = false; failure = "Falha ao salvar. Seu cardio foi mantido." }
        compose.onNodeWithText("Corrida no parque").assertExists()
        compose.onNodeWithText("Falha ao salvar. Seu cardio foi mantido.").performScrollTo().assertIsDisplayed()
        shot("cardio-finish-large-font")
    }
    @Test fun manualJournalRejectsImpossibleDurationAndMissingDistanceNeverShowsAnInventedPace() {
        var row: CardioSession? = null
        content { CardioFinishSheet(null, now, false, null, {}, { _, _, _, _, manual -> row = manual }) }
        compose.onNodeWithText("Salvar cardio").assertIsNotEnabled()
        compose.onNodeWithText("Duração em minutos").performScrollTo().performTextInput("20")
        compose.onNodeWithText("Salvar cardio").assertIsEnabled().performClick()
        assertEquals("MANUAL", row!!.source); assertEquals(1_200_000L, row!!.accumulatedMs)
        compose.onNodeWithText("Ritmo médio", substring = true).assertDoesNotExist()
        shot("cardio-manual-large-font")
    }
    @Test fun completedReceiptShowsRealContextAndNeverTurnsMissingDistanceIntoPace() {
        val completed = start.copy(status = "FINISHED", accumulatedMs = 1_800_000, distanceKm = 5.0, effort = "EASY", note = "Parque · atividade de teste", xpGained = 60)
        content { Column(Modifier.padding(CardioTokens.inset)) { CardioRecordDetails(completed) } }
        compose.onNodeWithText("6:00").assertIsDisplayed()
        compose.onNodeWithText("+60 XP · Cronômetro do app").assertIsDisplayed()
        compose.onNodeWithText("Parque · atividade de teste").assertIsDisplayed()
        shot("cardio-receipt-large-font")
    }
}
