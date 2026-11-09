package com.liferpg.domain

/** Original guides for REM plates. Portions remain the cooked/edible TACO weights. */
internal object RecipeTutorials {
    private val arrozVideo = VideoReceita("Arroz branco soltinho", "https://www.youtube.com/watch?v=9o4YU1m1KTs", "Panelinha · Rita Lobo", true)
    private val frangoVideo = VideoReceita("Grelhar frango sem ressecar", "https://www.youtube.com/watch?v=tv8ffdWSUiA", "Panelinha · Rita Lobo", true)
    private val bifeVideo = VideoReceita("Técnica do bife grelhado", "https://www.youtube.com/watch?v=IFMpL2h7hMM", "Panelinha · Rita Lobo", true)
    private val ovosVideo = VideoReceita("Ovos: como acertar o ponto", "https://www.youtube.com/watch?v=t7FK8XQ-geU", "Tastemade Brasil", true)
    private val lentilhaVideo = VideoReceita("Lentilha: alternativa na pressão", "https://www.youtube.com/watch?v=KvLCXjsTESM", "Panelinha · Rita Lobo", true)
    private val tomateVideo = VideoReceita("Tomate: cortes para cada preparo", "https://www.youtube.com/watch?v=ui4x8SwgeDQ", "Panelinha · Rita Lobo", true)
    private val legumesVideo = VideoReceita("Brócolis e seus preparos", "https://www.youtube.com/watch?v=m4Hbn6hX80U", "Panelinha · Rita Lobo", true)
    private val aveiaVideo = VideoReceita("Montagem de aveia de geladeira", "https://www.youtube.com/watch?v=H1nWzcFgufk", "Receitas Nestlé", true)
    private val frutasVideo = VideoReceita("Montagem de frutas com iogurte", "https://www.youtube.com/watch?v=3-NqxC2wd9k", "Receitas Nestlé", true)

    private fun passo(titulo: String, descricao: String, ponto: String? = null) = EtapaReceita(titulo, descricao, ponto)
    private fun fruta(nome: String, corte: String) = passo("Prepare $nome",
        "Lave a parte externa antes de cortar e use uma tábua e faca limpas. $corte Pese apenas a parte comestível indicada. Monte perto da hora de servir ou mantenha refrigerado em recipiente limpo com tampa.")
    private fun cru(nome: String, corte: String) = passo("Prepare $nome",
        "Lave em água corrente e higienize com produto próprio para alimentos, seguindo a diluição e o tempo indicados no rótulo. $corte Pese no estado cru indicado, antes de misturar. Seque bem para não encharcar a montagem e mantenha utensílios separados de carnes cruas.")
    private fun legume(nome: String, corte: String) = passo("Cozinhe $nome",
        "Lave e $corte. Coloque numa panela com água fervente e cozinhe em fervura suave, conferindo o centro com um garfo. Retire quando ficar macio, escorra e pese no estado cozido indicado. O corte e o tamanho mudam o tempo; não cozinhe até desmanchar.", "Centro macio, pedaços ainda inteiros. Escorra antes de pesar.")

