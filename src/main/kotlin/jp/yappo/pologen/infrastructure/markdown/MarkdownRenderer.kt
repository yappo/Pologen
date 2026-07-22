package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.domain.model.TocEntry
import jp.yappo.pologen.domain.support.sha256Hex
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup

class MarkdownRenderer {
    private val flavour = CommonMarkFlavourDescriptor()

    fun render(markdown: String): RenderedMarkdown {
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val html = parsedTree.children.joinToString(separator = "") {
            HtmlGenerator(markdown, it, flavour).generateHtml()
        }
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val usedSlugs = mutableMapOf<String, Int>()
        val toc = document.select("h2, h3").map { heading ->
            val text = heading.text().trim()
            val baseSlug = slugify(text)
            val occurrence = usedSlugs.getOrDefault(baseSlug, 0) + 1
            usedSlugs[baseSlug] = occurrence
            val id = if (occurrence == 1) baseSlug else "$baseSlug-$occurrence"
            heading.attr("id", id)
            TocEntry(heading.tagName().removePrefix("h").toInt(), text, id)
        }
        return RenderedMarkdown(
            html = document.body().html(),
            toc = toc,
        )
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
}

data class RenderedMarkdown(
    val html: String,
    val toc: List<TocEntry>,
)
