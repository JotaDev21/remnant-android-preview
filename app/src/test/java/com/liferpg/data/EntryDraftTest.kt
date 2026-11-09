package com.liferpg.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class EntryDraftTest {
    @Test fun draftSurvivesNewStoreInstanceWithoutPasswords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val answers = EntryAnswers(step = 4, nome = "Jota", peso = "75,5", altura = "175", idade = "24",
            foco = "BICEPS", dias = setOf(2, 4, 6))
        EntryDraft(context).save(answers)
        assertEquals(answers, EntryDraft(context).load())
        assertTrue(EntryDraft(context).load().completa())
        assertFalse(context.getSharedPreferences("entry_answers_v1", Context.MODE_PRIVATE).all.keys.any { "pass" in it || "token" in it })
        EntryDraft(context).clear()
        assertEquals("", EntryDraft(context).load().nome)
    }
    @Test fun malformedMeasurementsAndEmailNicknameCannotPass() {
        val a = EntryAnswers(nome = "Jota", peso = "80", altura = "175", idade = "24")
        assertTrue(a.completa())
        assertFalse(a.copy(peso = "NaN").completa())
        assertFalse(a.copy(altura = "0").completa())
        assertFalse(a.copy(nome = "jota@gmail.com").completa())
        assertFalse(a.copy(dias = emptySet()).completa())
        assertFalse(a.copy(dias = (1..7).toSet()).completa())
    }
}