    private val preparos = mapOf(
        "3" to passo("Arroz: do grão ao acompanhamento",
            "Para preparar uma pequena panela, use 1 medida de arroz branco e 2 medidas de água. Leve água e arroz ao fogo; ao ferver, abaixe o fogo e deixe a tampa entreaberta. Não fique mexendo. Se secar antes de amaciar, acrescente um pouco de água quente. Desligue quando os grãos estiverem macios, tampe por 5 minutos e solte com um garfo. Separe o peso cozido indicado nos ingredientes; o restante pode servir a outras refeições.",
            "Grãos macios, sem água acumulada no fundo. A proporção pode variar com o arroz: confira a embalagem."),
        "561" to passo("Feijão: planeje antes de começar",
            "Selecione o feijão, lave e deixe de molho em água na geladeira por 8–12 horas. Descarte essa água. Em panela comum, cubra os grãos com água nova, leve à fervura e cozinhe em fogo baixo, com tampa entreaberta, até amaciarem. Verifique a água durante o preparo e reponha água quente quando necessário. Pode levar 1–2 horas conforme o grão. Tempere no final e pese a porção cozida com caldo. Se já tiver feijão pronto, basta reaquecer bem.",
            "Um grão deve se desfazer facilmente ao ser apertado. Não sirva feijão ainda duro ou cru."),
        "577" to passo("Lentilha macia sem virar purê",
            "Retire impurezas, lave a lentilha em uma peneira e coloque numa panela comum com água suficiente para cobrir e sobrar cerca de 3 cm. Quando ferver, reduza o fogo. Cozinhe por cerca de 15–30 minutos, provando o ponto e repondo água quente se secar. Escorra o excesso; pese os grãos já cozidos para montar a porção.",
            "Grãos macios ao morder, mas ainda inteiros. O tempo varia com a lentilha."),
        "410" to passo("Frango grelhado: controle o calor",
            "Use peito sem pele, descongelado na geladeira, em filés de espessura parecida. Seque com papel descartável; não lave o frango cru. Aqueça uma frigideira antiaderente em fogo médio e disponha os filés sem empilhar. Vire quando dourarem e reduza o fogo para cozinhar o centro. Se a superfície ameaçar queimar, abaixe o fogo e acrescente uma colher de água. Pese apenas a carne pronta.",
            "O centro deve atingir 74 °C, medidos com termômetro culinário na parte mais espessa. Cor e tempo sozinhos não garantem segurança."),
        "408" to passo("Frango cozido para desfiar",
            "Coloque o peito sem pele e descongelado numa panela comum; cubra com água. Leve à fervura, abaixe o fogo e cozinhe com a tampa entreaberta. Não use a mesma tábua ou faca do frango cru para ingredientes prontos. Depois de cozido, retire com uma pinça limpa, desfie com dois garfos e pese a carne, sem contar o caldo.",
            "Confira 74 °C no centro com termômetro culinário antes de desfiar."),
        "377" to passo("Patinho grelhado, sem endurecer",
            "Use bifes de patinho sem gordura aparente, em espessuras semelhantes. Seque a superfície, tempere e coloque numa frigideira antiaderente aquecida, sem sobrepor. Vire com uma pinça ou espátula, sem furar nem apertar a carne. Reduza o fogo se estiver tostando antes de cozinhar por dentro. Deixe descansar antes de fatiar e pese já grelhado.",
            "Para bife inteiro, confira pelo menos 63 °C no centro e espere 3 minutos antes de cortar. Carne moída exige 71 °C e não usa esta referência de bife."),
        "318" to passo("Sardinha assada: atenção às espinhas",
            "Use sardinhas limpas e descongeladas na geladeira. Aqueça o forno a 200 °C, acomode os peixes numa assadeira com papel próprio para forno e asse sem óleo extra até cozinharem por dentro. Verifique durante o preparo; tamanho e forno mudam o tempo. Retire espinhas e partes não comestíveis antes de pesar a porção assada.",
            "Confira 63 °C na parte mais espessa. A receita usa sardinha assada, não sardinha em conserva."),
        "488" to passo("Ovos bem cozidos e fáceis de descascar",
            "Com uma colher, coloque os ovos delicadamente numa panela de água fervente e mantenha uma fervura suave. Para gema firme, use cerca de 10–12 minutos como referência, ajustando ao tamanho do ovo. Transfira para água fria, espere esfriar e descasque. Pese o ovo pronto sem casca; quantidade de ovos e peso variam.",
            "Clara e gema firmes. Não use gema crua ou líquida neste preparo."),
        "88" to passo("Batata-doce por igual",
            "Lave, descasque e corte a batata-doce em pedaços semelhantes. Cubra com água em uma panela, leve à fervura e cozinhe em fogo médio até amaciar. Comece a conferir depois de 15 minutos; pedaços maiores demoram mais. Escorra antes de pesar a porção cozida.",
            "O garfo atravessa o centro sem resistência; retire antes de desmanchar."),
        "91" to passo("Batata cozida sem encharcar",
            "Lave, descasque e corte a batata em cubos de tamanho parecido. Coloque numa panela com água fria cobrindo os cubos e leve ao fogo. Após ferver, cozinhe em fogo médio e confira a maciez a partir de 10 minutos. Escorra e deixe o vapor sair. Para salada, espere esfriar antes de misturar os ingredientes frios.",
            "Centro macio e cubos ainda inteiros. O peso informado é depois de cozinhar e escorrer."),
        "129" to passo("Mandioca completamente cozida",
            "Use mandioca de mesa própria para consumo, descascada e limpa. Corte em pedaços, cubra com água numa panela comum e leve à fervura. Cozinhe até ficar totalmente macia; pode levar 20–40 minutos ou mais. Descarte a água do cozimento, retire a fibra central e pese apenas a parte comestível cozida.",
            "A mandioca abre e cede facilmente ao garfo. Nunca sirva crua ou parcialmente cozida."),
        "100" to passo("Brócolis sem perder a textura",
            "Lave o brócolis e divida em floretes de tamanho parecido. Coloque numa panela com água fervente e cozinhe por cerca de 3–5 minutos; teste um talo. Escorra imediatamente quando amaciar. Se os talos forem muito grossos, corte-os menores e coloque primeiro.",
            "Talos macios ao garfo, floretes ainda firmes. Pese depois de escorrer."),
        "109" to passo("Cenoura: tamanho do corte importa",
            "Lave, descasque e corte em rodelas ou cubos semelhantes. Cubra com água e cozinhe em fervura suave. Confira o ponto depois de 8–10 minutos; continue até o centro amaciar. Escorra antes de pesar e montar o prato.",
            "Garfo atravessa a cenoura sem precisar fazer força."),
        "64" to passo("Abóbora cabotiá em cubos",
            "Com a abóbora apoiada numa tábua estável, retire sementes, casca e corte em cubos. Se estiver difícil cortar, compre já cortada em vez de forçar a faca. Cozinhe em água fervente até os cubos ficarem macios, conferindo a partir de 10 minutos. Escorra com cuidado antes de pesar.",
            "Cubos macios e ainda identificáveis, sem água acumulada no prato."),
        "157" to passo("Tomate pronto para comer cru",
            "Lave o tomate em água corrente, retire partes danificadas e higienize com produto indicado para alimentos, seguindo a diluição e o tempo do rótulo. Corte com utensílios limpos. Pese com as sementes, como na referência dos ingredientes.",
            "Não use detergente para louça nem misture produtos de limpeza na higienização."),
        "179" to passo("Banana: pese somente a polpa",
            "Escolha banana nanica madura, descasque e pese apenas a parte comestível. Corte em rodelas para ter textura ou amasse com um garfo para adoçar naturalmente a mistura. Prepare perto da hora de servir para evitar que escureça."),
        "226" to passo("Mamão papaia, sem casca e sementes",
            "Lave a parte externa antes de cortar. Apoie numa tábua limpa, abra ao meio e retire as sementes com uma colher. Retire a casca, pese a polpa e corte em cubos pequenos. Evite a faca e a tábua usadas para carne crua."),
        "277" to passo("Atum: confira a conserva",
            "Escolha uma lata íntegra e dentro da validade. Abra com utensílio limpo e separe a porção de atum em conserva em óleo conforme o rótulo. Esta referência não corresponde a atum em água: tipo de conserva e drenagem mudam os macros. Depois de aberto, guarde o restante em recipiente adequado e siga o prazo do fabricante."),
        "461" to passo("Queijo minas frescal",
            "Mantenha refrigerado até o preparo e confira validade e aspecto. Com uma faca limpa, corte em cubos e pese. Use minas frescal: queijo padrão, ricota e versões light possuem composição diferente."),
        "448" to passo("Iogurte natural integral", "Mantenha o iogurte refrigerado até a montagem. Misture com uma colher limpa e pese numa tigela com a balança zerada. A referência usa natural integral, sem coberturas; confira o rótulo da sua marca."),
        "449" to passo("Iogurte natural desnatado", "Retire da geladeira apenas na hora de montar. Mexa com uma colher limpa e pese na tigela usando a função tara da balança. A referência usa natural desnatado; iogurte adoçado, integral ou proteico terá outros macros."),
        "7" to passo("Aveia na medida", "Pese os flocos secos antes de misturar. Nesta receita podem hidratar no iogurte ou servir como cobertura; não precisam de cozimento. Verifique no rótulo os ingredientes e possíveis alergênicos. Granola e aveia com açúcar não usam a mesma composição."),
        "1" to passo("Arroz integral com o grão macio", "Para uma pequena panela, use 1 medida de arroz integral e cerca de 3 medidas de água; confira a proporção da embalagem. Ao ferver, reduza o fogo e cozinhe com tampa entreaberta por cerca de 30–50 minutos, até amaciar. Reponha um pouco de água quente se secar cedo. Desligue, tampe por 5 minutos e solte os grãos. Pese a porção já cozida; guarde o excedente adequadamente.", "Grão macio, sem centro duro e sem água acumulada no fundo."),
        "40" to passo("Macarrão: pese seco antes de cozinhar", "O peso desta lista é de macarrão de trigo seco/cru. Pese antes de levar à panela. Ferva água suficiente para a massa se movimentar, acrescente a massa e mexa no começo para não grudar. Cozinhe conforme o tempo da embalagem e prove uma unidade para conferir o ponto. Escorra; reserve um pouco da água caso precise umedecer a montagem. Não adicione óleo sem registrar.", "Massa cozida, firme ao morder, sem centro cru. O peso aumenta com a água; não substitua os gramas secos pelos gramas cozidos."),
        "533" to passo("Cuscuz de milho na cuscuzeira", "Use flocos de milho próprios para cuscuz. Comece hidratando 1 medida de flocos com cerca de meia medida de água, ajustando à embalagem. Misture e deixe descansar por 10 minutos: deve ficar úmido, sem água sobrando. Coloque água na parte inferior da cuscuzeira, abaixo do cesto. Distribua os flocos sem apertar, tampe e cozinhe no vapor por cerca de 10–15 minutos após a água ferver. Solte com um garfo e pese já cozido.", "Cuscuz macio por inteiro, sem flocos secos. A água de baixo não deve encostar no cesto."),
        "52" to passo("Pão integral pronto para rechear", "Confira o rótulo, validade e peso das fatias: os gramas desta receita podem corresponder a mais ou menos fatias conforme a marca. Pese o pão pronto. Se quiser, aqueça numa frigideira seca até ficar levemente firme; não há manteiga incluída na estimativa."),
        "45" to passo("Milho de conserva, drenado", "Abra uma lata íntegra e dentro da validade com utensílio limpo. Escorra o líquido numa peneira e pese os grãos drenados. O peso da lata inteira, incluindo a água, não corresponde à porção indicada. Guarde o restante conforme o rótulo, em recipiente adequado."),
        "560" to passo("Ervilhas de conserva, drenadas", "Confira a validade e a integridade da lata. Depois de abrir, escorra a água e pese as ervilhas drenadas antes de misturar. Não confunda com ervilha seca ou ervilha fresca, que têm outro preparo e outra composição nutricional."),
        "326" to passo("Carne moída cozida, sem óleo extra", "Use acém moído, descongelado na geladeira. Coloque numa panela antiaderente aquecida e desfaça os blocos com uma espátula. Mexa para cozinhar por igual; reduza o fogo se estiver queimando e use um pouco de água quando necessário. Pese a carne já cozida conforme a lista.", "Carne moída exige pelo menos 71 °C, conferidos com termômetro culinário; não use só a cor para decidir."),
        "317" to passo("Salmão grelhado sem pele", "Use filé sem pele, descongelado na geladeira e seco com papel descartável. Aqueça uma frigideira antiaderente em fogo médio e coloque o peixe sem sobrepor. Vire cuidadosamente com espátula quando estiver firme; reduza o fogo para terminar o centro sem ressecar. Pese a porção grelhada, sem pele nem espinhas.", "Confira pelo menos 63 °C na parte mais espessa com termômetro culinário."),
        "301" to passo("Merluza assada", "Descongele o filé na geladeira, seque a superfície e procure espinhas. Aqueça o forno a 200 °C e coloque os filés separados numa assadeira com papel próprio para forno. Asse até cozinhar o centro; comece a conferir depois de cerca de 15 minutos e ajuste à espessura. Pese a parte comestível já assada, sem óleo extra incluído.", "Confira 63 °C no centro com termômetro culinário; o tempo do forno sozinho não garante o ponto."),
        "584" to passo("Tofu pronto para usar", "Escolha tofu próprio para consumo conforme o rótulo e mantenha refrigerado. Escorra o excesso de líquido da embalagem e corte em cubos com utensílios limpos. Pese antes de montar. Siga a orientação de consumo e conservação do fabricante; se aquecer, evite óleo extra não registrado."),
        "469" to passo("Ricota em cubos ou esfarelada", "Mantenha a ricota refrigerada e confira validade e instruções do fabricante. Pese, corte em cubos ou esfarele com um garfo limpo para distribuir pelo prato. Ricota cremosa com creme de leite e produtos light têm macros diferentes."),
        "70" to legume("abobrinha italiana", "retire as extremidades e corte em cubos ou meias-luas de tamanho parecido"),
        "97" to legume("beterraba", "descasque e corte em cubos semelhantes, usando tábua estável; cubos maiores demoram mais"),
        "118" to legume("couve-flor", "divida em floretes semelhantes e retire as partes muito duras do talo"),
        "77" to cru("alface americana", "Separe as folhas e retire partes danificadas. Seque antes de rasgar em pedaços."),
        "142" to cru("pepino", "Retire as extremidades e corte em rodelas ou cubos. A porção considera a parte comestível crua."),
        "149" to cru("repolho branco", "Retire folhas externas danificadas e o talo muito duro; corte em tiras finas."),
        "239" to cru("morango", "Retire os cabinhos depois de higienizar. Corte ao meio ou em quartos."),
        "222" to cru("maçã Fuji", "Retire o miolo e as sementes e corte em cubos. Esta referência inclui a casca."),
        "229" to fruta("manga Palmer", "Retire a casca e corte a polpa ao redor do caroço em cubos; não pese o caroço."),
        "164" to fruta("abacaxi", "Retire a coroa, casca, olhos e partes muito duras do miolo. Corte a polpa em cubos."),
        "163" to fruta("abacate", "Abra com cuidado numa tábua, retire o caroço com uma colher e separe a polpa da casca. Corte ou amasse conforme a montagem."),
        "558" to passo("Amendoim torrado", "Pese o amendoim torrado sem casca. Use inteiro ou quebre em pedaços com uma faca apoiada numa tábua. A referência é torrado e salgado; pasta de amendoim, doce e versões com cobertura têm outros macros. Confira alergênicos no rótulo."),
        "588" to passo("Castanha-de-caju torrada", "Pese as castanhas sem casca e pique grosseiramente para distribuir melhor. A referência é torrada e salgada. Produtos caramelizados ou cobertos com chocolate não são equivalentes; confira também os alergênicos da embalagem."),
        "589" to passo("Castanha-do-pará", "Use castanhas próprias para consumo, sem casca e com armazenamento adequado. Pese e pique em pedaços pequenos; o tamanho varia, por isso use gramas em vez de supor a quantidade de unidades. Confira alergênicos na embalagem.")
    )

