package com.liferpg.domain

import kotlin.math.abs

enum class PushUpPhase { SEARCHING, CALIBRATING, READY, DESCENDING, DOWN, ASCENDING }

data class PushUpPoseSample(
    val elbowAngle: Float,
    val bodyAngle: Float,
    val confidence: Float,
    val timestampMs: Long,
    val bodySpanRatio: Float = .55f,
    val bodyTiltDegrees: Float = 10f,
    val chestClearanceRatio: Float = .25f,
)

data class PushUpCounterResult(
    val repetitions: Int,
    val phase: PushUpPhase,
    val feedback: String,
    val bodyAligned: Boolean,
    val tracking: Boolean = false,
    val calibrationProgress: Float = 0f,
    val rangeProgress: Float = 0f,
    val lastRepQuality: Int? = null,
    val elbowAngle: Int? = null,
    val trackingConfidence: Int = 0,
)

/**
 * Temporal push-up recognizer fed by ML Kit landmarks.
 *
 * The user's own lockout is calibrated first. A repetition then has to cross a
 * descending, bottom and ascending state with hysteresis; isolated noisy frames
 * can no longer create or cancel a repetition.
 */
class PushUpRepEngine {
    private var repetitions = 0
    private var phase = PushUpPhase.SEARCHING
    private var calibrationStartedAt = 0L
    private var calibrationFrames = 0
    private var calibrationElbowSum = 0f
    private var calibrationClearanceSum = 0f
    private var topElbow = 160f
    private var topClearance = .25f
    private var badFrames = 0
    private var misalignedFrames = 0
    private var bottomFrames = 0
    private var lockoutFrames = 0
    private var repStartedAt = 0L
    private var lowestElbow = 180f
    private var lowestBody = 180f
    private var smoothElbow: Float? = null
    private var smoothBody: Float? = null
    private var previousElbow: Float? = null
    private var lastRepQuality: Int? = null

    fun reset(repetitionsToKeep: Int = 0) {
        repetitions = repetitionsToKeep.coerceAtLeast(0)
        phase = PushUpPhase.SEARCHING
        resetCalibration()
        smoothElbow = null
        smoothBody = null
        previousElbow = null
        lastRepQuality = null
        badFrames = 0
        misalignedFrames = 0
    }

    fun recalibrate(completedFloor: Int) = reset(maxOf(repetitions, completedFloor))

