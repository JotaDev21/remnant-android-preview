package com.liferpg.domain

data class Receita(
    val id: String,
    val nome: String,
    val imagem: String?,
    val instrucoes: String,
    val ingredientes: List<String>,
    val minutosMontagem: Int = 0,
    val nutrientes: Nutrientes? = null,
    val tags: List<String> = emptyList(),
    val fonte: String = "TheMealDB",
    val etapas: List<EtapaReceita> = emptyList(),
    val utensilios: List<String> = emptyList(),
    val dicas: List<String> = emptyList(),
    val videos: List<VideoReceita> = emptyList(),
    val fonteUrl: String? = null,
    val idioma: String = "pt",
)

data class EtapaReceita(val titulo: String, val descricao: String, val ponto: String? = null)
data class VideoReceita(val titulo: String, val url: String, val autor: String, val tecnica: Boolean = false)