    private val finais = mapOf(
        "frango-arroz" to "Monte arroz e feijão lado a lado, acrescente os floretes e fatie o frango por último. Colocar a carne sobre o arroz ajuda a aproveitar seus sucos no prato.",
        "frango-batata-doce" to "Sirva o frango fatiado com os pedaços de batata-doce e cenoura. Mantenha os legumes inteiros; se preferir purê, amasse a batata sem acrescentar manteiga ou leite não registrados.",
        "patinho-mandioca" to "Fatie o bife contra o sentido das fibras e acomode junto da mandioca. Coloque o tomate separado da parte quente para manter a textura.",
        "ovos-arroz" to "Coloque arroz no fundo da tigela, distribua a abóbora e finalize com os ovos em quartos. Misture apenas na hora de comer, para preservar os pedaços.",
        "lentilha-abobora" to "Misture delicadamente lentilha e arroz e coloque a abóbora por cima. Não mexa vigorosamente: a abóbora macia pode virar purê.",
        "feijao-ovos" to "Monte arroz e feijão quentes e finalize com os ovos cortados. Acrescente o tomate na hora de servir, usando um utensílio limpo.",
        "sardinha-batata" to "Distribua batata e brócolis no prato e acrescente a sardinha já sem espinhas. Confira mais uma vez as partes que vai comer antes de servir.",
        "frango-lentilha" to "Misture o frango desfiado à lentilha escorrida. Junte o tomate picado apenas na hora de servir para que a tigela não fique aguada.",
        "iogurte-banana" to "Coloque a banana sobre o iogurte e finalize com os flocos de aveia. Misture para ficar cremoso ou deixe em camadas para manter a textura.",
        "iogurte-mamao" to "Distribua o mamão sobre o iogurte e acrescente a aveia por último. Sirva logo para os flocos continuarem firmes.",
        "aveia-geladeira" to "Misture iogurte e aveia num pote limpo com tampa. Deixe na geladeira por pelo menos 2 horas, ou durante a noite. Ao servir, mexa e acrescente a banana fresca. O descanso é além dos 5 minutos de montagem.",
        "atum-batata" to "Com a batata fria ou morna, junte o tomate e o atum e mexa delicadamente. Evite esmagar os cubos; não deixe a salada esperando fora da geladeira.",
        "minas-mamao" to "Sirva os cubos de queijo e mamão lado a lado ou misture com cuidado. Não há iogurte, mel ou cobertura nesta versão.",
        "ovos-minas" to "Corte os ovos já frios em pedaços, acrescente queijo e tomate e misture delicadamente. Sirva assim ou tempere com ervas; molhos e azeite precisam ser registrados à parte.",
        "frango-abobora" to "Coloque o arroz no prato, distribua a abóbora sem amassar e acrescente o frango fatiado. Monte enquanto os ingredientes quentes estiverem prontos para servir.",
        "lentilha-legumes" to "Misture a lentilha escorrida com cenoura e brócolis já prontos, mexendo pouco para não quebrar os floretes. Sirva aquecido ou espere esfriar e refrigere."
    )

