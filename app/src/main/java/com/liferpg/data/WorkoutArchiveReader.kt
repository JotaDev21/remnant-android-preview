package com.liferpg.data

import com.liferpg.domain.*
import com.liferpg.data.sync.CircleCodec
import org.json.JSONObject

object WorkoutArchiveReader {
    fun workout(session: TreinoSessao): ArchivedWorkout {
        val exercises=runCatching {
            CircleCodec.validateReceipt(session.receiptJson)
            val entries=JSONObject(session.receiptJson).getJSONArray("exercises")
            (0 until entries.length()).map { i ->
                val e=entries.getJSONObject(i); val sets=e.getJSONArray("sets")
                ArchivedExercise(e.getString("id"),e.getString("name"),
                    Musculo.entries.firstOrNull { it.name==e.getString("muscle") }?.nomeExibicao ?: e.getString("muscle"),
                    (0 until sets.length()).map { j -> sets.getJSONObject(j).let { s ->
                        ArchivedSet(if(s.has("kg")) s.getDouble("kg").toFloat() else null,
                            if(s.has("reps")) s.getInt("reps") else null,if(s.has("seconds")) s.getInt("seconds") else null,s.getBoolean("pr"))
                    } })
            }
        }.getOrNull()
        val grouped=exercises.orEmpty().groupBy { it.id }.map { (_,rows) -> rows.first().copy(sets=rows.flatMap { it.sets }) }
        return ArchivedWorkout(session,grouped,exercises!=null)
    }
}
