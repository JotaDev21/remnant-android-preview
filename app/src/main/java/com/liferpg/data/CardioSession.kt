package com.liferpg.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.liferpg.domain.CardioEngine
import org.json.JSONObject

@Entity(tableName = "cardio_session", indices = [Index("epochDay"), Index("status")])
data class CardioSession(
    @PrimaryKey val id: String,
    val activity: String,
    val goal: String,
    val target: Double,
    val startedAt: Long,
    val accumulatedMs: Long,
    val anchorMs: Long,
    val boot: Int,
    val status: String,
    val distanceKm: Double?,
    val effort: String,
    val note: String,
    val source: String,
    val endedAt: Long,
    val epochDay: Long,
    val xpGained: Int,
    val device: String = "",
    val steps: Long? = null,
)

object CardioCodec {
    fun encode(row: CardioSession): JSONObject = JSONObject().apply {
        put("id", row.id); put("activity", row.activity); put("goal", row.goal); put("target", row.target)
        put("startedAt", row.startedAt); put("durationMs", row.accumulatedMs)
        put("distanceKm", row.distanceKm ?: JSONObject.NULL); put("effort", row.effort); put("note", row.note)
        put("source", row.source); put("endedAt", row.endedAt); put("epochDay", row.epochDay); put("xp", row.xpGained)
        put("steps", row.steps ?: JSONObject.NULL)
    }
    /** Portable saves contain completed activities, never a running timer from another phone. */
    fun decode(o: JSONObject): CardioSession = CardioSession(o.getString("id"), o.getString("activity"), o.getString("goal"), o.getDouble("target"),
        o.getLong("startedAt"), o.getLong("durationMs"), 0L, 0, "FINISHED", if (o.isNull("distanceKm")) null else o.getDouble("distanceKm"),
        o.getString("effort"), o.getString("note"), o.getString("source"), o.getLong("endedAt"), o.getLong("epochDay"), o.getInt("xp"))
        .copy(steps = if (o.has("steps") && !o.isNull("steps")) o.getLong("steps") else null).also(CardioEngine::validate)
}
