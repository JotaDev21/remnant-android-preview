package com.liferpg.data.sync

import org.json.JSONArray
import org.json.JSONObject

enum class CircleReaction(val key: String,val title: String) {
    RESPECT("respect","Força"), FIRE("fire","Brutal"), RECORD("record","PR");
    companion object { fun of(key:String?)=entries.firstOrNull { it.key==key } }
}
data class CircleSocial(val respect:Int=0,val fire:Int=0,val record:Int=0,val mine:CircleReaction?=null,val comments:Int=0) {
    fun count(kind:CircleReaction)=when(kind) { CircleReaction.RESPECT->respect; CircleReaction.FIRE->fire; CircleReaction.RECORD->record }
    val total get()=respect+fire+record
}
data class CircleComment(val id:String,val uid:String,val nickname:String,val body:String,val createdAt:String)
data class CircleConversation(val post:CirclePost,val comments:List<CircleComment>)
data class CircleCommentNotice(val id:String,val circle:String,val post:String,val nickname:String,val title:String,val createdAt:String)
data class CircleConversationTarget(val circle:String,val post:String) {
    val route get()="circulos/$circle/recibo/$post"
    companion object {
        private val pattern=Regex("^circulos/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/recibo/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$")
        fun parse(route:String?)=route?.let { pattern.matchEntire(it) }?.let { CircleConversationTarget(it.groupValues[1],it.groupValues[2]) }
    }
}
object CircleConversationCodec {
    fun social(r:JSONObject?)=CircleSocial(r?.optInt("respect")?.coerceAtLeast(0)?:0,r?.optInt("fire")?.coerceAtLeast(0)?:0,
        r?.optInt("record")?.coerceAtLeast(0)?:0,CircleReaction.of(r?.optString("mine")),r?.optInt("comments")?.coerceAtLeast(0)?:0)
    fun conversation(body:String):CircleConversation {
        val r=JSONObject(body); val post=CircleCodec.posts(JSONArray().put(r.getJSONObject("post")).toString()).single()
        val rows=r.getJSONArray("comments"); require(rows.length()<=30)
        val comments=(0 until rows.length()).map { rows.getJSONObject(it).let { c ->
            CircleComment(c.getString("id"),c.getString("uid"),c.getString("nickname"),c.getString("body").also { require(it.length in 1..800) },c.getString("created_at"))
        } }
        return CircleConversation(post,comments)
    }
    fun notices(body:String):List<CircleCommentNotice> {
        val rows=JSONArray(body); require(rows.length()<=30)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { r -> CircleCommentNotice(r.getString("id"),r.getString("circle_id"),
            r.getString("post_id"),r.getString("nickname"),r.getString("title"),r.getString("created_at")) } }
    }
}
