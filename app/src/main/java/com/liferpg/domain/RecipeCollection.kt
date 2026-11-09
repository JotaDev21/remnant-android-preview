package com.liferpg.domain

/** Editorial plates, not a cartesian product or variants that only change portion size. */
internal object RecipeCollection {
    data class Entry(val id: String, val nome: String, val minutos: Int, val tags: List<String>, val itens: List<Pair<String, Int>>, val montagem: String)

    private fun entry(nome: String, itens: String, tags: List<String>, minutos: Int, montagem: String) = Entry(
        "colecao-" + normalizarAlimento(nome).replace(Regex(" +"), "-"), nome, minutos, tags,
        itens.split(' ').map { value -> value.split(':').let { it[0] to it[1].toInt() } }, montagem)
    private fun cafe(nome: String, itens: String, montagem: String = "Monte em uma tigela, distribuindo a fruta por cima. Acrescente flocos ou castanhas apenas ao servir para manter a textura. Use os pesos indicados; mel, açúcar e coberturas não entram nesta porção.") =
        entry(nome, itens, listOf("Café da manhã", "Lanche", "Vegetariana"), 5, montagem)
    private fun sanduiche(nome: String, itens: String, vegetal: Boolean = false) = entry(nome, itens,
        listOf("Lanche", "Café da manhã", "Sanduíche") + if (vegetal) listOf("Vegetariana") else emptyList(), 8,
        "Se desejar, aqueça o pão numa frigideira seca. Distribua o recheio já preparado e pesado entre as fatias, sem encharcar o pão. Acrescente tomate ou folhas perto de servir. Não há maionese, manteiga ou óleo extra incluídos; se usar, registre no diário. Feche o sanduíche e corte ao meio.")
    private fun cuscuz(nome: String, itens: String, vegetal: Boolean = false) = entry(nome, itens,
        listOf("Almoço", "Jantar", "Cuscuz") + if (vegetal) listOf("Vegetariana") else emptyList(), 8,
        "Solte o cuscuz pronto com um garfo e pese a porção. Distribua os acompanhamentos já preparados por cima ou sirva ao lado. Para comer depois, armazene separadamente e aqueça a parte quente na hora de servir. Manteiga, leite e molhos não estão incluídos nesta versão.")
    private fun massa(nome: String, itens: String, vegetal: Boolean = false) = entry(nome, itens,
        listOf("Almoço", "Jantar", "Massas") + if (vegetal) listOf("Vegetariana") else emptyList(), 10,
        "Escorra a massa e misture os acompanhamentos já preparados. Caso precise de umidade, use um pouco da água do cozimento em vez de acrescentar óleo sem registrar. Pese a massa seca antes de cozinhar, como indicado nos ingredientes; a porção pronta absorve água e pesa mais. Sirva assim que montar.")
    private fun salada(nome: String, itens: String, vegetal: Boolean = false) = entry(nome, itens,
        listOf("Almoço", "Jantar", "Saladas") + if (vegetal) listOf("Vegetariana") else emptyList(), 10,
        "Espere os ingredientes cozidos esfriarem antes de misturar com os crus. Escorra e seque bem para a salada não ficar aguada. Monte numa tigela limpa e acrescente os ingredientes delicados por último. Guarde refrigerada e tempere na hora de servir; azeite e molhos são extras que mudam os macros.")
    private fun prato(nome: String, itens: String, vegetal: Boolean = false) = entry(nome, itens,
        listOf("Almoço", "Jantar") + if (vegetal) listOf("Vegetariana") else emptyList(), 10,
        "Monte os acompanhamentos já preparados e pesados no prato. Acrescente a proteína por último, depois de conferir o ponto. Sirva os componentes quentes logo após o preparo; folhas e tomate entram na hora de comer. Temperos podem ser ajustados a gosto, mas registre óleo, manteiga e molhos adicionados.")

