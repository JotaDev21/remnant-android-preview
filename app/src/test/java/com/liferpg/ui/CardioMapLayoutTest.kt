package com.liferpg.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.liferpg.data.CardioSession
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardioMapLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = CardioClock(600_000, 1, 1_791_480_000_000)
    private val row = CardioEngine.start(CardioActivity.WALK, CardioGoal.FREE, 0.0, now.copy(elapsed = 0))
        .copy(source = "GPS", distanceKm = 1.2, steps = 1530)
    private fun content(block: @Composable () -> Unit) { compose.setContent { LifeRpgTheme {
        CompositionLocalProvider(LocalContentColor provides TextoPrimario, LocalDensity provides Density(1f, 1.3f)) {
            Column(Modifier.fillMaxSize().background(Fundo)) { block() }
        }
    } } }
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
        val image = compose.runOnIdle { val view = compose.activity.window.decorView
            android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) } }
        val file = java.io.File("../../dist/qa-alpha37/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun recordingKeepsDistanceAndSinglePauseFinishActionsVisibleBeforeScrolling() {
        var paused = false; var reviewed = false
        content { CardioContent(row, emptyList(), false, null, true, now, null, { _, _, _ -> }, { paused = true }, {}, { reviewed = true }, {}, {}, {},
            route = { Box(Modifier.fillMaxWidth().height(360.dp)) { Text("Map renderer placeholder") } }) }
        compose.onNodeWithText("1,20 km").assertIsDisplayed()
        compose.onAllNodesWithText("Pausar").assertCountEquals(1)
        compose.onAllNodesWithText("Concluir").assertCountEquals(1)
        compose.onNodeWithText("Pausar").assertIsDisplayed().performClick(); assertTrue(paused)
        compose.onNodeWithText("Concluir").assertIsDisplayed().performClick(); assertTrue(reviewed)
        shot("recording-controls-large-font-map-placeholder")
    }
    @Test fun routeExpandsAndClosesWithoutChangingTheCompletedSession() {
        val finished = row.copy(status = "FINISHED", accumulatedMs = 600_000)
        val route = CardioRouteState(listOf(CardioFix(-23.0, -46.0, 4f, 1000, 1)), 1200.0)
        content { CardioRouteContent(finished, route, renderer = { modifier, full, expand ->
            Box(modifier) {
                if (full) Text("Fullscreen map renderer placeholder")
                else Button(onClick = { expand!!() }) { Text("Abrir mapa em tela inteira") }
            }
        }) }
        compose.onNodeWithText("Abrir mapa em tela inteira").performClick()
        compose.onNodeWithText("Fullscreen map renderer placeholder").assertIsDisplayed()
        compose.onNodeWithText("1,20 km").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar à atividade").performClick()
        compose.onNodeWithText("Abrir mapa em tela inteira").assertIsDisplayed()
        shot("route-panel-large-font-map-placeholder")
        assertEquals(600_000L, finished.accumulatedMs); assertEquals(1200.0, route.meters, .001)
    }
    @Test fun missingTransferredCoordinatesNeverPresentAnInventedMap() {
        content { CardioRouteContent(row.copy(status = "FINISHED"), CardioRouteState(), renderer = { _, _, _ -> fail("No map for absent coordinates") }) }
        compose.onNodeWithText("Percurso salvo no aparelho de origem").assertIsDisplayed()
        compose.onNodeWithText("As coordenadas não acompanham o backup da conta. Distância e tempo continuam no resumo.").assertIsDisplayed()
    }
}
