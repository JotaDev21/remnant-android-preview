package com.liferpg.ui
import org.junit.Assert.*
import org.junit.Test
class MentorTextTest {
    @Test fun formatsHeadingsListsAndEmphasisWithoutPrintingMarkers() {
        val lines=mentorTextLines("## Próxima série\n- **Supino**: 3 séries de *oito* repetições.")
        assertTrue(lines[0].heading)
        assertEquals("Próxima série",lines[0].text)
        assertEquals("• Supino: 3 séries de oito repetições.",mentorAnnotated(lines[1].text).text)
        assertTrue(mentorAnnotated(lines[1].text).spanStyles.isNotEmpty())
    }
    @Test fun preservesPortugueseNumbersAndLiteralMultiplication() {
        assertEquals("Faça 2*5 repetições e use 12,5 kg.",mentorPlainText("Faça 2*5 repetições e use 12,5 kg."))
        assertEquals("Supino",mentorPlainText("[Supino](https://example.com/exercicio)"))
        assertEquals("REM",mentorPlainText("ＲＥＭ"))
    }
}
