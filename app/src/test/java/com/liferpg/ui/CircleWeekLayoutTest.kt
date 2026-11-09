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
import com.liferpg.data.CircleWeekState
import com.liferpg.data.sync.*
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
class CircleWeekLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val monday=LocalDate.of(2026,10,12)
    private fun week(start:LocalDate=monday)=CircleWeek("c",start,monday.minusWeeks(3),monday,monday.plusDays(4),"America/Sao_Paulo",
        listOf(CircleWeekMember("a","jota",3,setOf(start,start.plusDays(2)),3,21,2,3600),
            CircleWeekMember("b","Aliada da Forja com um nome comprido",5,setOf(start,start.plusDays(1),start.plusDays(4)),4,30,3,5400),
            CircleWeekMember("c","Outro caçador",3,emptySet(),0,0,0,0)))
    private fun shot(name:String,dialog:Boolean=false) {
        compose.waitForIdle()
        val image=compose.runOnIdle {
            val view=if(dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?:compose.activity.window.decorView else compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file=File("../../dist/qa-alpha41/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun calendarShowsSharedDaysAndExpandedMemberConnectsToTheFriendProfileAtLargeFont() {
        var inspected=""
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            CircleWeekContent(CircleWeekState("a","c",content=week()),"Círculo da Forja","a",setOf("b"),{}, {}, {}, { inspected=it },Modifier.fillMaxSize())
        } } } }
        compose.onNodeWithContentDescription("jota, 12/10: treino compartilhado").assertExists()
        compose.onNodeWithContentDescription("jota, 13/10: sem treino compartilhado").assertExists()
        compose.onNodeWithText("Aliada da Forja com um nome comprido").performScrollTo().performClick()
        compose.onNodeWithText("4 treinos · 30 séries · 3 PRs · 90 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ver perfil de Aliada da Forja com um nome comprido").performScrollTo().performClick()
        assertEquals("b",inspected)
        shot("circle-week-member-large-font")
    }
    @Test fun historicalWeekHasAWorkingReturnAndNoCurrentWeekProgressClaim() {
        var selected:LocalDate?=monday.minusWeeks(1)
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            CircleWeekContent(CircleWeekState("a","c",monday.minusWeeks(1),week(monday.minusWeeks(1))),"Círculo da Forja","a",emptySet(),{}, {}, { selected=it }, {},Modifier.fillMaxSize())
        } } } }
        compose.onNodeWithText("05/10 — 11/10").assertIsDisplayed()
        compose.onNodeWithText("EM ANDAMENTO").assertDoesNotExist()
        compose.onNodeWithText("Voltar à semana atual").performClick(); assertNull(selected)
        shot("circle-week-history")
    }
    @Test fun accessErrorContainsNoMembersAndRetryRemainsVisible() {
        var retries=0
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            CircleWeekContent(CircleWeekState("a","c",error="Acesso encerrado"),"Círculo da Forja","a",emptySet(),{}, { retries++ }, {}, {},Modifier.fillMaxSize())
        } } } }
        compose.onNodeWithText("Acesso encerrado").assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").performClick(); assertEquals(1,retries)
        compose.onNodeWithText("jota").assertDoesNotExist()
        shot("circle-week-error")
    }
    @Test fun realModalHasAnAccessibleCloseAndTheCurrentWeekHeaderFits() {
        var closed=false
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            CircleWeekSheet(CircleWeekState("a","c",content=week()),"Círculo da Forja","a",emptySet(),{ closed=true },{}, {}, {})
        } } } }
        compose.onNodeWithText("EM ANDAMENTO").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fechar placar").assertIsDisplayed()
        shot("circle-week-modal",true)
        compose.onNodeWithContentDescription("Fechar placar").performClick(); assertTrue(closed)
    }
}
