package com.liferpg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushUpRepEngineTest {
    @Test fun recalibrationPreservesCompletedRepsButDiscardsPartialMovement() {
        val engine = PushUpRepEngine()
        engine.reset(39)
        assertEquals(39, engine.process(null).repetitions)
        engine.calibrate()
        repeat(5) { engine.process(PushUpPoseSample(70f, 172f, .9f, 900L + it * 100)) }
        engine.reset(39)
        repeat(5) { engine.process(PushUpPoseSample(175f, 172f, .9f, 1700L + it * 100)) }
        assertEquals(39, engine.process(PushUpPoseSample(175f, 172f, .9f, 2300)).repetitions)
        engine.recalibrate(38) // A delayed pause snapshot must not roll back a completed rep.
        assertEquals(39, engine.process(null).repetitions)
    }
    private fun PushUpRepEngine.calibrate(start: Long = 0L) {
        repeat(9) { process(PushUpPoseSample(170f, 175f, .9f, start + it * 50L)) }
    }

    @Test fun countsOnlyFullDownAndUpCycle() {
        val engine = PushUpRepEngine().also { it.calibrate() }
        repeat(5) { engine.process(PushUpPoseSample(70f, 172f, .9f, 900L + it * 100)) }
        repeat(5) { engine.process(PushUpPoseSample(175f, 170f, .9f, 1_700L + it * 100)) }
        val result = engine.process(PushUpPoseSample(175f, 170f, .9f, 2_300))

        assertEquals(1, result.repetitions)
        assertEquals(PushUpPhase.READY, result.phase)
        assertNotNull(result.lastRepQuality)
    }

    @Test fun partialRangeDoesNotCount() {
        val engine = PushUpRepEngine().also { it.calibrate() }
        engine.process(PushUpPoseSample(125f, 175f, .9f, 900))
        val result = engine.process(PushUpPoseSample(170f, 175f, .9f, 1_500))

        assertEquals(0, result.repetitions)
    }

    @Test fun bentBodyCancelsCalibrationAndRep() {
        val engine = PushUpRepEngine().also { it.calibrate() }
        repeat(4) { engine.process(PushUpPoseSample(80f, 120f, .9f, 900L + it * 100)) }
        val result = engine.process(PushUpPoseSample(170f, 120f, .9f, 1_500))

        assertEquals(0, result.repetitions)
        assertEquals(PushUpPhase.SEARCHING, result.phase)
        assertFalse(result.bodyAligned)
    }

    @Test fun lowConfidencePausesAfterShortTolerance() {
        val engine = PushUpRepEngine().also { it.calibrate() }
        repeat(7) { engine.process(PushUpPoseSample(170f, 175f, .2f, 900L + it * 50)) }
        val result = engine.process(PushUpPoseSample(170f, 175f, .2f, 1_300))

        assertFalse(result.tracking)
        assertEquals(PushUpPhase.SEARCHING, result.phase)
        assertTrue(result.feedback.contains("ombros"))
    }

    @Test fun userMustHoldTheStartPositionBeforeCounting() {
        val engine = PushUpRepEngine()
        engine.process(PushUpPoseSample(170f, 175f, .9f, 0))
        engine.process(PushUpPoseSample(75f, 175f, .9f, 500))
        val result = engine.process(PushUpPoseSample(170f, 175f, .9f, 1_100))

        assertEquals(0, result.repetitions)
        assertTrue(result.phase == PushUpPhase.CALIBRATING || result.phase == PushUpPhase.SEARCHING)
    }

    @Test fun bodyTooSmallRequestsCloserFraming() {
        val result = PushUpRepEngine().process(PushUpPoseSample(170f, 175f, .9f, 0, bodySpanRatio = .12f))

        assertFalse(result.tracking)
        assertTrue(result.feedback.contains("perto"))
    }

    @Test fun oneNoisyBottomFrameCannotCreateARep() {
        val engine = PushUpRepEngine().also { it.calibrate() }
        engine.process(PushUpPoseSample(82f, 173f, .9f, 900))
        repeat(4) { engine.process(PushUpPoseSample(170f, 173f, .9f, 1_100L + it * 100)) }

        assertEquals(0, engine.process(PushUpPoseSample(170f, 173f, .9f, 1_600)).repetitions)
    }

    @Test fun briefLandmarkLossDoesNotDestroyCalibration() {
        val engine = PushUpRepEngine().also { it.calibrate() }
        repeat(3) { engine.process(null) }
        repeat(4) { engine.process(PushUpPoseSample(85f, 172f, .9f, 900L + it * 100)) }
        repeat(4) { engine.process(PushUpPoseSample(170f, 172f, .9f, 1_500L + it * 100)) }

        assertEquals(1, engine.process(PushUpPoseSample(170f, 172f, .9f, 2_000)).repetitions)
    }

    @Test fun calibrationAdaptsToUserWithoutFullElbowLockout() {
        val engine = PushUpRepEngine()
        repeat(9) { engine.process(PushUpPoseSample(145f, 173f, .9f, it * 50L)) }
        repeat(4) { engine.process(PushUpPoseSample(88f, 171f, .9f, 900L + it * 100)) }
        repeat(4) { engine.process(PushUpPoseSample(145f, 171f, .9f, 1_500L + it * 100)) }

        assertEquals(1, engine.process(PushUpPoseSample(145f, 171f, .9f, 2_000)).repetitions)
    }

    @Test fun standingMovementIsRejectedAsWrongCameraAngle() {
        val result = PushUpRepEngine().process(
            PushUpPoseSample(170f, 175f, .9f, 0, bodyTiltDegrees = 82f)
        )

        assertFalse(result.tracking)
        assertTrue(result.feedback.contains("frente"))
    }
}
