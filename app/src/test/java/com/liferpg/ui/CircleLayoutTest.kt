package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.liferpg.data.CircleDelivery
import com.liferpg.data.sync.*
import com.liferpg.ui.theme.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w384dp-h832dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircleLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val receipt="""{"title":"Peito e tríceps","ended_at":"2026-10-08T17:00:00Z","duration_seconds":2700,"exercises":[{"id":"bench","name":"Supino inclinado na máquina","muscle":"PEITO","sets":[{"kg":80,"reps":10,"pr":true},{"kg":80,"reps":8,"pr":false}]}]}"""
    private val post=CirclePost("post","session","a","Caçador de teste","2026-10-08T17:00:01Z",receipt)
    private fun shot(name:String) {
        compose.waitForIdle()
        val image=compose.runOnIdle { val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view=dialog?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        val file=File("../../dist/qa-alpha33/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun groupBoardAndWorkoutDetailKeepContextAtLargeFontScale() {
        val circle=TrainingCircle("c","a","Círculo da Forja","America/Sao_Paulo","accepted","2026-10-05",listOf(
            CircleMember("a","Caçador de teste","accepted",3,2),CircleMember("b","Aliado de teste","accepted",5,3)))
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            var detail by remember { mutableStateOf(false) }
            MotionScene(Modifier.fillMaxSize()) { Page("ARENA / CÍRCULOS","Treine junto.") {
                CircleBoard(circle,"a",setOf("b")) {}
                Section("Atividade compartilhada")
                CirclePostRow(post) { detail=true }
            } }
            if(detail) CirclePostDialog(post,true,false,{ detail=false }) {}
        } } }
        compose.onNodeWithText("2/3 dias · 66%").assertExists()
        compose.onNodeWithText("Peito e tríceps").performScrollTo().performClick()
        compose.onNodeWithText("Supino inclinado na máquina").assertExists()
        compose.onNodeWithText("1. 80 kg × 10 rep · PR").assertExists()
        compose.onNodeWithText("Retirar do círculo").assertIsDisplayed()
        shot("circle-workout-detail-large-font")
        compose.onNodeWithText("Fechar").performClick()
        shot("circle-board-large-font")
    }
    @Test fun failedDeliveryHasRetryAndWithdrawalWithoutPretendingItWasSent() {
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            MotionScene(Modifier.fillMaxSize()) { Page("ARENA / CÍRCULOS","A atividade espera conexão.") {
                CircleDeliveryLine(CircleDelivery("q","a","c","s",receipt,"PUBLISH","failed","Acesso encerrado.",1),{}, {})
            } }
        } } }
        compose.onNodeWithText("Ação não concluída").assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
        compose.onNodeWithText("Retirar compartilhamento").assertIsDisplayed()
        compose.onNodeWithText("Resumo publicado no círculo.").assertDoesNotExist()
        shot("circle-delivery-failure")
    }
}
