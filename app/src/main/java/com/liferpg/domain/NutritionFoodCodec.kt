package com.liferpg.domain

import com.liferpg.data.Refeicao
import org.json.JSONObject

object NutritionFoodCodec {
    fun escrever(f: AlimentoEncontrado): String = JSONObject().apply {
        put("nome", f.nome); put("marca", f.marca ?: JSONObject.NULL); put("barcode", f.barcode ?: JSONObject.NULL)
        put("id", f.id); put("fonte", f.fonte); put("proteinaG", f.proteinaG.toDouble()); put("calorias", f.calorias.toDouble())
        put("carboG", f.carboG.toDouble()); put("gorduraG", f.gorduraG.toDouble()); put("fibraG", f.fibraG?.toDouble() ?: JSONObject.NULL)
        put("imagemUrl", f.imagemUrl ?: JSONObject.NULL); put("dadosAusentes", f.dadosAusentes)
        put("grupo", f.grupo ?: JSONObject.NULL)
    }.toString()

    fun ler(raw: String): AlimentoEncontrado {
        require(raw.length <= 6000)
        val o = JSONObject(raw)
        fun n(key: String) = o.getDouble(key).toFloat().also { require(it.isFinite() && it in 0f..100_000f) }
        fun optional(key: String) = if (o.isNull(key) || !o.has(key)) null else o.getString(key).take(300)
        val nome = o.getString("nome").trim().also { require(it.isNotEmpty() && it.length <= 160) }
        return AlimentoEncontrado(nome, optional("marca"), n("proteinaG"), n("calorias"), n("carboG"), n("gorduraG"), optional("barcode"),
            o.optString("id").take(160), o.optString("fonte", "Registro manual").take(120),
            if (o.isNull("fibraG") || !o.has("fibraG")) null else n("fibraG"),
            optional("imagemUrl")?.takeIf { it.startsWith("https://") }, o.optInt("dadosAusentes", 0).also { require(it in 0..15) }, optional("grupo"))
    }
    fun daRefeicao(r: Refeicao): AlimentoEncontrado? {
        if (!r.porcaoG.isFinite() || r.porcaoG <= 0) return null
        val f = 100 / r.porcaoG
        return AlimentoEncontrado(r.nome, null, r.proteinaG * f, r.calorias * f, r.carboG * f, r.gorduraG * f, r.barcode,
            fonte = r.fonte, fibraG = r.fibraG?.times(f), dadosAusentes = if (r.macrosCompletos) 0 else (if (r.carboG <= 0) 4 else 0) or (if (r.gorduraG <= 0) 8 else 0))
    }
}
