package com.liferpg.domain

import com.liferpg.data.net.BrazilianFoods

/** Original assembly ideas. All weights describe the edible food in the named preparation. */
object RecipeBook {
    fun criar(tabela: BrazilianFoods): List<Receita> {
        fun receita(id: String, nome: String, minutos: Int, tags: List<String>, itens: List<Pair<String, Int>>, passos: String): Receita {
            val alimentos = itens.map { (idAlimento, gramas) -> requireNotNull(tabela.obter("taco:$idAlimento")) to gramas }
            val total = alimentos.fold(Nutrientes()) { n, (f, g) ->
                val escala = g / 100f
                n + Nutrientes(f.calorias * escala, f.proteinaG * escala, f.carboG * escala, f.gorduraG * escala)
            }
            return RecipeTutorials.completar(Receita("rem:$id", nome, null, passos,
                alimentos.map { (f, g) -> "$g g · ${f.nome}" }, minutos, total, tags, "REM · estimativa TACO/NEPA-Unicamp"), itens, passos)
        }
        val originais = listOf(
            receita("frango-arroz", "Frango, arroz e feijão", 10, listOf("Almoço", "Jantar"), listOf("410" to 100, "3" to 120, "561" to 90, "100" to 70),
                "1. Grelhe o frango sem óleo adicionado em uma frigideira antiaderente até ficar completamente cozido.\n2. Cozinhe arroz, feijão e brócolis separadamente.\n3. Pese as porções depois de prontas e monte o prato. A montagem pode usar alimentos já preparados."),
            receita("frango-batata-doce", "Frango com batata-doce", 8, listOf("Almoço", "Jantar"), listOf("410" to 120, "88" to 180, "109" to 80),
                "1. Prepare o frango grelhado e cozinhe batata-doce e cenoura até ficarem macias.\n2. Pese tudo já pronto, corte em pedaços e aqueça se necessário.\n3. Monte o prato; não há óleo extra incluído na estimativa."),
            receita("patinho-mandioca", "Patinho com mandioca", 10, listOf("Almoço", "Jantar"), listOf("377" to 100, "129" to 140, "157" to 100),
                "1. Cozinhe a mandioca descascada até ficar macia; descarte a água e a fibra central.\n2. Grelhe o patinho sem gordura aparente em frigideira antiaderente.\n3. Pese a carne e a mandioca prontas e sirva com tomate lavado e cortado."),
            receita("ovos-arroz", "Tigela de ovos e abóbora", 8, listOf("Almoço", "Jantar", "Vegetariana"), listOf("488" to 100, "3" to 100, "64" to 120),
                "1. Cozinhe os ovos até clara e gema ficarem firmes, esfrie e descasque.\n2. Cozinhe a abóbora e o arroz.\n3. Pese os ingredientes já preparados, corte os ovos e monte a tigela."),
            receita("lentilha-abobora", "Arroz com lentilha e abóbora", 8, listOf("Almoço", "Jantar", "Vegetal"), listOf("577" to 160, "64" to 150, "3" to 100),
                "1. Cozinhe lentilha, arroz e abóbora separadamente.\n2. Pese as porções prontas e aqueça com um pouco de água, se necessário.\n3. Monte a tigela. Óleo ou molho adicionados devem ser registrados à parte."),
            receita("feijao-ovos", "Arroz, feijão e ovos", 8, listOf("Almoço", "Jantar", "Vegetariana"), listOf("3" to 120, "561" to 120, "488" to 100, "157" to 80),
                "1. Cozinhe os ovos até ficarem firmes e retire as cascas.\n2. Aqueça arroz e feijão já preparados.\n3. Pese as porções prontas e sirva com tomate lavado e fatiado."),
            receita("sardinha-batata", "Sardinha com batata e brócolis", 10, listOf("Almoço", "Jantar"), listOf("318" to 100, "91" to 180, "100" to 100),
                "1. Asse as sardinhas até ficarem completamente cozidas; retire partes não comestíveis.\n2. Cozinhe batata e brócolis.\n3. Pese a parte comestível já pronta e monte o prato. A referência usada é sardinha assada, não conserva."),
            receita("frango-lentilha", "Bowl de frango e lentilha", 8, listOf("Almoço", "Jantar"), listOf("408" to 100, "577" to 140, "157" to 100),
                "1. Cozinhe o peito de frango sem pele até ficar completamente cozido e desfie.\n2. Cozinhe a lentilha até ficar macia.\n3. Pese ambos já prontos e misture com tomate lavado e picado."),
            receita("iogurte-banana", "Iogurte com banana e aveia", 5, listOf("Café da manhã", "Lanche", "Vegetariana"), listOf("449" to 170, "179" to 100, "7" to 30),
                "1. Coloque o iogurte natural desnatado na tigela.\n2. Descasque a banana, pese a polpa e corte em rodelas.\n3. Acrescente a aveia em flocos e misture. Açúcar, mel e coberturas não estão incluídos."),
            receita("iogurte-mamao", "Tigela de iogurte e mamão", 5, listOf("Café da manhã", "Lanche", "Vegetariana"), listOf("448" to 170, "226" to 150, "7" to 25),
                "1. Retire casca e sementes do mamão e pese a polpa.\n2. Misture com iogurte natural e aveia.\n3. Sirva em seguida; mantenha o iogurte refrigerado até a montagem."),
            receita("aveia-geladeira", "Aveia de geladeira com banana", 5, listOf("Café da manhã", "Lanche", "Vegetariana"), listOf("449" to 170, "7" to 40, "179" to 80),
                "1. Misture o iogurte natural desnatado e a aveia em um recipiente limpo com tampa.\n2. Deixe na geladeira por pelo menos duas horas; esse tempo não entra nos cinco minutos de montagem.\n3. Acrescente a banana descascada e pesada na hora de servir."),
            receita("atum-batata", "Salada de atum e batata", 8, listOf("Almoço", "Jantar"), listOf("277" to 80, "91" to 150, "157" to 100),
                "1. Cozinhe a batata, esfrie e pese a porção pronta.\n2. Lave e pique o tomate.\n3. Misture com atum em conserva em óleo. A estimativa usa essa conserva; se usar atum em água ou outra marca, confira os valores no rótulo."),
            receita("minas-mamao", "Queijo minas com mamão", 5, listOf("Café da manhã", "Lanche", "Vegetariana"), listOf("461" to 60, "226" to 180),
                "1. Corte o queijo minas frescal em cubos.\n2. Descasque o mamão, retire sementes e pese a polpa.\n3. Sirva lado a lado. A estimativa não inclui pão, açúcar ou outras coberturas."),
            receita("ovos-minas", "Salada de ovos com queijo minas", 8, listOf("Lanche", "Jantar", "Vegetariana"), listOf("488" to 100, "461" to 40, "157" to 100),
                "1. Cozinhe os ovos até ficarem firmes, esfrie e descasque.\n2. Pese ovos, queijo minas frescal e tomate lavado.\n3. Corte e misture. Registre à parte qualquer molho ou azeite adicionado."),
            receita("frango-abobora", "Frango com arroz e abóbora", 8, listOf("Almoço", "Jantar"), listOf("410" to 120, "3" to 100, "64" to 150),
                "1. Grelhe o peito de frango sem pele até ficar completamente cozido.\n2. Cozinhe arroz e abóbora.\n3. Pese tudo já pronto e monte o prato; a estimativa considera preparo sem óleo extra."),
            receita("lentilha-legumes", "Lentilha com cenoura e brócolis", 8, listOf("Almoço", "Jantar", "Vegetal"), listOf("577" to 180, "109" to 100, "100" to 100),
                "1. Cozinhe a lentilha e os legumes até ficarem macios.\n2. Pese as porções prontas e misture em uma tigela.\n3. Sirva aquecido. Você pode complementar a refeição conforme sua rotina e registrar os itens extras.")
        )
        return originais + RecipeCollection.entries.map { e -> receita(e.id, e.nome, e.minutos, e.tags, e.itens, e.montagem) }
    }

