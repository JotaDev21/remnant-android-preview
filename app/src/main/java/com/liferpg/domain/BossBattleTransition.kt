package com.liferpg.domain

/** A presentation event, never a second source of campaign damage or rewards. */
data class BossDefeat(val defeatedStage: Int, val count: Int, val totalWins: Int, val nextStage: Int)
