package com.liferpg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SquatRepEngineTest {
    @Test fun recalibrationKeepsCompletedRepsAndRejectsOldPartialSquat() {
        val engine = SquatRepEngine()
        engine.reset(20)
        engine.calibrate()
        repeat(6) { engine.process(sample(82f, 900L + it * 120L)) }
        engine.reset(20)
        repeat(6) { engine.process(sample(170f, 1900L + it * 120L)) }
        assertEquals(20, engine.process(sample(170f, 2700)).repetitions)
        engine.recalibrate(19)
        assertEquals(20, engine.process(null).repetitions)
    }
    private fun sample(angle: Float, time: Long, confidence: Float = .9f) =
        SquatPoseSample(angle, 12f, confidence, .55f, time)

    private fun SquatRepEngine.calibrate() {
        repeat(10) { process(sample(170f, it * 60L)) }
    }

    @Test fun countsControlledFullSquatOnce() {
        val engine = SquatRepEngine().also { it.calibrate() }
        repeat(6) { engine.process(sample(82f, 900L + it * 120L)) }
        repeat(6) { engine.process(sample(170f, 1900L + it * 120L)) }
        assertEquals(1, engine.process(sample(170f, 2700L)).repetitions)
    }

    @Test fun partialSquatDoesNotDamageBoss() {
        val engine = SquatRepEngine().also { it.calibrate() }
        repeat(4) { engine.process(sample(126f, 900L + it * 100L)) }
        repeat(5) { engine.process(sample(170f, 1500L + it * 100L)) }
        assertEquals(0, engine.process(sample(170f, 2200L)).repetitions)
    }

    @Test fun losingLegTrackingDoesNotInventRep() {
        val engine = SquatRepEngine().also { it.calibrate() }
        repeat(7) { engine.process(sample(80f, 900L + it * 100L)) }
        repeat(8) { engine.process(null) }
        val result = engine.process(sample(170f, 2200L))
        assertFalse(result.phase == PushUpPhase.ASCENDING)
        assertEquals(0, result.repetitions)
    }
}
