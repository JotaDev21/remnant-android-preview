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
import androidx.compose.ui.unit.dp
import com.liferpg.data.DailySteps
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
class CardioTrackingLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun content(block: @Composable () -> Unit) { compose.setContent { LifeRpgTheme {
        CompositionLocalProvider(LocalContentColor provides TextoPrimario, LocalDensity provides Density(1f, 1.3f)) {
            Column(Modifier.fillMaxSize().background(Fundo).padding(22.dp)) { block() }
        }
    } } }
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
        val image = compose.runOnIdle { val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        val file = File("../../dist/qa-alpha36/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun dailyStepsShowsConsolidatedRealCountAndAllowsChangingGoal() {
        var updated = 0; var refreshed = false
        content { StepsDashboard(DailySteps("READY", 6500L, updatedAt = 1_791_480_000_000), 8000, false, {}, { refreshed = true }, { updated = it }) }
        compose.onNodeWithText("6.500").assertIsDisplayed()
        compose.onNodeWithText("Atualizar").performClick(); assertTrue(refreshed)
        shot("cardio-steps-large-font")
        compose.onNodeWithText("Editar meta").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("10000")
        compose.onNodeWithText("Salvar meta").performClick(); assertEquals(10_000, updated)
    }
    @Test fun permissionStateNeverShowsZeroAndHasClearConnectionAction() {
        var opened = false
        content { StepsDashboard(DailySteps("PERMISSION"), 8000, false, { opened = true }, {}, {}) }
        compose.onNodeWithText("Conectar passos").assertIsDisplayed().performClick(); assertTrue(opened)
        compose.onNodeWithText("0").assertDoesNotExist()
        shot("cardio-steps-permission-large-font")
    }
    @Test fun liveGpsSessionShowsMeasuredDistanceStepsAndDistanceGoal() {
        val clock = CardioClock(600_000, 1, 1_791_480_000_000)
        val row = CardioEngine.start(CardioActivity.WALK, CardioGoal.DISTANCE, 1.0, clock.copy(elapsed = 0))
            .copy(source = "GPS", distanceKm = 1.2, steps = 1530)
        content { CardioLive(row, clock, false, {}, {}, {}) }
        compose.onNodeWithText("1,20 km").assertIsDisplayed(); compose.onNodeWithText("1530").assertIsDisplayed()
        compose.onNodeWithText("Meta de distância alcançada.").assertIsDisplayed()
        compose.onNodeWithText("Concluir").assertIsDisplayed()
        shot("cardio-gps-live-large-font")
    }
    @Test fun gpsReviewKeepsMeasuredDistanceInsteadOfOfferingAManualOverride() {
        val clock = CardioClock(600_000, 1, 1_791_480_000_000)
        val row = CardioEngine.start(CardioActivity.WALK, CardioGoal.FREE, 0.0, clock).copy(status = "PAUSED", source = "GPS", accumulatedMs = 600_000, distanceKm = 1.2, steps = 1530)
        content { CardioFinishSheet(row, clock, false, null, {}, { _, _, _, _, _ -> }) }
        compose.onNodeWithText("Distância em km · opcional").assertDoesNotExist()
        compose.onNodeWithText("1,20 km medidos por GPS").assertIsDisplayed(); compose.onNodeWithText("Salvar cardio").assertIsDisplayed()
    }
}