    fun filtrar(receitas: List<Receita>, termo: String, filtro: String): List<Receita> {
        val palavras = normalizarAlimento(termo).split(' ').filter { it.isNotBlank() }
        return receitas.filter { r ->
            val texto = normalizarAlimento((listOf(r.nome) + r.tags + r.ingredientes).joinToString(" "))
            palavras.all { it in texto } && when (filtro) {
                "proteina" -> (r.nutrientes?.proteina ?: 0f) >= 20f
                "vegetal" -> "Vegetal" in r.tags || "Vegetariana" in r.tags
                "rapida" -> r.minutosMontagem in 1..5
                "cafe" -> "Café da manhã" in r.tags
                "lanche" -> "Lanche" in r.tags
                "refeicao" -> "Almoço" in r.tags || "Jantar" in r.tags
                "massas" -> "Massas" in r.tags
                "saladas" -> "Saladas" in r.tags
                "cuscuz" -> "Cuscuz" in r.tags || "cuscuz" in normalizarAlimento(r.nome)
                else -> true
            }
        }
    }

    fun recomendar(receitas: List<Receita>, restantes: Nutrientes): List<Receita> = when {
        restantes.proteina > 20f -> receitas.sortedByDescending { it.nutrientes?.proteina ?: 0f }
        restantes.carbo > 40f -> receitas.sortedByDescending { it.nutrientes?.carbo ?: 0f }
        else -> receitas
    }
}
