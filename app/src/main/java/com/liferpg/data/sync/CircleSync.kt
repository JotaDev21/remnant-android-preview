package com.liferpg.data.sync

import com.liferpg.domain.EstadoSessao
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class CircleMember(val uid: String, val nickname: String, val status: String, val goal: Int, val days: Int) {
    val progress get() = (days.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
}
data class TrainingCircle(val id: String, val owner: String, val name: String, val timezone: String,
    val status: String, val weekStart: String, val members: List<CircleMember>) {
    val board get() = members.filter { it.status == "accepted" }.sortedWith(
        compareByDescending<CircleMember> { it.progress }.thenBy { it.nickname.lowercase() }.thenBy { it.uid })
    fun position(member: CircleMember) = board.count { it.progress > member.progress } + 1
}
data class CirclePost(val id: String, val sessionId: String, val uid: String, val nickname: String,
    val createdAt: String, val receipt: String, val social: CircleSocial = CircleSocial())
class CircleRequestFailure(message: String, val retryable: Boolean,val accessDenied:Boolean=false) : IllegalStateException(message)

object CircleCodec {
    fun validateReceipt(body: String) {
        require(body.toByteArray().size<=65536)
        val r=JSONObject(body)
        require(r.keys().asSequence().toSet()==setOf("title","ended_at","duration_seconds","exercises"))
        require(r.getString("title").length in 1..120)
        java.time.OffsetDateTime.parse(r.getString("ended_at"))
        require(r.getLong("duration_seconds") in 0..604800)
        val exercises=r.getJSONArray("exercises"); require(exercises.length() in 1..60)
        var count=0
        for(i in 0 until exercises.length()) {
            val e=exercises.getJSONObject(i)
            require(e.keys().asSequence().toSet()==setOf("id","name","muscle","sets"))
            require(e.getString("id").length in 1..160 && e.getString("name").length in 1..120 && e.getString("muscle").length in 1..40)
            val sets=e.getJSONArray("sets"); require(sets.length() in 1..100); count+=sets.length()
            for(j in 0 until sets.length()) {
                val s=sets.getJSONObject(j)
                require(s.keys().asSequence().all { it in setOf("kg","reps","seconds","pr") })
                require(s.get("pr") is Boolean && (s.has("reps") || s.has("seconds")))
                if(s.has("kg")) require(s.getDouble("kg").let { it.isFinite() && it in 0.0..2000.0 })
                if(s.has("reps")) require(s.getInt("reps") in 1..100000)
                if(s.has("seconds")) require(s.getInt("seconds") in 1..86400)
            }
        }
        require(count<=600)
    }
    fun cache(rows: List<TrainingCircle>): String = JSONArray(rows.map { c ->
        JSONObject().put("id",c.id).put("owner",c.owner).put("name",c.name).put("timezone",c.timezone)
            .put("status",c.status).put("week_start",c.weekStart).put("members",JSONArray(c.members.map { m ->
                JSONObject().put("uid",m.uid).put("nickname",m.nickname).put("status",m.status).put("goal",m.goal).put("days",m.days)
            }))
    }).toString()
    fun receipt(s: EstadoSessao, endedAt: Long): String {
        require(s.seriesFeitas > 0 && s.itens.size <= 60)
        return JSONObject().put("title", s.nome.take(120)).put("ended_at", Instant.ofEpochMilli(endedAt).toString())
            .put("duration_seconds", ((endedAt - s.iniciadaEm) / 1000).coerceIn(0, 604800))
            .put("exercises", JSONArray(s.itens.filter { it.feitas.isNotEmpty() }.map { item ->
                JSONObject().put("id", item.item.exercicio.id).put("name", item.item.exercicio.nome.take(120))
                    .put("muscle", item.item.exercicio.musculo.name).put("sets", JSONArray(item.feitas.map { set ->
                        JSONObject().put("kg", set.pesoKg).put("reps", set.reps).put("seconds", set.segundos).put("pr", set.recorde)
                    }))
            })).toString().also(::validateReceipt)
    }
    fun circles(body: String): List<TrainingCircle> {
        val rows = JSONArray(body); require(rows.length() <= 30)
        return (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i); val members = r.getJSONArray("members"); require(members.length() <= 30)
            TrainingCircle(r.getString("id"), r.getString("owner"), r.getString("name"), r.getString("timezone"),
                r.getString("status"), r.getString("week_start"), (0 until members.length()).map { j ->
                    val m = members.getJSONObject(j); require(m.getInt("goal") in 1..7)
                    CircleMember(m.getString("uid"), m.getString("nickname"), m.getString("status"), m.getInt("goal"), m.getInt("days").coerceIn(0, 7))
                })
        }
    }
    fun posts(body: String): List<CirclePost> {
        val rows = JSONArray(body); require(rows.length() <= 20)
        return (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r ->
            CirclePost(r.getString("id"), r.getString("session_id"), r.getString("uid"), r.getString("nickname"),
                r.getString("created_at"), r.getJSONObject("receipt").toString(),CircleConversationCodec.social(r.optJSONObject("social")))
        } }
    }
}
