package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.liferpg.data.CameraBossProgress
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val day=LocalDate.of(2026,10,8).toEpochDay()
    private val orders=MissaoEngine.gerarMissoes(day).mapIndexed { i,m -> m.copy(id=(i+1).toLong()) }
    private val weekly=MissaoEngine.gerarSemana(day,3).copy(id=10,progresso=2)
    private fun content(board: SystemBoard, font: Float=1.3f, open: (String)->Unit={}, adapt: (SystemMode)->Unit={}) {
        compose.setContent { LifeRpgTheme {
            CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,font)) {
                MotionScene(Modifier.fillMaxSize().background(Fundo)) { SystemContent(board,open,adapt,{}) }
            }
        } }
    }
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        val bitmap=compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file=File("../../dist/qa-alpha39/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun dailyOrdersHaveRealDetailsAndReachableActionsWithLargeFont() {
        var route=""
        content(SystemBoard(day,orders,listOf(weekly),canAdapt=true,planName="Costas e bíceps"),open={route=it})
        compose.onNodeWithText("Missões e desafios").assertIsDisplayed(); shot("system-orders-large-font")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithText("Cuide da recuperação").performClick()
        compose.onNodeWithText("Registre no diário a água que você já bebeu.",substring=true).assertExists()
        compose.onNodeWithText("Abrir diário de nutrição").performScrollTo().assertIsDisplayed()
        val action=compose.onNodeWithText("Abrir diário de nutrição").fetchSemanticsNode().boundsInRoot
        assertTrue("Detail action must fit the actual phone viewport",action.bottom<=800f && action.top>=0f)
        shot("system-mission-detail-large-font")
        compose.onNodeWithText("Abrir diário de nutrição").performClick(); assertEquals(RemnantRoute.NUTRITION,route)
    }
    @Test fun adaptationShowsEveryModalityAndRoutesChoiceWithoutGrantingXp() {
        var chosen: SystemMode?=null
        content(SystemBoard(day,orders,canAdapt=true),font=1f,adapt={chosen=it})
        shot("system-orders")
        compose.onNodeWithText("Adaptar ordens").performClick()
        compose.onNodeWithText("Cardio").performScrollTo().performClick(); assertEquals(SystemMode.CARDIO,chosen)
    }
    @Test fun challengesShowCurrentBossAndOpenCameraDirectly() {
        var route=""
        content(SystemBoard(day,orders,bosses=mapOf(CameraBoss.ASH_WARDEN to CameraBossProgress(bossId=CameraBoss.ASH_WARDEN.name,trialRank=5))),open={route=it})
        compose.onNodeWithText("Provações").performClick(); shot("system-challenges-large-font")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText("Enfrentar com flexões").assertIsDisplayed();shot("system-boss-large-font")
        compose.onNodeWithText("Ordens").assertIsDisplayed()
        compose.onNodeWithText("Enfrentar com flexões").performClick();assertEquals(RemnantRoute.camera("Pushups"),route)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        compose.onNodeWithText("Pactos").performClick();assertEquals(RemnantRoute.PACTS,route)
        compose.onNodeWithText("Duelos").performClick();assertEquals(RemnantRoute.DUELS,route)
        shot("system-community-large-font")
    }
    @Test fun archiveSeparatesExpiredAndCompletedAndNeverOffersAnExpiredReward() {
        val old=orders[0].copy(id=20,epochDay=day-1,progresso=0,descricao="Ordem anterior")
        val done=orders[1].copy(id=21,epochDay=day-1,progresso=6,concluida=true,descricao="Conquista anterior")
        content(SystemBoard(day,orders,history=listOf(old,done)))
        compose.onNodeWithText("Registro").performClick();shot("system-history-large-font")
        compose.onNodeWithText("Últimos 90 dias · 60 XP recebidos em ordens").assertExists()
        compose.onNodeWithText("Encerradas").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.onNodeWithText("Ordem anterior").performClick()
        compose.onNodeWithText("Essa ordem encerrou.",substring=true).assertExists()
        compose.onNodeWithText("Ver meu treino").assertDoesNotExist()
    }
    @Test fun completedBoardAndEmptyArchiveShowHonestStates() {
        content(SystemBoard(day,orders.map { it.copy(concluida=true,progresso=it.alvoQtd) }),font=1f)
        compose.onNodeWithText("Ordens\ncumpridas.").assertIsDisplayed();shot("system-orders-completed")
        compose.onNodeWithText("Registro").performClick()
        compose.onNodeWithText("Ver ordens de hoje").performScrollTo().performClick()
        compose.onNodeWithText("Ordens\ncumpridas.").assertExists()
    }
    @Test fun weeklyContractShowsRealDaysAndAdaptationCannotResetAStartedDay() {
        var chosen: SystemMode?=null
        content(SystemBoard(day,orders,listOf(weekly),canAdapt=false,trainedDays=setOf(day-1,day)),adapt={chosen=it})
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        compose.onNodeWithText("2 / 3 dias").assertIsDisplayed();shot("system-weekly-large-font")
        compose.onNodeWithText("Ordens").assertIsDisplayed()
        // Keep the mode action below the sticky tabs instead of tapping through them.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        assertTrue(compose.onNodeWithText("Adaptar ordens").fetchSemanticsNode().boundsInRoot.top>=60f)
        compose.onNodeWithText("Adaptar ordens").performClick()
        compose.onNodeWithText("As ordens já começaram.",substring=true).assertExists()
        compose.onNodeWithText("Cardio").assertIsNotEnabled();assertNull(chosen)
        shot("system-adaptation-locked-large-font")
    }
}
