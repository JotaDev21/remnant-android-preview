package com.liferpg.data.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

data class PactMember(val uid: String, val nickname: String, val status: String, val goal: Int, val days: Int) {
    val contribution get() = if (status != "accepted") 0f else (days.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
}
data class TrainingPact(val id: String, val owner: String, val name: String, val boss: String, val status: String,
    val startsAt: String?, val endsAt: String?, val inviteDeadline: String, val members: List<PactMember>) {
    val participants get() = members.filter { it.status == "accepted" }
    val progress get() = if (participants.isEmpty()) 0f else participants.sumOf { it.contribution.toDouble() }.toFloat() / participants.size
    val bossName get() = when(boss) { "CRYPT" -> "Colosso da Cripta"; "ASH" -> "Carrasco das Cinzas"; else -> "Guardião da Forja" }
    val startedMillis get() = startsAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
}
data class FriendEvolution(val week: Long, val goal: Int, val days: Int, val improvements: Map<String, Float>,
    val campaignName: String? = null, val chapters: Int = 0, val weeks: Int = 0)
data class CloudBackup(val revision: Long, val payload: String, val updatedAt: String) {
    val nickname get() = JSONObject(payload).optJSONObject("perfil")?.optString("nome").orEmpty()
    val xp get() = JSONObject(payload).optJSONObject("perfil")?.optLong("xpTotal") ?: 0
    val setCount get() = JSONObject(payload).optJSONArray("series")?.length() ?: 0
}

object EvolutionNetworkCodec {
    fun pacts(body: String): List<TrainingPact> {
        val rows = JSONArray(body)
        require(rows.length() <= 50)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i); val people = row.getJSONArray("members")
            require(people.length() in 1..5)
            TrainingPact(row.getString("id"), row.getString("owner"), row.getString("name"), row.getString("boss"), row.getString("status"),
                row.optionalString("starts_at"), row.optionalString("ends_at"), row.getString("invite_deadline"),
                (0 until people.length()).map { n -> people.getJSONObject(n).let { person ->
                    val goal = person.getInt("goal"); require(goal in 1..7)
                    PactMember(person.getString("uid"), person.getString("nickname"), person.getString("status"), goal, person.getInt("days").coerceIn(0, goal))
                } })
        }
    }
    fun friend(body: String): FriendEvolution? {
        if (body.trim() == "null") return null
        val row = JSONObject(body); val records = row.getJSONArray("records"); val campaign = row.optJSONObject("campaign")
        require(row.getInt("goal") in 1..7 && row.getInt("days") in 0..7 && records.length() <= 1000)
        return FriendEvolution(row.getLong("week"), row.getInt("goal"), row.getInt("days"),
            (0 until records.length()).associate { i -> records.getJSONObject(i).let { it.getString("id") to it.getDouble("pct").toFloat() } },
            campaign?.optString("name"), campaign?.optInt("chapters") ?: 0, campaign?.optInt("weeks") ?: 0)
    }
    fun backup(body: String): CloudBackup? {
        if (body.trim() == "null") return null
        val row = JSONObject(body)
        return CloudBackup(row.getLong("revision"), row.getJSONObject("payload").toString(), row.getString("updated_at"))
    }
    private fun JSONObject.optionalString(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