    fun process(sample: PushUpPoseSample?): PushUpCounterResult {
        if (sample == null || sample.confidence < MIN_CONFIDENCE) {
            return trackingLost("Mostre cabeça, ombros, cotovelos e as duas mãos no quadro.")
        }
        if (sample.bodySpanRatio < MIN_BODY_SPAN) {
            return trackingLost("Chegue mais perto: cabeça, braços e mãos precisam ocupar o visor.")
        }
        if (sample.bodySpanRatio > MAX_BODY_SPAN) {
            return trackingLost("Afaste um pouco o celular até caber cabeça, cotovelos e mãos.")
        }
        if (sample.bodyTiltDegrees > MAX_BODY_TILT) {
            return trackingLost("Deixe o celular reto e fique de frente para a lente.")
        }

        badFrames = 0
        val elbow = ema(smoothElbow, sample.elbowAngle).also { smoothElbow = it }
        val body = ema(smoothBody, sample.bodyAngle).also { smoothBody = it }
        val velocity = elbow - (previousElbow ?: elbow)
        previousElbow = elbow
        val aligned = body >= MIN_BODY_ANGLE

        if (!aligned) {
            misalignedFrames++
            if (misalignedFrames >= FORM_LOSS_TOLERANCE) cancelCurrentRep()
            return result(
                "Os braços estão dessincronizados. Desça com os dois cotovelos juntos.",
                aligned = false,
                tracking = true,
                elbow = elbow,
                confidence = sample.confidence,
            )
        }
        misalignedFrames = 0

        val upThreshold = (topElbow - 10f).coerceIn(138f, 165f)
        val downThreshold = (topElbow - 45f).coerceIn(95f, 115f)
        val range = ((upThreshold - elbow) / (upThreshold - downThreshold)).coerceIn(0f, 1f)
        val clearanceDown = sample.chestClearanceRatio <= topClearance * .76f
        val bottomSignal = elbow <= downThreshold || (elbow <= downThreshold + 13f && clearanceDown)
        val lockoutSignal = elbow >= upThreshold && sample.chestClearanceRatio >= topClearance * .72f

        when (phase) {
            PushUpPhase.SEARCHING -> if (elbow >= MIN_CALIBRATION_TOP) {
                startCalibration(sample.timestampMs, elbow, sample.chestClearanceRatio)
            }

            PushUpPhase.CALIBRATING -> {
                if (elbow >= MIN_CALIBRATION_TOP && abs(velocity) <= MAX_CALIBRATION_MOTION) {
                    calibrationFrames++
                    calibrationElbowSum += elbow
                    calibrationClearanceSum += sample.chestClearanceRatio
                    if (calibrationFrames >= MIN_CALIBRATION_FRAMES && sample.timestampMs - calibrationStartedAt >= CALIBRATION_MS) {
                        topElbow = calibrationElbowSum / calibrationFrames
                        topClearance = calibrationClearanceSum / calibrationFrames
                        phase = PushUpPhase.READY
                    }
                } else if (elbow < MIN_CALIBRATION_TOP - 8f) {
                    phase = PushUpPhase.SEARCHING
                    resetCalibration()
                }
            }

            PushUpPhase.READY -> {
                if (range >= DESCENT_START_RANGE && velocity < -MIN_DIRECTION_SPEED) {
                    phase = PushUpPhase.DESCENDING
                    repStartedAt = sample.timestampMs
                    lowestElbow = elbow
                    lowestBody = body
                    bottomFrames = 0
                }
            }

            PushUpPhase.DESCENDING -> {
                recordBottom(elbow, body)
                if (bottomSignal) {
                    bottomFrames++
                    if (bottomFrames >= STABLE_BOTTOM_FRAMES) phase = PushUpPhase.DOWN
                } else {
                    bottomFrames = 0
                    if (lockoutSignal && velocity > 0f) cancelCurrentRep(keepCalibration = true)
                }
            }

            PushUpPhase.DOWN -> {
                recordBottom(elbow, body)
                if (velocity > MIN_DIRECTION_SPEED || elbow > downThreshold + 8f) {
                    phase = PushUpPhase.ASCENDING
                    lockoutFrames = 0
                }
            }

            PushUpPhase.ASCENDING -> {
                recordBottom(elbow, body)
                if (bottomSignal && velocity < -MIN_DIRECTION_SPEED) {
                    phase = PushUpPhase.DOWN
                    lockoutFrames = 0
                } else if (lockoutSignal) {
                    lockoutFrames++
                    if (lockoutFrames >= STABLE_LOCKOUT_FRAMES) {
                        val duration = sample.timestampMs - repStartedAt
                        if (duration in MIN_REP_MS..MAX_REP_MS) {
                            repetitions++
                            lastRepQuality = quality(lowestElbow, lowestBody, duration, downThreshold)
                        }
                        phase = PushUpPhase.READY
                        repStartedAt = 0L
                        lockoutFrames = 0
                    }
                } else lockoutFrames = 0
            }
        }

        val feedback = when (phase) {
            PushUpPhase.SEARCHING -> "Estenda os braços e pare por um instante para calibrar."
            PushUpPhase.CALIBRATING -> "Segure a prancha alta. Estou aprendendo sua extensão."
            PushUpPhase.READY -> "Calibrado. Desça o peito dobrando os dois braços."
            PushUpPhase.DESCENDING -> "Boa descida. Continue até passar o limite verde."
            PushUpPhase.DOWN -> "Fundo confirmado. Empurre com os dois braços."
            PushUpPhase.ASCENDING -> "Suba até sua extensão calibrada."
        }
        return result(feedback, true, true, range, elbow, sample.confidence)
    }