    val entries = listOf(
        // 18 cafés e lanches: fruit, dairy, oats and overnight preparations.
        cafe("Iogurte com morango e aveia", "449:170 239:120 7:25"),
        cafe("Iogurte com manga e castanha-do-pará", "449:170 229:120 589:10"),
        cafe("Banana com iogurte e amendoim", "449:170 179:100 558:10"),
        cafe("Tigela de maçã, iogurte e aveia", "448:170 222:130 7:30"),
        cafe("Iogurte com abacaxi e aveia", "449:170 164:120 7:25"),
        cafe("Creme de abacate e banana", "449:170 163:60 179:60", "Amasse a banana e o abacate com um garfo e incorpore o iogurte até ficar cremoso. Para outra textura, bata rapidamente no liquidificador limpo. Sirva imediatamente; não há açúcar nem leite extra nesta versão."),
        cafe("Aveia de geladeira com morango", "449:170 7:35 239:100", "Misture iogurte e aveia num pote limpo com tampa. Refrigere por pelo menos 2 horas ou durante a noite. Acrescente os morangos higienizados e cortados ao servir. O descanso na geladeira é além dos minutos de montagem."),
        cafe("Aveia de geladeira com manga", "449:170 7:35 229:100", "Misture iogurte e aveia, tampe e refrigere por pelo menos 2 horas. Corte a manga em cubos e acrescente na hora de servir. Mexa para incorporar a aveia hidratada; não há mel nem coco nesta versão."),
        cafe("Aveia de geladeira com maçã", "449:170 7:35 222:100", "Misture iogurte e aveia, tampe e refrigere por pelo menos 2 horas. Higienize e corte a maçã com casca apenas ao servir. Misture os cubos para combinar cremosidade e crocância."),
        cafe("Aveia de geladeira com mamão", "449:170 7:35 226:100", "Misture iogurte e aveia e refrigere em pote tampado por pelo menos 2 horas. Acrescente os cubos de mamão sem casca e sementes ao servir. Não conte o descanso como tempo de montagem."),
        cafe("Pote de mamão, morango e iogurte", "448:170 226:100 239:100"),
        cafe("Salada de frutas com iogurte", "179:80 226:80 239:80 448:100", "Corte as frutas em pedaços parecidos e misture delicadamente. Acrescente o iogurte na hora de servir. Para levar, mantenha refrigerado em pote limpo com tampa; não há suco nem leite condensado nesta receita."),
        cafe("Ricota com manga e aveia", "469:60 229:150 7:15", "Amasse a ricota com um garfo, misture parte da manga picada e finalize com o restante e a aveia. A fruta madura traz doçura; não há açúcar adicionado nesta versão."),
        cafe("Ricota com maçã e castanha-de-caju", "469:60 222:100 588:10", "Corte a maçã em cubos pequenos, esfarele a ricota e misture. Quebre as castanhas e acrescente por último para manter a crocância."),
        cafe("Queijo minas com banana e aveia", "461:60 179:100 7:20", "Corte o queijo em cubos e a banana em rodelas. Distribua numa tigela e finalize com a aveia. Sirva sem açúcar ou mel; adições alteram os macros."),
        cafe("Cuscuz com queijo minas", "533:120 461:40", "Solte o cuscuz pronto com um garfo e junte o queijo em cubos. Se quiser amolecer o queijo, aqueça brevemente os dois numa frigideira antiaderente com uma colher de água. Não há manteiga incluída."),
        cafe("Cuscuz com ricota e tomate", "533:120 469:60 157:60", "Sirva o cuscuz aquecido, distribua a ricota esfarelada e acrescente o tomate fresco ao servir. Aqueça o cuscuz com um pouco de água se estiver ressecado."),
        cafe("Cuscuz com ovos e tomate", "533:120 488:100 157:60", "Coloque o cuscuz quente no prato e distribua os ovos cozidos em quartos. Sirva o tomate separado para não encharcar o cuscuz. Esta versão usa ovos cozidos, não ovos fritos."),
        // 12 sandwiches with distinct fillings.
        sanduiche("Sanduíche integral de frango", "52:60 408:100 157:60"),
        sanduiche("Sanduíche integral de atum", "52:60 277:80 157:60"),
        sanduiche("Sanduíche integral de ovos", "52:60 488:100 157:60", true),
        sanduiche("Sanduíche de ricota e tomate", "52:60 469:70 157:60", true),
        sanduiche("Sanduíche de minas com pepino", "52:60 461:50 142:60", true),
        sanduiche("Sanduíche de frango com milho", "52:60 408:100 45:40"),
        sanduiche("Sanduíche de frango com cenoura", "52:60 408:100 109:50"),
        sanduiche("Sanduíche de ovos com ricota", "52:60 488:80 469:40", true),
        sanduiche("Sanduíche de atum com ervilhas", "52:60 277:80 560:50"),
        sanduiche("Sanduíche de minas com abacate", "52:60 461:50 163:50", true),
        sanduiche("Sanduíche de ricota com banana", "52:60 469:60 179:80", true),
        sanduiche("Sanduíche de frango com repolho", "52:60 408:100 149:50"),
        // 12 savory couscous plates.
        cuscuz("Cuscuz com frango e cenoura", "533:150 408:100 109:80"),
        cuscuz("Cuscuz com frango e brócolis", "533:150 410:100 100:80"),
        cuscuz("Cuscuz com frango e milho", "533:150 408:100 45:50"),
        cuscuz("Cuscuz com frango e abobrinha", "533:150 410:100 70:100"),
        cuscuz("Cuscuz com feijão e ovos", "533:120 561:100 488:100", true),
        cuscuz("Cuscuz com lentilha e abóbora", "533:120 577:120 64:100", true),
        cuscuz("Cuscuz com ovos e brócolis", "533:150 488:100 100:100", true),
        cuscuz("Cuscuz com ovos, milho e tomate", "533:120 488:100 45:40 157:60", true),
        cuscuz("Cuscuz com patinho e tomate", "533:150 377:100 157:100"),
        cuscuz("Cuscuz com carne moída e cenoura", "533:150 326:100 109:80"),
        cuscuz("Cuscuz com sardinha e tomate", "533:150 318:100 157:100"),
        cuscuz("Cuscuz com sardinha e brócolis", "533:150 318:100 100:100"),
        // 10 pastas, with dry pasta weights explicitly retained.
        massa("Macarrão com frango e brócolis", "40:70 408:100 100:100"),
        massa("Macarrão com frango e abobrinha", "40:70 410:100 70:100"),
        massa("Macarrão com frango e cenoura", "40:70 408:100 109:80"),
        massa("Macarrão com atum e tomate", "40:70 277:80 157:100"),
        massa("Macarrão com atum e ervilhas", "40:70 277:80 560:60"),
        massa("Macarrão com patinho e couve-flor", "40:70 377:100 118:100"),
        massa("Macarrão com carne moída e tomate", "40:70 326:100 157:100"),
        massa("Macarrão com ricota e brócolis", "40:70 469:80 100:120", true),
        massa("Macarrão com lentilha e abobrinha", "40:70 577:120 70:100", true),
        massa("Macarrão com ovos e tomate", "40:70 488:100 157:100", true),
        // 12 salads, including plant protein and cooked components.
        salada("Salada de frango com batata", "408:100 91:150 157:80 77:40"),
        salada("Salada de frango com lentilha", "408:100 577:120 142:80 77:40"),
        salada("Salada de atum com milho", "277:80 45:70 157:100 77:40"),
        salada("Salada de atum com arroz integral", "277:80 1:120 142:80 157:80"),
        salada("Salada de ovos com batata", "488:100 91:150 157:80 77:40", true),
        salada("Salada de ovos com beterraba", "488:100 97:120 142:80 77:40", true),
        salada("Salada de lentilha com ricota", "577:140 469:60 157:80 142:80", true),
        salada("Salada de feijão com milho", "561:150 45:60 157:100 142:60", true),
        salada("Salada de tofu com tomate", "584:150 157:100 142:80 77:50", true),
        salada("Salada de tofu com lentilha", "584:120 577:120 97:100 142:80", true),
        salada("Salada de minas com milho e pepino", "461:60 45:60 142:100 157:80", true),
        salada("Salada de salmão com batata", "317:100 91:140 157:80 77:40"),
        // 20 main dishes: multiple proteins, grains and vegetables.
        prato("Frango com arroz integral e feijão", "410:120 1:120 561:90 157:80"),
        prato("Frango com cuscuz e abóbora", "410:120 533:120 64:150"),
        prato("Frango com lentilha e couve-flor", "410:120 577:140 118:100"),
        prato("Frango com mandioca e brócolis", "410:120 129:150 100:100"),
        prato("Frango com batata e abobrinha", "410:120 91:160 70:120"),
        prato("Salmão com arroz e brócolis", "317:120 3:120 100:100"),
        prato("Salmão com batata-doce e cenoura", "317:120 88:150 109:100"),
        prato("Salmão com arroz integral e abobrinha", "317:120 1:120 70:120"),
        prato("Merluza com arroz e tomate", "301:120 3:120 157:100"),
        prato("Merluza com batata e brócolis", "301:120 91:160 100:100"),
        prato("Patinho com arroz integral e feijão", "377:100 1:120 561:100"),
        prato("Patinho com batata-doce e tomate", "377:100 88:160 157:100"),
        prato("Patinho com arroz e abobrinha", "377:100 3:120 70:120"),
        prato("Carne moída com arroz e feijão", "326:100 3:120 561:100"),
        prato("Carne moída com batata e brócolis", "326:100 91:160 100:100"),
        prato("Carne moída com abóbora e arroz", "326:100 64:150 3:120"),
        prato("Tofu com arroz e lentilha", "584:150 3:100 577:120", true),
        prato("Tofu com batata-doce e brócolis", "584:150 88:180 100:100", true),
        prato("Ovos com arroz integral e feijão", "488:100 1:120 561:100", true),
        prato("Lentilha com cuscuz e brócolis", "577:160 533:120 100:100", true)
    )
}
