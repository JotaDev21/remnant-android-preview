package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.liferpg.data.sync.PactMember
import com.liferpg.data.sync.TrainingPact
import com.liferpg.ui.theme.Fundo
import com.liferpg.ui.theme.LifeRpgTheme
import com.liferpg.ui.theme.TextoPrimario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w384dp-h832dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EvolutionLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val pact=TrainingPact("pact","owner","Pacto da Forja","FORGE","forming",null,null,"2026-10-09T12:00:00Z",
        listOf(PactMember("owner","Caçador", "accepted",3,0),PactMember("ally","Aliado","pending",3,0)))
    private fun shot(name:String) {
        compose.waitForIdle()
        val image=compose.runOnIdle { val view=compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also{ view.draw(Canvas(it)) } }
        val file=File("../../dist/qa-alpha31/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use{ image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun invitedAllyCanChooseAnIndividualGoalAndAcceptFromSmallScreen() {
        var accepted=0
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) { MotionScene(Modifier.fillMaxSize()) {
            Page("ARENA / PACTOS","Juntos,\nalém da névoa.") {
                PactCard(pact,"ally",3,false,{accepted=it},{},{},{})
            }
        } } } }
        compose.onNodeWithText("Aceitar e assumir 3 dias").performScrollTo().assertIsDisplayed()
        shot("pact-invitation")
        compose.onNodeWithText("Aceitar e assumir 3 dias").performClick()
        assertEquals(3,accepted)
        compose.onNodeWithText("Iniciar expedição").assertDoesNotExist()
    }
    @Test fun activePactShowsPersonalProgressAndEveryMemberHasTheSameContributionCap() {
        val active=pact.copy(status="active",startsAt="2026-10-08T12:00:00Z",endsAt="2026-10-15T12:00:00Z",members=listOf(
            PactMember("owner","Caçador","accepted",5,3),PactMember("ally","Aliado","accepted",2,2)))
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) { MotionScene(Modifier.fillMaxSize()) { Page("ARENA / PACTOS","Pacto da Forja") {
            PactCard(active,"owner",3,false,{},{},{},{})
        } } } } }
        compose.onNodeWithText("Vida do chefe").assertExists()
        compose.onNodeWithText("3/5 dias · 60% do compromisso").assertExists()
        compose.onNodeWithText("2/2 dias · 100% do compromisso").assertExists()
        assertEquals(.8f,active.progress,.001f)
        shot("pact-active")
    }
}
