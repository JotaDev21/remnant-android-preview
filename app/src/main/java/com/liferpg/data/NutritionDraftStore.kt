package com.liferpg.data

import android.content.Context
import com.liferpg.domain.AnalisePrato
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ItemPratoRevisao(val nome: String, val alimentoId: String?, val porcao: String, val confianca: String, val incluido: Boolean = true) {
    val gramas: Float? get() = porcao.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in 1f..3000f }
}
data class PratoRevisao(val itens: List<ItemPratoRevisao>, val observacao: String, val fotoUri: String?, val id: String = UUID.randomUUID().toString())

/** A reviewed plate draft survives leaving the screen, backgrounding and process recreation. */
class NutritionDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("nutrition_plate_draft", Context.MODE_PRIVATE)
    fun ler(): PratoRevisao? = runCatching {
        val raw = prefs.getString("prato", null) ?: return null
        val o = JSONObject(raw); val items = o.getJSONArray("itens")
        require(items.length() <= 6)
        PratoRevisao((0 until items.length()).map { i -> items.getJSONObject(i).let { r ->
            ItemPratoRevisao(r.getString("nome").take(160), r.optString("id").takeIf { it.isNotBlank() }, r.getString("porcao").take(12), r.optString("confianca"), r.optBoolean("incluido", true))
        } }, o.optString("observacao").take(300), o.optString("fotoUri").takeIf { it.startsWith("content://") },
            o.optString("id").takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.randomUUID().toString()).also { if (!o.has("id")) salvar(it) }
    }.getOrNull()
    fun salvar(draft: PratoRevisao?) {
        if (draft == null) { prefs.edit().remove("prato").apply(); return }
        val o = JSONObject().put("id", draft.id).put("observacao", draft.observacao).put("fotoUri", draft.fotoUri ?: "")
            .put("itens", JSONArray().apply { draft.itens.forEach { r -> put(JSONObject().put("nome", r.nome).put("id", r.alimentoId ?: "").put("porcao", r.porcao).put("confianca", r.confianca).put("incluido", r.incluido)) } })
        prefs.edit().putString("prato", o.toString()).apply()
    }
    fun criar(analysis: AnalisePrato, uri: String) = PratoRevisao(analysis.componentes.map {
        ItemPratoRevisao(it.nome, it.alimentoId, it.gramas.toInt().toString(), it.confianca)
    }, analysis.observacao, uri)
}
