package jp.yappo.pologen

import kotlinx.serialization.Serializable

/**
 * Represents a heading entry for table of contents.
 */
@Serializable
data class TocEntry(
    val level: Int,
    val text: String,
    val id: String,
)
