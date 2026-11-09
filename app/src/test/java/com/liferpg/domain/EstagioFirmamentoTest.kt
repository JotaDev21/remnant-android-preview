package com.liferpg.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EstagioFirmamentoTest {
    @Test fun eachAscensionStartsAtItsOwnLevel() {
        EstagioFirmamento.entries.forEachIndexed { index, stage ->
            assertEquals(stage, EstagioFirmamento.deNivel(stage.nivelMinimo))
            if (index > 0) assertEquals(EstagioFirmamento.entries[index - 1], EstagioFirmamento.deNivel(stage.nivelMinimo - 1))
        }
    }

    @Test fun playerAscensionDoesNotDependOnPowerScore() {
        assertEquals(EstagioFirmamento.BRASA, EstagioFirmamento.deNivel(1))
        assertEquals(EstagioFirmamento.FIRMAMENTO, EstagioFirmamento.deNivel(100))
    }
}
