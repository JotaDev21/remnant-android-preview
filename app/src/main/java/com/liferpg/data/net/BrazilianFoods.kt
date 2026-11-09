package com.liferpg.data.net

import android.content.Context
import com.liferpg.domain.AlimentoEncontrado
import com.liferpg.domain.normalizarAlimento
import org.json.JSONObject

/** Published laboratory reference values, per 100 g of edible portion; raw/cooked remain separate. */
class BrazilianFoods(context: Context) {
    private val app = context.applicationContext
    val alimentos: List<AlimentoEncontrado> by lazy {
        val array = JSONObject(app.assets.open("nutrition/taco.json").bufferedReader().use { it.readText() }).getJSONArray("alimentos")
        (0 until array.length()).map { i -> array.getJSONObject(i).let { o ->
            AlimentoEncontrado(o.getString("nome"), null, o.getDouble("proteinaG").toFloat(), o.getDouble("calorias").toFloat(),
                o.getDouble("carboG").toFloat(), o.getDouble("gorduraG").toFloat(), null,
                id = o.getString("id"), fonte = "TACO · NEPA/UNICAMP · 2011",
                fibraG = if (o.isNull("fibraG")) null else o.getDouble("fibraG").toFloat(),
                grupo = o.optString("categoria").takeIf { it.isNotBlank() })
        } }
    }
    fun buscar(termo: String): List<AlimentoEncontrado> {
        val words = normalizarAlimento(termo).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return alimentos.filter { food -> val name = normalizarAlimento(food.nome); words.all { name.contains(it) } }
            .sortedWith(compareBy<AlimentoEncontrado> { !it.nome.contains("cozido", true) && !it.nome.contains("grelhado", true) }.thenBy { it.nome.length })
    }
    fun obter(id: String?) = alimentos.firstOrNull { it.id == id }
    fun referenciaCamera(): String = alimentos.joinToString("\n") { "${it.id} ${it.nome}" }
}
