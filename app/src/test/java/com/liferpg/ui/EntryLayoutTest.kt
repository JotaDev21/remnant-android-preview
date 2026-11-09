package com.liferpg.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.liferpg.data.CatalogoRepo
import com.liferpg.data.EntryAnswers
import com.liferpg.ui.theme.Fundo
import com.liferpg.ui.theme.LifeRpgTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Exercises the same composables shipped in production, without an account or writes to a save. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w384dp-h832dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun shot(name: String) {
        compose.waitForIdle()
        // PixelCopy times out on this Windows host; native Canvas renders the actual Android view.
        val image = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val output = File("../../dist/qa-alpha30/entry-local-$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(image.width > 0 && image.height > 0)
    }
    private fun input(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(value)
    }
    @Test fun sixStepsRemainNavigableAndSignupRequiresMatchingPassword() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = runBlocking { CatalogoRepo(context).exercicios() }
        val answers = mutableStateOf(EntryAnswers())
        var authSubmits = 0
        compose.setContent { LifeRpgTheme {
            Box(Modifier.fillMaxSize().background(Fundo)) {
                EntryJourneyContent(answers.value, catalog, null, false, false, false, null,
                    { answers.value = it }, {}, {}) {
                    AccountForm(null, false, null, false, false, false, true,
                        { _, _ -> }, { _, _ -> authSubmits++ }, {}, {}, {}, {}, {})
                }
            }
        } }
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        input("Nickname", "Caçador QA")
        shot("1-nome")
        compose.onNodeWithText("Continuar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        input("Peso · kg", "80")
        input("Altura · cm", "175")
        input("Idade · anos", "24")
        shot("2-medidas")
        compose.onNodeWithText("Continuar").assertIsDisplayed().performClick()
        shot("3-objetivo")
        compose.onNodeWithText("Continuar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Bíceps").performScrollTo().performClick()
        shot("4-agenda")
        compose.onNodeWithText("Continuar").assertIsDisplayed().performClick()
        shot("5-plano")
        compose.onNodeWithText("Proteger minha jornada").assertIsDisplayed().performClick()
        compose.onNodeWithText("Criar minha conta").performScrollTo().assertIsNotEnabled()
        input("E-mail", "qa@example.com")
        input("Senha", "password8")
        input("Confirmar senha", "different8")
        compose.onNodeWithText("Criar minha conta").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Confirmar senha").performTextClearance()
        compose.onNodeWithText("Confirmar senha").performTextInput("password8")
        compose.onNodeWithText("Criar minha conta").performScrollTo().assertIsEnabled()
        shot("6-conta")
        compose.onNodeWithText("Criar minha conta").performClick()
        assertEquals(1, authSubmits) // Fake UI action only: no remote signup, series, profile or XP writes.
        assertEquals("Caçador QA", answers.value.nome)
        assertEquals("BICEPS", answers.value.foco)
    }
    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun smallScreenAndLargerTextKeepTheFinalActionsReachable() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = runBlocking { CatalogoRepo(context).exercicios() }
        val answers = mutableStateOf(EntryAnswers(step = 4, nome = "Caçador QA", peso = "80", altura = "175", idade = "24"))
        compose.setContent { LifeRpgTheme {
            Box(Modifier.fillMaxSize().background(Fundo)) {
                EntryJourneyContent(answers.value, catalog, null, false, false, false, null,
                    { answers.value = it }, {}, {}) {
                    AccountForm(null, false, null, false, false, false, true,
                        { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
                }
            }
        } }
        compose.onNodeWithText("Proteger minha jornada").assertIsDisplayed().assertIsEnabled()
        shot("small-plan")
        compose.onNodeWithText("Proteger minha jornada").performClick()
        input("E-mail", "qa@example.com")
        input("Senha", "password8")
        input("Confirmar senha", "password8")
        compose.onNodeWithText("Criar minha conta").performScrollTo().assertIsDisplayed().assertIsEnabled()
        shot("small-account")
    }
}