    fun completar(receita: Receita, itens: List<Pair<String, Int>>, montagem: String): Receita {
        val ids = itens.map { it.first }.toSet()
        val quente = ids.any { it in setOf("1", "40", "533", "326", "317", "301", "70", "97", "118", "3", "561", "577", "410", "408", "377", "318", "488", "88", "91", "129", "100", "109", "64") }
        val utensils = buildList {
            addAll(listOf("Balança culinária", "Tábua, faca e colher", "Prato ou tigela"))
            if (quente) add("Panela comum e escorredor")
            if (ids.any { it in setOf("410", "377", "317", "326") }) add("Frigideira antiaderente e pinça")
            if (ids.any { it in setOf("318", "301") }) add("Forno e assadeira")
            if (ids.any { it in setOf("410", "408", "377", "318", "301", "317", "326") }) add("Termômetro culinário")
            if ("533" in ids) add("Cuscuzeira")
            if (receita.nome.contains("geladeira", true)) add("Pote com tampa e geladeira")
        }
        val ordem = listOf("561", "577", "1", "3", "533", "40", "129", "88", "91", "64", "97", "109", "100", "70", "118", "488", "318", "301", "377", "326", "408", "410", "317")
        val pesagem = if ("40" in ids) "Macarrão cru é pesado seco antes de cozinhar; ingredientes descritos como cozidos ou grelhados são pesados depois de prontos."
            else "Para ingredientes descritos como cozidos ou grelhados, prepare quantidade suficiente e pese depois de prontos; não substitua o peso pronto pelo peso cru."
        val etapas = listOf(passo("Antes de começar",
            "Separe os ingredientes e utensílios. Os gramas abaixo descrevem uma porção comestível, no estado indicado: cozido, grelhado ou cru. $pesagem Adiante os acompanhamentos e prepare a proteína perto de servir. Tempere a gosto, mas registre óleo, manteiga, molhos e coberturas se adicionar.")) +
            itens.sortedBy { ordem.indexOf(it.first).takeIf { index -> index >= 0 } ?: ordem.size }.map { preparos.getValue(it.first) } +
            listOf(passo("Confira sua porção", "Use a função tara da balança e respeite os gramas e o estado indicado de cada ingrediente. $pesagem Nas carnes, retire partes não comestíveis; nos cozidos e conservas, escorra conforme a etapa. Os macros são dessa porção, não do rendimento da panela inteira."),
                passo("Monte e sirva", finais[receita.id.removePrefix("rem:")] ?: montagem))
        val videos = buildList {
            if ("410" in ids) add(frangoVideo)
            if ("377" in ids) add(bifeVideo)
            if ("488" in ids) add(ovosVideo)
            if ("3" in ids) add(arrozVideo)
            if ("100" in ids) add(legumesVideo)
            if ("577" in ids) add(lentilhaVideo)
            if ("157" in ids) add(tomateVideo)
            if ("7" in ids) add(if (receita.nome.contains("geladeira", true)) aveiaVideo else frutasVideo)
            if (isEmpty() && ids.any { it in setOf("226", "179", "229", "239", "222", "164", "163") }) add(frutasVideo)
        }.distinctBy { it.url }
        return receita.copy(etapas = etapas, instrucoes = etapas.joinToString("\n\n") { "${it.titulo}\n${it.descricao}" },
            utensilios = utensils, videos = videos, idioma = "pt", dicas = listOf(
                if (quente) "Se já tiver os ingredientes prontos, comece pela pesagem e montagem. O tempo do card é de montagem, não inclui cozinhar, descongelar ou deixar de molho."
                else "Monte com ingredientes refrigerados quando indicado. Frutas maduras dão sabor sem precisar adicionar açúcar.",
                "Substituir um ingrediente muda os macros. Use o diário para informar o preparo e o peso reais; esta receita não é registrada automaticamente.",
                "Para adiantar refeições, separe ingredientes em potes limpos e refrigere prontamente a 4 °C ou menos. Siga o prazo de cada alimento e do fabricante; o ingrediente com menor validade limita o prato inteiro.",
                "Confira alergênicos nos rótulos: receitas com leite, ovos e peixe podem não ser adequadas para você. A lista do REM não substitui o rótulo."
            ))
    }
}
