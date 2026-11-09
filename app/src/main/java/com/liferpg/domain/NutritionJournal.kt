package com.liferpg.domain

import com.liferpg.data.Refeicao
import java.text.Normalizer
import org.json.JSONObject

enum class MomentoRefeicao(val titulo: String) {
    CAFE("Café da manhã"), ALMOCO("Almoço"), LANCHE("Lanche"), JANTAR("Jantar"), OUTROS("Outros");
    companion object { fun seguro(value: String) = entries.firstOrNull { it.name == value } ?: OUTROS }
}

data class Nutrientes(val calorias: Float = 0f, val proteina: Float = 0f, val carbo: Float = 0f, val gordura: Float = 0f) {
    operator fun plus(other: Nutrientes) = Nutrientes(calorias + other.calorias, proteina + other.proteina, carbo + other.carbo, gordura + other.gordura)
    companion object { fun das(refeicoes: List<Refeicao>) = refeicoes.fold(Nutrientes()) { total, r -> total + Nutrientes(r.calorias, r.proteinaG, r.carboG, r.gorduraG) } }
}

fun normalizarAlimento(texto: String): String = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ").trim()

data class ComponentePrato(val nome: String, val alimentoId: String?, val gramas: Float, val confianca: String)
data class AnalisePrato(val componentes: List<ComponentePrato>, val observacao: String)

/** Camera identifies candidate foods. Macros ONLY come from the reference table after review. */
object PratoParser {
    fun ler(raw: String, ids: Set<String>): AnalisePrato {
        require(raw.length <= 16_000) { "Análise grande demais. Fotografe um prato por vez." }
        val root = JSONObject(raw)
        val items = root.getJSONArray("alimentos")
        require(items.length() <= 6) { "Prato muito complexo. Revise os componentes separadamente." }
        val foods = (0 until items.length()).map { index ->
            val o = items.getJSONObject(index)
            val nome = o.getString("nome").trim()
            val grams = o.getDouble("gramas").toFloat()
            require(nome.isNotBlank() && nome.length <= 80 && grams.isFinite() && grams in 1f..3000f) { "A análise retornou uma porção inválida. Tire outra foto." }
            val id = if (o.isNull("id")) null else o.optString("id").takeIf { it in ids }
            ComponentePrato(nome, id, grams, o.optString("confianca").takeIf { it in listOf("alta", "media", "baixa") } ?: "baixa")
        }
        return AnalisePrato(foods, root.optString("observacao").take(300))
    }
}

/** Feedback reflects what was logged, never treating an incomplete diary as total daily intake. */
object NutritionInsights {
    fun temVegetais(alimentos: List<AlimentoEncontrado>): Boolean = alimentos.any {
        normalizarAlimento(it.grupo.orEmpty()).contains("verduras")
    }
    fun diario(refeicoes: List<Refeicao>, metas: MetasNutricao): List<String> {
        if (refeicoes.isEmpty()) return listOf("Comece pelo que você já comeu. O diário ajuda a enxergar sua rotina, sem exigir uma dieta perfeita.")
        val total = Nutrientes.das(refeicoes)
        return buildList {
            if (refeicoes.any { !it.macrosCompletos }) add("Há registros com macros incompletos. Edite-os para melhorar os totais de carboidratos e gorduras.")
            if (refeicoes.any { it.estimado }) add("As porções vindas da foto são estimativas. Uma balança ou o rótulo melhora a precisão.")
            if (metas.proteinaG > 0) {
                val falta = (metas.proteinaG - total.proteina).toInt()
                add(if (falta > 0) "No diário, faltam aproximadamente $falta g para sua referência de proteína. Distribua as fontes entre as refeições."
                    else "A proteína registrada alcançou sua referência. Observe também variedade, carboidratos e gorduras.")
            }
            if (metas.calorias > 0 && total.calorias > metas.calorias) add("O registro passou sua referência de energia. Confira porções e preparos; não compense pulando refeições.")
            add("Arroz, feijão, legumes e uma fonte de proteína podem compor uma refeição variada. Acrescente os óleos e molhos usados no preparo ao registro.")
        }.take(4)
    }
    fun prato(total: Nutrientes, restantes: Nutrientes, temVegetais: Boolean): List<String> = buildList {
        if (total.proteina < 15 && restantes.proteina > 20) add("Este prato tem pouca proteína em relação ao restante da sua meta. Considere ovos, feijão, frango, peixe ou outra fonte que faça parte da sua rotina.")
        if (total.carbo < 15 && restantes.carbo > 40) add("Os carboidratos deste registro estão baixos. Arroz, batata ou outro cereal podem complementar a energia para o treino.")
        if (!temVegetais) add("Não identificamos legumes ou verduras entre os itens confirmados. Se estiverem no prato, inclua-os na revisão; se não, vale variar na próxima refeição.")
        add("Óleo, recheios, molhos e ingredientes escondidos não são medidos pela câmera. Acrescente-os para melhorar a estimativa.")
    }.take(3)
}
