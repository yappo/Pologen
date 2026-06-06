package jp.yappo.pologen.domain.support

import org.apache.commons.text.StringEscapeUtils

fun sanitizeForOgp(text: String, limit: Int = 100): String {
    val normalized = StringEscapeUtils.unescapeHtml4(text)
        .replace("\n", " ")
        .trim()
    var count = 0
    val builder = StringBuilder()
    normalized.codePoints().forEachOrdered { cp ->
        if (count < limit) {
            builder.appendCodePoint(cp)
            count++
        }
    }
    val originalCount = normalized.codePoints().count()
    return if (originalCount > limit) builder.append("…").toString() else builder.toString()
}

fun truncateSummary(text: String, limit: Int = 100): String = sanitizeForOgp(text, limit)

fun stripHtml(html: String): String = html.replace(Regex("<[^>]*>"), "").trim()
