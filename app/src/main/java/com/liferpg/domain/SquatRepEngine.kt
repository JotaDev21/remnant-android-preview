package com.liferpg.domain

import kotlin.math.abs

data class SquatPoseSample(
    val kneeAngle: Float,
    val torsoTilt: Float,
    val confidence: Float,
    val bodySpanRatio: Float,
    val timestampMs: Long,
)

/** Side-view squat recognizer: stable standing lockout, real depth, then full extension. */
class SquatRepEngine {
    private var reps = 0
    private var phase = PushUpPhase.SEARCHING
    private var standingAngle = 165f
    private var smoothed: Float? = null
    private var previous: Float? = null
    private var calibrationFrames = 0
    private var calibrationStartedAt = 0L
    private var calibrationSum = 0f
    private var bottomFrames = 0
    private var topFrames = 0
    private var badFrames = 0
    private var startedAt = 0L
    private var deepest = 180f
    private var lastQuality: Int? = null

    fun reset(repetitionsToKeep: Int = 0) {
        reps = repetitionsToKeep.coerceAtLeast(0); phase = PushUpPhase.SEARCHING; smoothed = null; previous = null
        calibrationFrames = 0; calibrationStartedAt = 0L; calibrationSum = 0f
        bottomFrames = 0; topFrames = 0; badFrames = 0; startedAt = 0L
        deepest = 180f; lastQuality = null
    }

    fun recalibrate(completedFloor: Int) = reset(maxOf(reps, completedFloor))

    fun process(sample: SquatPoseSample?): PushUpCounterResult {
        if (sample == null || sample.confidence < .52f) return lost("Mostre ombro, quadril, joelho e tornozelo de lado.")
        if (sample.bodySpanRatio < .22f) return lost("Aproxime o celular para as pernas aparecerem melhor.")
        if (sample.bodySpanRatio > .97f) return lost("Afaste o celular até caber ombro e tornozelo.")
        if (sample.torsoTilt > 68f) return lost("Mantenha o tronco visível e evite inclinar demais.")
        badFrames = 0
        val angle = (smoothed?.let { it + .45f * (sample.kneeAngle - it) } ?: sample.kneeAngle)
        val speed = angle - (previous ?: angle)
        smoothed = angle; previous = angle
        val top = (standingAngle - 11f).coerceIn(143f, 165f)
        val bottom = (standingAngle - 53f).coerceIn(95f, 111f)
        val range = ((top - angle) / (top - bottom)).coerceIn(0f, 1f)
        when (phase) {
            PushUpPhase.SEARCHING -> if (angle >= 148f) {
                phase = PushUpPhase.CALIBRATING
                calibrationFrames = 1; calibrationStartedAt = sample.timestampMs; calibrationSum = angle
            }
            PushUpPhase.CALIBRATING -> {
                if (angle >= 148f && abs(speed) <= 6f) {
                    calibrationFrames++; calibrationSum += angle
                    if (calibrationFrames >= 7 && sample.timestampMs - calibrationStartedAt >= 350L) {
                        standingAngle = calibrationSum / calibrationFrames
                        phase = PushUpPhase.READY
                    }
                } else if (angle < 140f) {
                    phase = PushUpPhase.SEARCHING; calibrationFrames = 0
                }
            }
            PushUpPhase.READY -> if (range >= .16f && speed < -.7f) {
                phase = PushUpPhase.DESCENDING; startedAt = sample.timestampMs
                deepest = angle; bottomFrames = 0
            }
            PushUpPhase.DESCENDING -> {
                deepest = minOf(deepest, angle)
                if (angle <= bottom) {
                    bottomFrames++
                    if (bottomFrames >= 2) phase = PushUpPhase.DOWN
                } else if (angle >= top && speed > 0f) phase = PushUpPhase.READY
                else bottomFrames = 0
            }
            PushUpPhase.DOWN -> {
                deepest = minOf(deepest, angle)
                if (speed > .7f || angle >= bottom + 8f) phase = PushUpPhase.ASCENDING
            }
            PushUpPhase.ASCENDING -> {
                if (angle <= bottom && speed < -1f) {
                    phase = PushUpPhase.DOWN; topFrames = 0
                }
                if (angle >= top) {
                    topFrames++
                    if (topFrames >= 2) {
                        val duration = sample.timestampMs - startedAt
                        if (duration in 650L..12_000L) {
                            reps++
                            lastQuality = ((111f - deepest) * 1.4f + (1f - abs(duration - 2500L) / 4000f) * 40f + 45f)
                                .toInt().coerceIn(1, 100)
                        }
                        phase = PushUpPhase.READY; topFrames = 0
                    }
                } else topFrames = 0
                if (sample.timestampMs - startedAt > 12_000L) phase = PushUpPhase.READY
            }
        }
        val feedback = when (phase) {
            PushUpPhase.SEARCHING -> "Fique de lado, com ombro e tornozelo no quadro."
            PushUpPhase.CALIBRATING -> "Fique em pé e imóvel para calibrar."
            PushUpPhase.READY -> "Pronto. Desça com controle até a coxa chegar perto da horizontal."
            PushUpPhase.DESCENDING -> "Desça mais: a meia repetição não causa dano."
            PushUpPhase.DOWN -> "Profundidade alcançada. Suba até estender as pernas."
            PushUpPhase.ASCENDING -> "Estenda as pernas para validar a repetição."
        }
        return result(feedback, true, range, angle, sample.confidence)
    }

    private fun lost(message: String): PushUpCounterResult {
        badFrames++
        if (badFrames >= 7) {
            phase = PushUpPhase.SEARCHING; calibrationFrames = 0; smoothed = null; previous = null
        }
        return result(message, false, 0f, smoothed, 0f)
    }

    private fun result(message: String, tracked: Boolean, range: Float, angle: Float?, confidence: Float) = PushUpCounterResult(
        repetitions = reps, phase = phase, feedback = message, bodyAligned = tracked, tracking = tracked,
        calibrationProgress = if (phase == PushUpPhase.CALIBRATING) (calibrationFrames / 7f).coerceIn(0f, 1f) else if (phase == PushUpPhase.SEARCHING) 0f else 1f,
        rangeProgress = range, lastRepQuality = lastQuality, elbowAngle = angle?.toInt(),
        trackingConfidence = (confidence * 100).toInt().coerceIn(0, 100),
    )
}
