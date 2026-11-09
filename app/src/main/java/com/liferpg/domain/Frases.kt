package com.liferpg.domain

import kotlin.random.Random

data class Frase(val texto: String, val autor: String)

/** Motivational copy and rest-day mobility tips, picked deterministically per day. */
object Frases {

    private val TODAS = listOf(
        Frase("A dor que você sente hoje é a força que você sente amanhã.", "Anônimo"),
        Frase("Não conte os dias, faça os dias contarem.", "Muhammad Ali"),
        Frase("O corpo alcança o que a mente acredita.", "Anônimo"),
        Frase("Disciplina é fazer o que precisa ser feito, mesmo sem vontade.", "Anônimo"),
        Frase("Cada série te aproxima da próxima patente.", "LifeRPG"),
        Frase("Sucesso é a soma de pequenos esforços repetidos todos os dias.", "Robert Collier"),
        Frase("O único treino ruim é o que você não fez.", "Anônimo"),
        Frase("Force seus limites — é lá que o progresso mora.", "Anônimo"),
        Frase("Seja mais forte que sua melhor desculpa.", "Anônimo"),
        Frase("Grandes feitos nascem de pequenas repetições.", "LifeRPG"),
        Frase("O ferro nunca mente.", "Henry Rollins"),
        Frase("Você contra você de ontem. Vença essa luta.", "Anônimo"),
        Frase("Constância vence intensidade no longo prazo.", "Anônimo"),
        Frase("O suor de hoje é o troféu de amanhã.", "Anônimo"),
        Frase("Respeite o processo: patente não se compra, se conquista.", "LifeRPG"),
        Frase("Comece onde você está. Use o que você tem. Faça o que pode.", "Arthur Ashe"),
        Frase("Músculo se constrói na recuperação, não só no treino.", "Anônimo"),
        Frase("Quem treina hoje, lidera amanhã.", "Anônimo"),
        Frase("A motivação te começa, o hábito te mantém.", "Jim Ryun"),
        Frase("Não pare quando estiver cansado. Pare quando terminar.", "David Goggins"),
        Frase("Sua única competição é quem você foi ontem.", "Anônimo"),
        Frase("Proteína, sono e progressão: a tríade do guerreiro.", "LifeRPG"),
        Frase("Um por cento melhor a cada dia é 37 vezes melhor no ano.", "James Clear"),
        Frase("O melhor projeto em que você vai trabalhar é você mesmo.", "Anônimo"),
        Frase("Lendário não é um ponto de chegada, é um hábito.", "LifeRPG"),
        Frase("Levante mais pesado que suas dúvidas.", "Anônimo"),
        Frase("Treine pela versão de você que ainda não existe.", "Anônimo"),
        Frase("Quando quiser desistir, lembre por que começou.", "Anônimo"),
        Frase("A consistência é a chave que abre todas as patentes.", "LifeRPG"),
        Frase("Pequenos progressos ainda são progressos.", "Anônimo"),
        Frase("Transforme o 'não consigo' em 'mais uma repetição'.", "Anônimo"),
        Frase("Cada recorde quebrado é uma nova versão sua nascendo.", "LifeRPG"),
        Frase("Foco no fraco: o ponto que dói é o que mais cresce.", "Anônimo"),
        Frase("Não existe atalho para lugar que vale a pena ir.", "Beverly Sills"),
        Frase("Hoje é um bom dia para ficar mais forte.", "Anônimo"),
        Frase("Você é capaz de muito mais do que imagina.", "Anônimo"),
    )

    private val MOBILIDADE: Map<Musculo, List<String>> = mapOf(
        Musculo.PEITO to listOf(
            "Alongue o peitoral na quina da porta por 30s",
            "Rotações de ombro para frente e para trás",
        ),
        Musculo.COSTAS to listOf(
            "Pendure-se na barra por 20–30s (descompressão)",
            "Gato-camelo: 8 ciclos lentos",
        ),
        Musculo.OMBROS to listOf(
            "Círculos de braço grandes, 10 para cada lado",
            "Passada de bastão/elástico sobre a cabeça",
        ),
        Musculo.BICEPS to listOf(
            "Alongue o bíceps com o braço estendido na parede",
            "Punho para cima e para baixo, 15x",
        ),
        Musculo.TRICEPS to listOf(
            "Cotovelo atrás da cabeça, puxe leve por 30s",
            "Flexão/extensão de cotovelo controlada",
        ),
        Musculo.ANTEBRACO to listOf(
            "Alongue flexores e extensores do punho, 30s cada",
            "Aperte e solte os punhos, 20x",
        ),
        Musculo.ABDOMEN to listOf(
            "Cobra (alongamento do abdômen) por 20s",
            "Respiração diafragmática, 5 ciclos",
        ),
        Musculo.QUADRICEPS to listOf(
            "Alongue o quadríceps em pé, 30s cada perna",
            "Agachamento profundo segurando por 20s",
        ),
        Musculo.POSTERIOR to listOf(
            "Alongue posterior com perna estendida, 30s",
            "Bom-dia leve sem carga, 10x",
        ),
        Musculo.GLUTEOS to listOf(
            "Alongamento 'figura 4' deitado, 30s cada",
            "Ponte de glúteo, 12 contrações",
        ),
        Musculo.PANTURRILHA to listOf(
            "Alongue a panturrilha na parede, 30s cada",
            "Elevação de panturrilha lenta, 15x",
        ),
    )

    /** Quote of the day. Same [epochDay] always yields the same quote. */
    fun doDia(epochDay: Long): Frase = TODAS[epochDay.mod(TODAS.size)]

    /**
     * A mobility tip for a rest day, drawn from the tips of [musculosAlvo].
     * Falls back to the full pool when no target muscle has tips.
     */
    fun descansoAtivo(musculosAlvo: List<Musculo>, epochDay: Long): String {
        val pool = musculosAlvo
            .flatMap { MOBILIDADE[it].orEmpty() }
            .ifEmpty { MOBILIDADE.values.flatten() }
        return if (pool.isEmpty()) {
            "Aproveite o descanso para respirar fundo e se hidratar."
        } else {
            pool[Random(epochDay).nextInt(pool.size)]
        }
    }
}
