package com.liferpg.domain

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass

/** Every muscle group the app tracks. Order drives the whole UI listing. */
@JsonClass(generateAdapter = false)
enum class Musculo(val nomeExibicao: String, val emoji: String) {
    PEITO("Peito", "🛡️"),
    COSTAS("Costas", "🦅"),
    OMBROS("Ombros", "⚔️"),
    BICEPS("Bíceps", "💪"),
    TRICEPS("Tríceps", "🔱"),
    ANTEBRACO("Antebraço", "🪝"),
    ABDOMEN("Abdômen", "🐉"),
    QUADRICEPS("Quadríceps", "🏛️"),
    POSTERIOR("Posterior", "🐎"),
    GLUTEOS("Glúteos", "🔥"),
    PANTURRILHA("Panturrilha", "⚡"),
}

/** How a set is measured, which decides the unit shown next to the number. */
@JsonClass(generateAdapter = false)
enum class ModoMedida { CARGA, REPS, TEMPO }

@JsonClass(generateAdapter = false)
enum class LocalTreino(val nomeExibicao: String) {
    CASA("Em Casa"),
    ACADEMIA("Academia"),
    AMBOS("Casa ou Academia"),
}

/**
 * Rank ladder. `SEM_PATENTE` is the unranked placeholder and is deliberately
 * excluded from [Patente.ranqueadas] and from the benchmark table, hence
 * [benchmarkIndex] being one less than the ordinal.
 */
enum class Patente(
    val titulo: String,
    val cor: Color,
    val corBrilho: Color,
    val drawable: String,
) {
    SEM_PATENTE("Recruta", Color(0xFF4A5568), Color(0xFF718096), "rank_recruta"),
    BRONZE("Bronze", Color(0xFFCD7F32), Color(0xFFE8A45C), "rank_bronze"),
    PRATA("Prata", Color(0xFFB8C2CC), Color(0xFFE2E8F0), "rank_prata"),
    OURO("Ouro", Color(0xFFF6C12B), Color(0xFFFFE08A), "rank_ouro"),
    PLATINA("Platina", Color(0xFF4FD8C4), Color(0xFF9BF0E1), "rank_platina"),
    DIAMANTE("Diamante", Color(0xFF5EC8F8), Color(0xFFB9E8FF), "rank_diamante"),
    MESTRE("Mestre", Color(0xFFB24BF3), Color(0xFFD9A6FF), "rank_mestre"),
    LENDARIO("Lendário", Color(0xFFFF5C1F), Color(0xFFFFA245), "rank_lendario"),
    BRUTO("Bruto", Color(0xFFC65B3C), Color(0xFFE89A6B), "rank_bruto"),
    GLADIADOR("Gladiador", Color(0xFF8FA3B8), Color(0xFFC7D6E6), "rank_gladiador"),
    BERSERKER("Berserker", Color(0xFFE0452B), Color(0xFFFF7A4D), "rank_berserker"),
    COLOSSO("Colosso", Color(0xFF2FB58A), Color(0xFF6BE3BD), "rank_colosso"),
    TITA("Titã", Color(0xFF22D3EE), Color(0xFF9BF0E1), "rank_tita"),
    OLIMPIANO("Olimpiano", Color(0xFFB24BF3), Color(0xFFF6C12B), "rank_olimpiano"),
    IMORTAL("Imortal", Color(0xFFF6C12B), Color(0xFFFFE08A), "rank_imortal");

    /** Index into an exercise's `benchmarks` list, which has no unranked entry. */
    val benchmarkIndex: Int get() = ordinal - 1

    /** From BRUTO upwards the rank gets the epic frame treatment. */
    val ehEpica: Boolean get() = ordinal >= BRUTO.ordinal

    companion object {
        /** Every rank except the unranked placeholder. */
        val ranqueadas: List<Patente> = entries.drop(1)
    }
}

/**
 * Character class. Each grants +20% XP on its favourite muscles, except
 * AVENTUREIRO which trades that for a flat bonus, and MONGE which also
 * rewards bodyweight work.
 */
enum class ClassePersonagem(
    val titulo: String,
    val emoji: String,
    val descricaoPerk: String,
    val musculosFavoritos: Set<Musculo>,
    val bonusGeral: Float = 0f,
    val bonusCorporal: Boolean = false,
) {
    GUERREIRO(
        "Guerreiro", "⚔️", "+20% XP em Peito, Ombros e Tríceps",
        setOf(Musculo.PEITO, Musculo.OMBROS, Musculo.TRICEPS),
    ),
    CACADOR(
        "Caçador", "🏹", "+20% XP em Costas, Bíceps e Antebraço",
        setOf(Musculo.COSTAS, Musculo.BICEPS, Musculo.ANTEBRACO),
    ),
    COLOSSO(
        "Colosso", "🗿", "+20% XP em Quadríceps, Posterior, Glúteos e Panturrilha",
        setOf(Musculo.QUADRICEPS, Musculo.POSTERIOR, Musculo.GLUTEOS, Musculo.PANTURRILHA),
    ),
    MONGE(
        "Monge", "🧘", "+20% XP em Abdômen e em exercícios com peso corporal",
        setOf(Musculo.ABDOMEN), bonusCorporal = true,
    ),
    AVENTUREIRO(
        "Aventureiro", "🧭", "+8% XP em todos os treinos",
        emptySet(), bonusGeral = 0.08f,
    ),
}

/**
 * One exercise as it ships in `assets/exercises.json`.
 *
 * [benchmarks] is nullable: entries without a strength table fall back to
 * difficulty-based scoring.
 */
@JsonClass(generateAdapter = true)
data class Exercicio(
    val id: String,
    val nome: String,
    val musculo: Musculo,
    val musculosSecundarios: List<Musculo>,
    val equipamento: String,
    val local: LocalTreino,
    val modo: ModoMedida,
    val dificuldade: Float,
    val mecanica: String,
    val instrucoes: List<String>,
    val dica: String,
    val benchmarks: List<Float>?,
    val imagens: List<String>,
    val videoQuery: String,
) {
    val unidade: String
        get() = when (modo) {
            ModoMedida.CARGA -> "kg"
            ModoMedida.REPS -> "reps"
            ModoMedida.TEMPO -> "seg"
        }
}

/** Root of `assets/exercises.json`. */
@JsonClass(generateAdapter = true)
data class CatalogoExercicios(
    val exercicios: List<Exercicio>,
)
