package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.liferpg.data.sync.*
import com.liferpg.domain.*
import com.liferpg.ui.theme.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SocialHubLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun shot(name:String) {
        compose.waitForIdle()
        val image=compose.runOnIdle { val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view=dialog?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        val file=File("../../dist/qa-alpha34/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun friendsAndEveryExistingMainDestinationStayVisibleAtNarrowWidthAndLargeFont() {
        var selected=0
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            var tab by remember { mutableIntStateOf(0) }
            Scaffold(containerColor=Fundo,bottomBar={ MainNavigation(tab,7) { tab=it; selected=it } }) { padding ->
                Box(Modifier.padding(padding)) { Text("Treino em andamento preservado") }
            }
        } } }
        listOf("QG","Patentes","Arsenal","Treino","Amigos","Perfil").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithContentDescription("Amigos, 7 novidades").performClick().assertIsSelected()
        assertEquals(RemnantRoute.FRIENDS_TAB,selected)
        compose.onNodeWithText("Perfil").performClick().assertIsSelected()
        assertEquals(4,selected)
        shot("main-navigation-large-font")
    }
    @Test fun inviteButtonsOpenCorrectCoopModeAndCompetitiveOptionsAreVisible() {
        var selected=SocialSection.FRIENDS
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            var section by remember { mutableStateOf(SocialSection.FRIENDS) }
            MotionScene(Modifier.fillMaxSize()) { Page("REMNANT / COMUNIDADE","Amigos.") {
                SocialHubHeader(section,SocialInbox(circles=1,pacts=2),3) { section=it; selected=it }
            } }
        } } }
        compose.onNodeWithText("Convites para Pactos").performClick()
        assertEquals(SocialSection.PACTS,selected)
        compose.onNodeWithText("Pactos").assertIsDisplayed()
        compose.onNodeWithText("Círculos").assertIsDisplayed()
        shot("social-coop-large-font")
        compose.onNodeWithText("Competitivo").performClick()
        assertEquals(SocialSection.RANKING,selected)
        compose.onNodeWithText("Ranking").assertIsDisplayed()
        compose.onNodeWithText("Duelos").performClick()
        assertEquals(SocialSection.DUELS,selected)
    }
    @Test fun searchAndInviteCodeAreAvailableWithoutRankingAndFriendProfileOpensByTap() {
        var inspected=""
        val friend=AmizadeArena("f","b","Aliado com apelido comprido para testar leitura",400f,2,"accepted",false)
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            var query by remember { mutableStateOf("") }
            Scaffold(containerColor=Fundo,bottomBar={ MainNavigation(5,0) {} }) { padding ->
                MotionScene(Modifier.fillMaxSize().padding(padding)) { Page("REMNANT / COMUNIDADE","Amigos.") {
                    SocialHubHeader(SocialSection.FRIENDS,SocialInbox(),1) {}
                    FriendsHub(listOf(friend),300f,emptyList(),false,null,query,{ query=it },"REM-123456",false,
                        { inspected=it.jogadorId },{},{},{},{})
                } }
            }
        } } }
        compose.onNodeWithText("Apelido ou código REM").assertIsDisplayed()
        compose.onNodeWithText("Meu código").assertIsDisplayed()
        shot("friends-home-large-font")
        compose.onNodeWithText("Meu código").performClick()
        compose.onNodeWithText("REM-123456").assertIsDisplayed()
        compose.onNodeWithText("Copiar código").assertIsDisplayed()
        shot("friend-invite-dialog")
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithText(friend.apelido).performScrollTo().performClick()
        assertEquals("b",inspected)
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("ana")
        compose.onNodeWithText("Nenhum perfil encontrado.").assertExists()
    }
}
