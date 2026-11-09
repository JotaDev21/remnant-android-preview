package com.liferpg.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.liferpg.data.*
import com.liferpg.data.sync.*
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
class CircleConversationLayoutTest {
    @Test fun emptyCircleHasAnActionableInvitationWithoutInventedPeopleOrPosts() {
        var clicked=false
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            MotionScene(Modifier.fillMaxSize()) { Page("AMIGOS / COOP","Seu círculo.") { CircleActivityInvitation(false,true) { clicked=true } } }
        } } }
        compose.onNodeWithText("Criar Círculo privado").assertIsDisplayed().performClick(); assertTrue(clicked)
        compose.onNodeWithText("Você escolhe o que compartilhar. Fotos do shape permanecem fora do Círculo.").assertIsDisplayed()
        shot("circle-first-use-large-font")
    }
    @Test fun reactionHasImmediateMotionAndConfirmationDoesNotInventPoints() {
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            var action by remember { mutableStateOf<CircleReactionAction?>(null) }
            Column(Modifier.fillMaxSize().background(Fundo).padding(ConversationTokens.inset)) {
                CircleWorkoutStory(post,action,{ action=CircleReactionAction(true,it) },{})
            }
        } } }
        compose.mainClock.autoAdvance=false
        compose.onNodeWithContentDescription("Brutal, 2 reações").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithContentDescription("Brutal, 2 reações").assertIsNotEnabled()
        val frame=shot("circle-reaction-96ms")
        val bounds=compose.onNodeWithContentDescription("Brutal, 2 reações").fetchSemanticsNode().boundsInRoot
        var darkInk=0
        // Actual rendered label/icon pixels must survive the selection transition.
        // Exclude rounded corners: black pixels outside the pill are not readable ink.
        for(y in (bounds.top.toInt()+8).coerceAtLeast(0) until (bounds.bottom.toInt()-8).coerceAtMost(frame.height))
            for(x in (bounds.left.toInt()+12).coerceAtLeast(0) until (bounds.right.toInt()-12).coerceAtMost(frame.width)) {
                val pixel=frame.getPixel(x,y)
                if(android.graphics.Color.red(pixel)<40 && android.graphics.Color.green(pixel)<40 && android.graphics.Color.blue(pixel)<40) darkInk++
            }
        assertTrue("Selected reaction loses readable ink during transition",darkInk>50)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Confirmando reação…").assertExists()
        shot("circle-reaction-settled")
        compose.mainClock.autoAdvance=true
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val receipt="""{"title":"Peito e tríceps","ended_at":"2026-10-08T17:00:00Z","duration_seconds":2700,"exercises":[{"id":"bench","name":"Supino inclinado na máquina","muscle":"PEITO","sets":[{"kg":80,"reps":10,"pr":true},{"kg":80,"reps":8,"pr":false}]}]}"""
    private val post=CirclePost("p","s","owner","Aliado com apelido comprido para testar o espaço","2026-10-08T17:00:01Z",receipt,CircleSocial(3,2,1,CircleReaction.RESPECT,2))
    private val target=CircleConversationTarget("c","p")
    private val comments=listOf(CircleComment("c1","a","Você de teste","Quanto você descansou nessa série?","2026-10-08T17:00:02Z"),CircleComment("c2","b","Outro aliado","Dois minutos. Boa marca! 💪","2026-10-08T17:00:03Z"))
    private fun shot(name:String):Bitmap {
        if(compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val image=compose.runOnIdle { val dialog=org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }
            val view=dialog?.window?.decorView?:compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        val file=File("../../dist/qa-alpha35/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        return image
    }
    @Test fun performanceAndReactionsHaveRealContextAtLargeFontAndSmallWidth() {
        var tapped:CircleReaction?=null; var opened=false
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            MotionScene(Modifier.fillMaxSize()) { Page("CÍRCULO PRIVADO","Atividade.") { CircleWorkoutStory(post,null,{ tapped=it },{ opened=true }) } }
        } } }
        compose.onNodeWithText("80").assertExists(); compose.onNodeWithText("10 repetições").assertExists()
        compose.onNodeWithText("Supino inclinado na máquina").assertExists()
        compose.onNodeWithContentDescription("Brutal, 2 reações").performScrollTo().performClick(); assertEquals(CircleReaction.FIRE,tapped)
        compose.onNodeWithText("2 comentários").performScrollTo().performClick(); assertTrue(opened)
        shot("circle-workout-story-large-font")
    }
    @Test fun sheetComposerStaysReachableAndConversationAndSetsAreDifferentUsefulViews() {
        var sending=false; var deleted:CircleComment?=null
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            var value by remember { mutableStateOf("") }
            CircleConversationSheet(CircleConversationState(accountId="a",target=target,content=CircleConversation(post,comments),draft=value),null,"a",{}, {}, {}, {},{ value=it },{ sending=true },{ deleted=it })
        } } }
        compose.onNodeWithText("Seu comentário").assertIsDisplayed().performTextInput("Recorde absurdo!")
        compose.onNodeWithContentDescription("Publicar comentário").assertIsDisplayed().performClick(); assertTrue(sending)
        shot("circle-conversation-composer-large-font")
        compose.onNodeWithText("Quanto você descansou nessa série?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Excluir meu comentário").performClick()
        compose.onNodeWithText("Excluir").performClick(); assertEquals("c1",deleted?.id)
        compose.onNodeWithText("Séries").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Peito e tríceps").assertIsDisplayed()
        compose.onNodeWithText("80 kg × 10 rep").assertExists()
        compose.onNodeWithText("Seu comentário").assertDoesNotExist()
        shot("circle-workout-sets-large-font")
    }
    @Test fun failureNeverDiscardsDraftAndPendingReactionIsDifferentFromConfirmedCount() {
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario) {
            CircleConversationSheet(CircleConversationState(accountId="a",target=target,content=CircleConversation(post,emptyList()),draft="Texto mantido sem rede",error="Sem conexão. Tente novamente quando a rede voltar."),
                CircleReactionAction(true,CircleReaction.FIRE),"a",{}, {}, {}, {},{}, {},{})
        } } }
        compose.onNodeWithText("Texto mantido sem rede").assertIsDisplayed()
        compose.onNodeWithText("Sem conexão. Tente novamente quando a rede voltar.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Brutal, 2 reações").performScrollTo().assertIsNotEnabled()
        shot("circle-conversation-offline")
    }
}
