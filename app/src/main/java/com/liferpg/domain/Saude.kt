package com.liferpg.domain

import com.liferpg.data.Perfil
import com.liferpg.data.Preferencias
import kotlin.math.roundToInt

/**
 * A self-reported condition that constrains exercise selection.
 * Persisted as a comma-separated list of [name]s in `Preferencias.condicoes`.
 */
enum class CondicaoSaude(
    val titulo: String,
    val musculosEvitar: Set<Musculo>,
    val evitarCargaAlta: Boolean = false,
) {
    LESAO_JOELHO("Lesão no joelho", setOf(Musculo.QUADRICEPS, Musculo.PANTURRILHA)),
    LESAO_OMBRO("Lesão no ombro", setOf(Musculo.OMBROS)),
    LESAO_LOMBAR("Lesão na lombar/coluna", setOf(Musculo.POSTERIOR, Musculo.COSTAS), true),
    LESAO_COTOVELO("Lesão no cotovelo", setOf(Musculo.TRICEPS, Musculo.BICEPS)),
    LESAO_PUNHO("Lesão no punho", setOf(Musculo.ANTEBRACO)),
    HIPERTENSAO("Hipertensão", emptySet(), true),
    HERNIA("Hérnia abdominal", setOf(Musculo.ABDOMEN), true);

    companion object {
        /** Reads the persisted CSV, silently dropping tokens that no longer exist. */
        fun parse(csv: String): Set<CondicaoSaude> =
            csv.split(",")
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
                .toSet()

        fun musculosEvitados(csv: String): Set<Musculo> =
            parse(csv).flatMap { it.musculosEvitar }.toSet()

        fun evitarCargaAlta(csv: String): Boolean = parse(csv).any { it.evitarCargaAlta }

        fun paraCsv(set: Set<CondicaoSaude>): String = set.joinToString(",") { it.name }
    }
}

/** Preferred time of day to train; [hora] drives the reminder notification. */
enum class HorarioTreino(val titulo: String, val hora: Int) {
    MANHA("Manhã", 7),
    TARDE("Tarde", 14),
    NOITE("Noite", 19),
    QUALQUER("Sem preferência", 18);

    companion object {
        fun seguro(nome: String?): HorarioTreino =
            entries.firstOrNull { it.name == nome } ?: QUALQUER
    }
}

/** [ajusteBmr] is the sex constant of the Mifflin-St Jeor equation. */
enum class Sexo(val titulo: String, val ajusteBmr: Int) {
    MASCULINO("Masculino", 5),
    FEMININO("Feminino", -161),
    OUTRO("Prefiro não informar", -78);

    companion object {
        fun seguro(nome: String?): Sexo = entries.firstOrNull { it.name == nome } ?: OUTRO
    }
}

/** Daily nutrition targets derived from the profile. */
data class MetasNutricao(
    val calorias: Int,
    val proteinaG: Int,
    val carboG: Int,
    val gorduraG: Int,
    val bmr: Int,
)

/** Mifflin-St Jeor BMR → activity-scaled TDEE → goal-adjusted macros. */
object NutricaoEngine {

    fun calcular(perfil: Perfil, prefs: Preferencias): MetasNutricao {
        val peso = perfil.pesoCorporalKg.coerceIn(30f, 350f)
        val altura = perfil.alturaCm
        val idade = perfil.idade
        val sexo = Sexo.seguro(perfil.sexo)
        // A guessed age/height/sex cannot produce a personalized energy target.
        val podeEstimar = idade in 18..100 && altura in 120..230 && sexo != Sexo.OUTRO
        val bmr = if (podeEstimar) (10f * peso + altura * 6.25f - idade * 5f + sexo.ajusteBmr).roundToInt() else 0
        val fatorAtividade = when (prefs.atividadeNutri) {
            "LEVE" -> 1.375f
            "MODERADA" -> 1.55f
            "ALTA" -> 1.725f
            else -> 1.2f
        }
        val tdee = bmr * fatorAtividade

        val obj = Objetivo.seguro(prefs.objetivo)
        // Training endurance does NOT mean a weight-loss goal.
        val ajusteCalorias = when (prefs.objetivoNutri) {
            "GANHAR" -> 1.05f
            "REDUZIR" -> .9f
            else -> 1f
        }
        val calorias = if (prefs.metaCalorias > 0) prefs.metaCalorias else (tdee * ajusteCalorias).roundToInt()

        val gProteinaPorKg = when (obj) {
            Objetivo.HIPERTROFIA, Objetivo.FORCA -> 2.0f
            else -> 1.6f
        }
        val proteinaG = if (prefs.metasNutricaoManuais) prefs.metaProteinaG else if (idade in 18..100) (peso * gProteinaPorKg).roundToInt() else 0
        val gorduraG = if (prefs.metasNutricaoManuais) prefs.metaGorduraG else (calorias * 0.25f / 9f).roundToInt()
        val carboG = if (prefs.metasNutricaoManuais) prefs.metaCarboG else ((calorias - proteinaG * 4 - gorduraG * 9) / 4f)
            .coerceAtLeast(0f)
            .roundToInt()

        return MetasNutricao(calorias, proteinaG, carboG, gorduraG, bmr)
    }
}
