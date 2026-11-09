package com.liferpg.domain

import java.net.URI

/** Preserve the source's wording and order; never synthesize missing cooking instructions. */
object RecipeInstructions {
    fun etapas(texto: String): List<EtapaReceita> {
        val normalized = texto.replace("\r\n", "\n").replace('\r', '\n').trim()
            .replace(Regex("(?im)^\\s*(?:step\\s*)?\\d+\\s*[.:)]?\\s*$"), "\n\n")
            .replace(Regex("(?im)^\\s*step\\s+\\d+\\s*[:.-]?\\s*"), "\n\n")
            .replace(Regex("(?m)(?:^|(?<=\\s))\\d+[.)]\\s+(?=[\\p{L}])"), "\n\n")
        return normalized.split(Regex("\\n\\s*\\n"))
            .map { it.trim().replace(Regex("\\n[ \\t]*"), "\n") }.filter { it.isNotBlank() }
            .mapIndexed { i, text -> EtapaReceita("Etapa ${i + 1}", text) }
    }

    fun https(url: String?): String? = url?.trim()?.takeIf { value ->
        runCatching { URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
    }

    fun youtube(url: String?): String? {
        val uri = https(url)?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        val id = when (uri.host.lowercase()) {
            "youtu.be" -> uri.path.removePrefix("/")
            "youtube.com", "www.youtube.com", "m.youtube.com" -> when {
                uri.path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                uri.path.startsWith("/embed/") -> uri.path.removePrefix("/embed/")
                else -> null
            }
            else -> null
        } ?: return null
        return if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) "https://www.youtube.com/watch?v=$id" else null
    }
}
