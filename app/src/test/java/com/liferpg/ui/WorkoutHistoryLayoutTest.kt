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
import com.liferpg.data.*
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
class WorkoutHistoryLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val day=LocalDate.of(2026,10,9).toEpochDay()
    private val exercises=listOf(ArchivedExercise("bench","Supino inclinado na máquina","Peito",listOf(ArchivedSet(80f,10,null,true),ArchivedSet(75f,12,null))),
        ArchivedExercise("fly","Crucifixo no cabo","Peito",listOf(ArchivedSet(15f,12,null))))
    private val session=TreinoSessao(1,day,"Peito e tríceps",95,3,2,1,2700,2000)
    private val archive=WorkoutArchive(listOf(ArchivedWorkout(session,exercises,true),ArchivedWorkout(session.copy(id=2,epochDay=day-3,nome="Costas e bíceps",recordes=0,encerradaEm=1000),emptyList(),false)),
        listOf(ArchiveDay(day,exercises),ArchiveDay(day-3,listOf(exercises.first()))))
    private fun content(data: WorkoutArchive?=archive,font: Float=1.3f,open: (String)->Unit={},repeat: (TreinoSessao)->Unit={},selected: Long?=null,active: String?=null) {
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,font)) {
            MotionScene(Modifier.fillMaxSize().background(Fundo)) { WorkoutHistoryContent(data,day,{},open,repeat,initialSession=selected,activeSession=active) }
        } } }
    }
    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(800);compose.waitForIdle()
        val bitmap=compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file=File("../../dist/qa-alpha40/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun archiveGroupsSessionsAndSearchShowsHonestEmptyState() {
        content(font=1f);shot("history-sessions")
        compose.onNodeWithText("Peito e tríceps").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("não existe")
        compose.onNodeWithText("Nada neste filtro.").performScrollTo().assertIsDisplayed();shot("history-search-empty")
        compose.onNodeWithText("Limpar filtros").performScrollTo().performClick()
        compose.onNodeWithText("Peito e tríceps").assertExists()
    }
    @Test fun longSessionAndManyExercisesKeepRepeatActionInsideViewport() {
        var repeated: TreinoSessao?=null
        val long=session.copy(nome="Treino completo de peito e tríceps com variações na máquina e no cabo")
        val many=exercises+(1..18).map { n -> exercises.first().copy(id="extra$n",name="Exercício complementar $n") }
        content(WorkoutArchive(listOf(ArchivedWorkout(long,many,true)),emptyList()),selected=1,repeat={repeated=it})
        compose.onNodeWithText("Repetir treino").assertIsDisplayed()
        val bounds=compose.onNodeWithText("Repetir treino").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top>=0f && bounds.bottom<=800f);shot("history-session-large-font")
        compose.onAllNodesWithText("Supino inclinado na máquina").onLast().performClick()
        compose.onNodeWithText("80 kg × 10 reps").assertExists();compose.onNodeWithText("PR",useUnmergedTree=true).assertExists()
        shot("history-sets-expanded-large-font")
        compose.onNodeWithText("Repetir treino").performClick();assertEquals(long,repeated)
    }
    @Test fun activeSessionOffersResumeAndDoesNotReplaceIt() {
        var repeated=false;var route=""
        content(selected=1,active="Perna",open={route=it},repeat={repeated=true})
        compose.onNodeWithText("Repetir treino").assertDoesNotExist()
        compose.onNodeWithText("Retomar sessão atual").assertIsDisplayed().performClick()
        assertEquals(RemnantRoute.SESSION,route);assertFalse(repeated)
    }
    @Test fun legacySessionExplicitlyKeepsDaySeriesSeparate() {
        content(selected=2)
        compose.onNodeWithText("Este treino antigo tem apenas o resumo.",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Supino inclinado na máquina").assertDoesNotExist();shot("history-legacy-large-font")
    }
    @Test fun dayLogExpandsEquipmentAndInvalidSetAndOpensRealExercise() {
        var route=""
        val e=exercises.first().copy(sets=listOf(ArchivedSet(80f,10,null,valid=false,equipment="Máquina da academia")))
        content(archive.copy(days=listOf(ArchiveDay(day,listOf(e)))),open={route=it})
        compose.onNodeWithText("Séries").performClick();shot("history-days-large-font")
        compose.onNodeWithText("Supino inclinado na máquina").performScrollTo().performClick()
        compose.onAllNodesWithText("Supino inclinado na máquina").onLast().performClick()
        compose.onNodeWithText("Fora do ranking").assertExists();shot("history-day-detail-large-font")
        compose.onNodeWithText("Ver exercício").performScrollTo().performClick();assertEquals(RemnantRoute.exercise("bench"),route)
    }
    @Test fun newUserHasUsefulActionWithoutFakeHistory() {
        var route="";content(WorkoutArchive(emptyList(),emptyList()),open={route=it})
        compose.onNodeWithText("Seu próximo treino começa este arquivo.").performScrollTo().assertIsDisplayed();shot("history-empty-large-font")
        compose.onNodeWithText("Abrir meu treino").performScrollTo().performClick();assertEquals(RemnantRoute.PLAN,route)
    }
    @Test fun loadingDoesNotPretendSaveIsEmpty() {
        content(null)
        compose.onNodeWithContentDescription("Carregando histórico").assertExists()
        compose.onNodeWithText("Seu próximo treino começa este arquivo.").assertDoesNotExist()
    }
    @Test fun todayLogGroupsRepeatedSetsAndExercisePreviewLinksToFullArchive() {
        val ex=Exercicio("bench","Supino",Musculo.PEITO,emptyList(),"Barra",LocalTreino.ACADEMIA,ModoMedida.CARGA,1f,"",emptyList(),"",null,emptyList(),"")
        val rows=(1L..4L).map { SerieRegistro(it,"bench","PEITO",80f,10,null,1f,20,day,it) }
        var route=""
        compose.setContent { LifeRpgTheme { CompositionLocalProvider(LocalContentColor provides TextoPrimario,LocalDensity provides Density(1f,1.3f)) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Fundo)) {
                TodayTrainingLog(rows,listOf(ex)) { route=it }
                ExerciseHistoryLog(rows,ex) { route=it }
            }
        } } }
        compose.onAllNodesWithText("Supino").assertCountEquals(2)
        compose.onAllNodesWithText("4 séries · Peito").assertCountEquals(2);shot("history-compact-logs-large-font")
        compose.onNodeWithText("Histórico completo").performClick();assertEquals(RemnantRoute.exerciseHistory("bench"),route)
    }
}
