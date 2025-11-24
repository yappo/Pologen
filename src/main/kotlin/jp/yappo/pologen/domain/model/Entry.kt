package jp.yappo.pologen.domain.model

import kotlinx.serialization.Serializable
import java.nio.file.Path

data class Entry(
    val filePath: Path,
    val urlPath: String,
    val title: String,
    val publishDate: String,
    val publishDateLocal: String,
    val markdown: String,
    val html: String,
    val body: String,
    val ogpImageUrl: String? = null,
    val ogpDescription: String? = null,
    val toc: List<TocEntry> = emptyList(),
) {
    val summary: String by lazy {
        if (body.length > 140) {
            body.take(140) + "..."
        } else {
            body
        }
    }
}

@Serializable
data class TocEntry(
    val level: Int,
    val text: String,
    val id: String,
)
