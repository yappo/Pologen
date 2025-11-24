package jp.yappo.pologen.application.util

import org.apache.commons.text.StringEscapeUtils
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun convertToRssDateTimeFormat(dateTime: String, fromZoneId: ZoneId, toZoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val localDateTime = LocalDateTime.parse(dateTime, formatter)
    val localZonedDateTime = localDateTime.atZone(fromZoneId)
    val gmtZonedDateTime = localZonedDateTime.withZoneSameInstant(toZoneId)
    val rssFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
    return gmtZonedDateTime.format(rssFormatter)
}

fun currentDateTimeInJST(): String {
    val currentDateTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return currentDateTime.format(formatter)
}

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