    private fun startCalibration(timestamp: Long, elbow: Float, clearance: Float) {
        phase = PushUpPhase.CALIBRATING
        calibrationStartedAt = timestamp
        calibrationFrames = 1
        calibrationElbowSum = elbow
        calibrationClearanceSum = clearance
    }

    private fun recordBottom(elbow: Float, body: Float) {
        lowestElbow = minOf(lowestElbow, elbow)
        lowestBody = minOf(lowestBody, body)
    }

    private fun trackingLost(message: String): PushUpCounterResult {
        badFrames++
        if (badFrames >= TRACKING_LOSS_TOLERANCE) cancelCurrentRep()
        return result(message, false, false, elbow = smoothElbow)
    }

    private fun cancelCurrentRep(keepCalibration: Boolean = false) {
        phase = if (keepCalibration) PushUpPhase.READY else PushUpPhase.SEARCHING
        repStartedAt = 0L
        bottomFrames = 0
        lockoutFrames = 0
        lowestElbow = 180f
        lowestBody = 180f
        if (!keepCalibration) {
            resetCalibration()
            smoothElbow = null
            smoothBody = null
            previousElbow = null
        }
    }

    private fun resetCalibration() {
        calibrationStartedAt = 0L
        calibrationFrames = 0
        calibrationElbowSum = 0f
        calibrationClearanceSum = 0f
    }

    private fun result(
        feedback: String,
        aligned: Boolean,
        tracking: Boolean,
        range: Float = 0f,
        elbow: Float? = null,
        confidence: Float = 0f,
    ) = PushUpCounterResult(
        repetitions = repetitions,
        phase = phase,
        feedback = feedback,
        bodyAligned = aligned,
        tracking = tracking,
        calibrationProgress = if (phase == PushUpPhase.CALIBRATING) {
            (calibrationFrames.toFloat() / MIN_CALIBRATION_FRAMES).coerceIn(0f, 1f)
        } else if (phase != PushUpPhase.SEARCHING) 1f else 0f,
        rangeProgress = range,
        lastRepQuality = lastRepQuality,
        elbowAngle = elbow?.toInt(),
        trackingConfidence = (confidence * 100).toInt().coerceIn(0, 100),
    )

    private fun ema(previous: Float?, current: Float): Float = previous?.let {
        it + SMOOTHING * (current - it)
    } ?: current

    private fun quality(depth: Float, alignment: Float, durationMs: Long, downThreshold: Float): Int {
        val depthScore = ((downThreshold - depth + 20f) / 20f).coerceIn(0f, 1f)
        val alignmentScore = ((alignment - MIN_BODY_ANGLE) / 30f).coerceIn(0f, 1f)
        val tempoScore = (1f - abs(durationMs - IDEAL_REP_MS) / IDEAL_REP_MS.toFloat()).coerceIn(.35f, 1f)
        return (55f * depthScore + 30f * alignmentScore + 15f * tempoScore).toInt().coerceIn(1, 100)
    }

    private companion object {
        const val MIN_CONFIDENCE = .48f
        const val MIN_BODY_SPAN = .14f
        const val MAX_BODY_SPAN = .96f
        const val MAX_BODY_TILT = 28f
        const val MIN_BODY_ANGLE = 148f
        const val MIN_CALIBRATION_TOP = 135f
        const val MAX_CALIBRATION_MOTION = 7f
        const val CALIBRATION_MS = 350L
        const val MIN_CALIBRATION_FRAMES = 7
        const val DESCENT_START_RANGE = .14f
        const val MIN_DIRECTION_SPEED = .65f
        const val STABLE_BOTTOM_FRAMES = 2
        const val STABLE_LOCKOUT_FRAMES = 2
        const val MIN_REP_MS = 550L
        const val MAX_REP_MS = 10_000L
        const val IDEAL_REP_MS = 2_000L
        const val TRACKING_LOSS_TOLERANCE = 7
        const val FORM_LOSS_TOLERANCE = 4
        const val SMOOTHING = .48f
    }
}

val Exercicio.suportaContadorDeFlexao: Boolean
    get() = modo == ModoMedida.REPS && musculo == Musculo.PEITO && nome.contains("Flexão", ignoreCase = true)
