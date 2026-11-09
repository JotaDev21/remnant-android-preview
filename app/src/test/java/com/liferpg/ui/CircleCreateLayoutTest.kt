package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.liferpg.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircleCreateLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun shot(name:String,dialog:Boolean=false) {
        compose.waitForIdle()
        val bitmap=compose.runOnIdle {
            val view=if(dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?:compose.activity.window.decorView else compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file=File("../../dist/qa-alpha42/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun modalClampsTheInitialGoalAndSubmitsTheChosenGoalWithTrimmedName() {
        var result:Pair<String,Int>?=null
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) {
            CircleCreateDialog(false,null,9,{},create={ name,goal -> result=name to goal })
        } } }
        compose.onNodeWithContentDescription("Meta de 7 dias por semana").assertIsSelected()
        compose.onNodeWithText("Criar círculo").assertIsNotEnabled()
        compose.onNodeWithText("Nome do círculo").performTextInput("  Forja  ")
        compose.onNodeWithContentDescription("Meta de 3 dias por semana").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Meta de 3 dias por semana").assertIsSelected()
        compose.onNodeWithText("Criar círculo").assertIsDisplayed().performClick()
        assertEquals("Forja" to 3,result)
        shot("circle-create-modal",true)
    }
    @Test fun nameLimitsUseCharactersInsteadOfSurrogateCodeUnits() {
        var name by mutableStateOf("💪")
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
                CircleCreateContent(name,{name=it},3,{},false,null,{}, {},Modifier.fillMaxSize())
            }
        } } }
        compose.onNodeWithText("Criar círculo").assertIsNotEnabled()
        compose.runOnIdle { name="💪💪" }
        compose.onNodeWithText("Criar círculo").assertIsEnabled()
        compose.runOnIdle { name="a".repeat(61) }
        compose.onNodeWithText("Criar círculo").assertIsNotEnabled()
        compose.onNodeWithText("Use até 60 caracteres.").assertExists()
        compose.runOnIdle { name="💪".repeat(60) }
        compose.onNodeWithText("Criar círculo").assertIsEnabled()
    }
    @Test fun pendingCreationDisablesEditingSelectionAndClose() {
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) {
            CircleCreateDialog(true,null,3,{},create={_,_ -> fail("Pending creation submitted twice")})
        } } }
        compose.onNodeWithText("Criando círculo…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Meta de 2 dias por semana").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fechar criação de círculo").assertIsNotEnabled()
        compose.onNodeWithText("Nome do círculo").assertIsNotEnabled()
    }
    @Test fun backgroundRefreshDoesNotPretendToCreateOrBlockEditingAndClose() {
        var closed=false
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) {
            CircleCreateDialog(false,null,3,{closed=true},canSubmit=false,create={_,_ -> fail("Refresh still pending")})
        } } }
        compose.onNodeWithText("Nome do círculo").performTextInput("Forja")
        compose.onNodeWithContentDescription("Meta de 2 dias por semana").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Meta de 2 dias por semana").assertIsSelected()
        compose.onNodeWithText("Criar círculo").assertIsNotEnabled()
        compose.onNodeWithText("Criando círculo…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fechar criação de círculo").assertIsEnabled().performClick()
        assertTrue(closed)
    }
    @Test fun errorCanBeReadAndRetriedWithGoalPreservedAndFooterAtLargeFont() {
        var attempts=0; var goal by mutableIntStateOf(3)
        compose.setContent { LifeRpgTheme { MotionScene(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
                CircleCreateContent("Círculo da Forja",{},goal,{goal=it},false,"Conexão interrompida. Tente novamente.",{}, {attempts++},Modifier.fillMaxSize())
            }
        } } }
        compose.onNodeWithContentDescription("Meta de 4 dias por semana").performScrollTo().performClick()
        compose.onNodeWithText("Conexão interrompida. Tente novamente.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Criar círculo").assertIsDisplayed().performClick()
        compose.onNodeWithText("Criar círculo").performClick()
        assertEquals(2,attempts); assertEquals(4,goal)
        compose.onNodeWithContentDescription("Meta de 4 dias por semana").performScrollTo().assertIsSelected()
        shot("circle-create-large-font")
    }
}
