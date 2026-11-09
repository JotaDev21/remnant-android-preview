package com.liferpg.domain

data class StarterWorkout(val nome: String, val dias: Set<Int>, val itens: List<ItemRotinaDef>)

/** A small, editable starting template. Never assigns a load or copies an existing user's PR. */
object StarterPlan {
    fun classe(foco: Musculo?): ClassePersonagem = ClassePersonagem.entries.firstOrNull {
        foco != null && foco in it.musculosFavoritos
    } ?: ClassePersonagem.AVENTUREIRO

    fun diasPadrao(quantidade: Int): Set<Int> = when (quantidade) {
        1 -> setOf(1); 2 -> setOf(1, 4); 3 -> setOf(1, 3, 5)
        4 -> setOf(1, 2, 4, 5); 5 -> setOf(1, 2, 4, 5, 6)
        else -> setOf(1, 2, 3, 4, 5, 6)
    }

    fun criar(catalogo: List<Exercicio>, dias: Set<Int>, objetivo: Objetivo,
        experiencia: NivelExperiencia, local: LocalTreino, foco: Musculo?): List<StarterWorkout> {
        val agenda = dias.filter { it in 1..7 }.distinct().sorted().take(6)
        if (agenda.isEmpty()) return emptyList()
        val pool = catalogo.filter { it.local == LocalTreino.AMBOS || local == LocalTreino.AMBOS || it.local == local }
        val favoritos = listOf("Barbell_Bench_Press_-_Medium_Grip", "Incline_Dumbbell_Press", "Pushups",
            "Wide-Grip_Lat_Pulldown", "Seated_Cable_Rows", "Dumbbell_Shoulder_Press", "Side_Lateral_Raise",
            "Leg_Press", "Leg_Extensions", "Romanian_Deadlift", "Lying_Leg_Curls", "Bodyweight_Squat",
            "Barbell_Curl", "Hammer_Curls", "Triceps_Pushdown", "Standing_Calf_Raises", "Plank")
        val membrosInferiores = listOf(Musculo.QUADRICEPS, Musculo.POSTERIOR, Musculo.GLUTEOS, Musculo.PANTURRILHA)
        val superior = listOf(Musculo.PEITO, Musculo.COSTAS, Musculo.OMBROS, Musculo.BICEPS, Musculo.TRICEPS)
        val splits = if (agenda.size <= 3) listOf(listOf(Musculo.QUADRICEPS, Musculo.PEITO,
            Musculo.COSTAS, Musculo.POSTERIOR, Musculo.OMBROS, foco ?: Musculo.ABDOMEN))
            else if (agenda.size <= 5) listOf(superior, membrosInferiores + (foco?.takeIf { it in membrosInferiores } ?: Musculo.ABDOMEN))
            else listOf(listOf(Musculo.PEITO, Musculo.OMBROS, Musculo.TRICEPS),
                listOf(Musculo.COSTAS, Musculo.BICEPS, Musculo.ANTEBRACO), membrosInferiores + Musculo.ABDOMEN)
        return agenda.mapIndexed { index, dia ->
            val alvos = splits[index % splits.size].toMutableList()
            if (foco != null && agenda.size > 3 && foco in alvos) {
                alvos.remove(foco); alvos.add(0, foco)
            }
            val escolhidos = mutableListOf<Exercicio>()
            for (musculo in alvos) {
                val options = pool.filter { it.musculo == musculo && it.id !in escolhidos.map { e -> e.id } }
                    .sortedWith(compareBy<Exercicio> { favoritos.indexOf(it.id).takeIf { n -> n >= 0 } ?: 999 }
                        .thenBy { it.dificuldade }.thenBy { it.id })
                val alternativa = if (agenda.size <= 3 && options.size > 1) index % minOf(2, options.size) else 0
                options.getOrNull(alternativa)?.let(escolhidos::add)
            }
            val iniciante = experiencia == NivelExperiencia.INICIANTE
            val items = escolhidos.take(6).map { ex ->
                val min = if (iniciante && objetivo == Objetivo.FORCA) 6 else objetivo.repMin
                val max = if (iniciante && objetivo == Objetivo.FORCA) 8 else objetivo.repMax
                ItemRotinaDef(ex.id, if (iniciante) 2 else 3,
                    if (ex.modo == ModoMedida.TEMPO) 7 else min,
                    if (ex.modo == ModoMedida.TEMPO) 10 else max,
                    objetivo.descansoSeg.coerceAtLeast(if (iniciante) 75 else 45))
            }
            val titulo = if (agenda.size <= 3) "Corpo inteiro" else if (agenda.size <= 5)
                (if (index % 2 == 0) "Parte superior" else "Pernas e core") else
                listOf("Peito, ombros e tríceps", "Costas e bíceps", "Pernas e core")[index % 3]
            StarterWorkout("Base ${('A'.code + index).toChar()} · $titulo", setOf(dia), items)
        }.filter { it.itens.isNotEmpty() }
    }
}
