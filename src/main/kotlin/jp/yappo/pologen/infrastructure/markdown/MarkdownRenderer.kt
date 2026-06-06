package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.domain.model.TocEntry
import jp.yappo.pologen.domain.support.sha256Hex
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

class MarkdownRenderer {
    private val flavour = CommonMarkFlavourDescriptor()

    fun render(markdown: String): RenderedMarkdown {
        val toc = extractToc(markdown)
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val html = parsedTree.children.joinToString(separator = "") {
            HtmlGenerator(markdown, it, flavour).generateHtml()
        }
        return RenderedMarkdown(
            html = injectHeadingIds(html, toc),
            toc = toc,
        )
    }

    private fun extractToc(markdown: String): List<TocEntry> = buildList {
        markdown.lines().forEach { line ->
            val trimmed = line.trimStart()
            val level = when {
                trimmed.startsWith("### ") -> 3
                trimmed.startsWith("## ") -> 2
                else -> null
            }
            if (level != null) {
                val text = trimmed.removePrefix("#".repeat(level)).trim()
                add(TocEntry(level, text, slugify(text)))
            }
        }
    }

    private fun slugify(text: String): String {
        val normalized = text.lowercase().trim()
        val cleaned = normalized
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .trim()
            .replace(Regex("\\s+"), "-")
        if (cleaned.isNotBlank()) {
            return cleaned
        }
        return "heading-${sha256Hex(normalized).take(16)}"
    }

    private fun injectHeadingIds(html: String, toc: List<TocEntry>): String {
        var result = html
        toc.forEach { item ->
            result = result.replaceFirst("<h${item.level}>", """<h${item.level} id="${item.id}">""")
        }
        return result
    }
}

data class RenderedMarkdown(
    val html: String,
    val toc: List<TocEntry>,
)
