package com.liferpg.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import com.liferpg.ui.theme.*
import java.text.Normalizer

internal data class MentorTextLine(val text: String, val heading: Boolean = false)

private val HORIZONTAL_RULE = Regex("[-*_]{3,}")
private val HEADING = Regex("#{1,6}\\s+.*")
private val HEADING_MARK = Regex("^#{1,6}\\s+")
private val LINK = Regex("!?(\\[)([^]\\n]+)]\\(([^)]+)\\)")
private val BULLET = Regex("^[-*+]\\s+")
private val BLOCKQUOTE = Regex("^>\\s?")
private val TABLE_SEPARATOR = Regex("^\\|?[\\s:|-]+\\|?$")
private val HTML_TAG = Regex("</?[a-zA-Z][^>]*>")
private val INVISIBLE = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200F\\u2028\\u2029\\uFEFF]")
/** Lone emphasis markers left by a model ("**", "__", "~~") that would otherwise render raw. */
private val STRAY_MARKER = Regex("(?<![\\p{L}\\p{N}])[*_~]{1,3}(?![\\p{L}\\p{N}])")
private val TOKENS = Regex("(\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|(?<!\\w)\\*([^*]+)\\*(?!\\w)|~~(.+?)~~)")

/**
 * Turns a model reply into plain lines the brutal UI can draw: strips headings, rules, code fences,
 * links, blockquotes, HTML and tables (rows become "cell · cell"), and drops invisible/control chars.
 */
internal fun mentorTextLines(source: String): List<MentorTextLine> =
    Normalizer.normalize(source, Normalizer.Form.NFKC).replace("\r\n", "\n").replace(INVISIBLE, "").lines().mapNotNull { raw ->
        var line = raw.trim()
        if (line.matches(HORIZONTAL_RULE) || line.startsWith("```") || line.matches(TABLE_SEPARATOR) && line.contains('|')) return@mapNotNull null
        val heading = line.matches(HEADING)
        line = line.replace(HEADING_MARK, "")
            .replace(BLOCKQUOTE, "")
            .replace(HTML_TAG, "")
            .replace(LINK, "$2")
            .replace(BULLET, "• ")
        if (line.startsWith("|") && line.endsWith("|") && line.length > 1) {
            line = line.trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
        }
        MentorTextLine(line, heading)
    }

internal fun mentorPlainText(source: String) = mentorTextLines(source).joinToString("\n") {
    mentorAnnotated(it.text).text
}

internal fun mentorAnnotated(source: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    TOKENS.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first).replace(STRAY_MARKER, ""))
        val bold = match.groupValues[2].ifEmpty { match.groupValues[3] }
        val struck = match.groupValues[6]
        withStyle(
            SpanStyle(
                fontWeight = if (bold.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (match.groupValues[5].isNotEmpty()) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (struck.isNotEmpty()) TextDecoration.LineThrough else null,
            ),
        ) {
            append(bold.ifEmpty { match.groupValues[4].ifEmpty { match.groupValues[5].ifEmpty { struck } } })
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor).replace(STRAY_MARKER, ""))
}

@Composable internal fun MentorRichText(source: String) {
    val lines = remember(source) { mentorTextLines(source) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { line ->
            if (line.text.isBlank()) Spacer(Modifier.height(5.dp))
            else Text(
                remember(line.text) { mentorAnnotated(line.text) },
                fontFamily = FontFamily.SansSerif, fontSize = if (line.heading) 17.sp else 15.sp,
                fontWeight = if (line.heading) FontWeight.Bold else FontWeight.Normal,
                color = TextoPrimario, lineHeight = 24.sp,
            )
        }
    }
}
