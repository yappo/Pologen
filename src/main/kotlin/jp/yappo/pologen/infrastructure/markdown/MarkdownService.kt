package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.infrastructure.ogp.OGPGenerator
import jp.yappo.pologen.infrastructure.config.TomlReaders
import com.akuleshov7.ktoml.file.TomlFileWriter
import jp.yappo.pologen.application.util.convertToRssDateTimeFormat
import jp.yappo.pologen.application.util.currentDateTimeInJST
import jp.yappo.pologen.application.util.stripHtml
import jp.yappo.pologen.application.util.truncateSummary
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.config.EntryMeta
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.domain.model.TocEntry
import jp.yappo.pologen.infrastructure.image.generateResizedImages
import jp.yappo.pologen.infrastructure.util.resolveConfiguredPath
import org.apache.commons.text.StringEscapeUtils
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.ZoneId
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.streams.toList

class MarkdownService(
    private val digest: MessageDigest,
) {
    fun collectEntries(conf: Configuration, rootDirPath: Path, dirPath: Path, configBaseDir: Path): List<Entry> {
        val entries = mutableListOf<Entry>()
        val indexMdFile = dirPath.resolve("index.md")
        if (Files.exists(indexMdFile)) {
            entries.add(loadMarkdown(conf, rootDirPath, indexMdFile, configBaseDir))
        }

        Files.list(dirPath)
            .sorted(java.util.Comparator.reverseOrder())
            .filter { it.toFile().isDirectory }
            .forEach { child ->
                entries.addAll(collectEntries(conf, rootDirPath, child, configBaseDir))
            }

        return entries
    }

    private fun loadMarkdown(conf: Configuration, rootDirPath: Path, filePath: Path, configBaseDir: Path): Entry {
        val lines = Files.readAllLines(filePath)
        val titleLine = lines.first()

        val title = titleLine.removePrefix("title: ").trim().ifBlank { "Untitled" }
        val markdown = lines.drop(1).joinToString("\n").trim()
        val tocItems = extractToc(markdown)

        val relativePath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')
        val urlPath = "/${if (relativePath.isBlank()) "" else "$relativePath/"}"

        val processedMarkdown = processMarkdownImages(markdown, filePath.parent, conf)
        val markdownWithImages = processedMarkdown.markdown

        val flavour = org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor()
        val parsedTree = org.intellij.markdown.parser.MarkdownParser(flavour).buildMarkdownTreeFromString(markdownWithImages)
        val htmlGenerated = parsedTree.children.map {
            org.intellij.markdown.html.HtmlGenerator(markdownWithImages, it, flavour).generateHtml()
        }.joinToString(separator = "") { it }
        val htmlWithIds = injectHeadingIds(htmlGenerated, tocItems)
        val html = processedMarkdown.replacements.entries.fold(htmlWithIds) { acc, (placeholder, snippet) ->
            acc.replace(placeholder, snippet)
        }
        val body = stripHtml(html)
        val bodyDigest = digest.digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        val localZoneId = ZoneId.of("Asia/Tokyo")
        val gmtZoneId = ZoneId.of("GMT")

        val currentDateTime = currentDateTimeInJST()
        val metaFilePath = filePath.parent.resolve("meta.toml")
        val metaSummary = truncateSummary(body)
        val existingMeta = if (metaFilePath.isRegularFile()) {
            runCatching {
                TomlReaders.decodeMeta(EntryMeta.serializer(), metaFilePath)
            }.getOrElse {
                null
            }
        } else {
            null
        }

        val baseMeta = existingMeta ?: EntryMeta(
            publishDate = currentDateTime,
            updateDate = currentDateTime,
            bodyMd5 = bodyDigest,
            title = title,
            summary = metaSummary,
            toc = tocItems
        )
        val needsUpdate = existingMeta == null ||
            baseMeta.bodyMd5 != bodyDigest ||
            baseMeta.title != title ||
            baseMeta.summary != metaSummary ||
            baseMeta.toc != tocItems

        val meta = if (needsUpdate) {
            val updated = baseMeta.copy(
                bodyMd5 = bodyDigest,
                title = title,
                summary = metaSummary,
                toc = tocItems,
                updateDate = if (baseMeta.bodyMd5 == bodyDigest) baseMeta.updateDate else currentDateTime
            )
            com.akuleshov7.ktoml.file.TomlFileWriter().encodeToFile(
                EntryMeta.serializer(),
                updated,
                metaFilePath.toAbsolutePath().toString()
            )
            val label = if (existingMeta == null) "Created" else "Updated"
            println("$label: ${metaFilePath.toAbsolutePath()}")
            updated
        } else {
            baseMeta
        }

        var ogpImageUrl: String? = null
        var ogpDescription: String? = null
        if (conf.ogp.enabled) {
            val ogpPath = filePath.parent.resolve("ogp.png")
            ogpDescription = truncateSummary(body)
            val ogpSiteTitle = truncateSummary(conf.site.title, 60)
            val ogpEntryTitle = truncateSummary(title, 80)
            val needsOgp = !ogpPath.isRegularFile() || meta.bodyMd5 != bodyDigest
            if (needsOgp) {
                try {
                    val resolvedFont = resolveConfiguredPath(configBaseDir, conf.ogp.fontPath)
                    val resolvedIcon = resolveConfiguredPath(configBaseDir, conf.ogp.authorIconPath)
                    val resolvedOgp = conf.ogp.copy(
                        fontPath = resolvedFont?.toString(),
                        authorIconPath = resolvedIcon?.toString()
                    )
                    OGPGenerator.generate(
                        conf = resolvedOgp,
                        siteTitle = ogpSiteTitle,
                        entryTitle = ogpEntryTitle,
                        description = ogpDescription,
                        output = ogpPath
                    )
                } catch (e: Exception) {
                    println("Failed to generate OGP image for $filePath: ${e.message}")
                }
            }
            val urlSegment = urlPath.trimStart('/')
            val ogpUrl = if (urlSegment.isBlank()) URI(conf.site.documentBaseUrl).resolve(ogpPath.fileName.toString())
            else URI(conf.site.documentBaseUrl).resolve("$urlSegment${ogpPath.fileName}")
            ogpImageUrl = ogpUrl.normalize().toString()
        }

        return Entry(
            filePath = filePath,
            urlPath = urlPath,
            title = title,
            publishDate = convertToRssDateTimeFormat(meta.publishDate, localZoneId, gmtZoneId),
            publishDateLocal = convertToRssDateTimeFormat(meta.publishDate, localZoneId, localZoneId),
            markdown = markdownWithImages,
            html = html,
            body = body,
            ogpImageUrl = ogpImageUrl,
            ogpDescription = ogpDescription,
            toc = tocItems
        )
    }

    private fun extractToc(markdown: String): List<TocEntry> {
        val toc = mutableListOf<TocEntry>()
        markdown.lines().forEach { line ->
            val trimmed = line.trimStart()
            val level = when {
                trimmed.startsWith("### ") -> 3
                trimmed.startsWith("## ") -> 2
                else -> null
            }
            if (level != null) {
                val text = trimmed.removePrefix("#".repeat(level)).trim()
                val id = slugify(text)
                toc.add(TocEntry(level, text, id))
            }
        }
        return toc
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
        val hash = digest.digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "heading-${hash.take(16)}"
    }

    private fun injectHeadingIds(html: String, toc: List<TocEntry>): String {
        var result = html
        toc.forEach { item ->
            val tag = "<h${item.level}>"
            val replacement = """<h${item.level} id="${item.id}">"""
            result = result.replaceFirst(tag, replacement)
        }
        return result
    }

    private fun processMarkdownImages(
        markdown: String,
        entryDir: Path,
        conf: Configuration
    ): ProcessedMarkdown {
        val imageRegex = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
        val replacements = mutableMapOf<String, String>()
        val updated = imageRegex.replace(markdown) { matchResult ->
            val altText = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
            val relativeSource = matchResult.groupValues.getOrNull(2)?.trim().orEmpty()
            if (relativeSource.isBlank()) {
                return@replace matchResult.value
            }

            val sourcePath = entryDir.resolve(relativeSource).normalize()
            if (!sourcePath.isRegularFile()) {
                println("Image not found, skipping: $sourcePath")
                return@replace matchResult.value
            }

            val originalName = sourcePath.fileName.toString()
            val baseName = originalName.substringBeforeLast(".", originalName)
            val extension = originalName.substringAfterLast('.', "jpg")
            val fullName = "$baseName-full.$extension"
            val thumbName = "$baseName-thumb.$extension"
            val destFull = entryDir.resolve(fullName)
            val destThumb = entryDir.resolve(thumbName)

            try {
                generateResizedImages(
                    sourcePath,
                    destFull,
                    destThumb,
                    conf.images.fullMaxWidth,
                    conf.images.thumbWidth,
                    conf.images.scaleMethod,
                    conf.images.jpegQuality
                )
            } catch (e: Exception) {
                println("Failed to resize image $sourcePath: ${e.message}")
                return@replace matchResult.value
            }

            val placeholder = UUID.randomUUID().toString()
            val relativeFull = destFull.relativeTo(entryDir).toString().replace(File.separatorChar, '/')
            val relativeThumb = destThumb.relativeTo(entryDir).toString().replace(File.separatorChar, '/')
            val escapedAlt = StringEscapeUtils.escapeHtml4(altText)
            val snippet = """
                <button type="button" class="pologen-image-thumb block" data-full-src="./$relativeFull" data-alt="$escapedAlt">
                    <img src="./$relativeThumb" alt="$escapedAlt" loading="lazy" class="max-w-full h-auto rounded-xl shadow-md my-4"/>
                </button>
            """.trimIndent()
            replacements[placeholder] = snippet
            placeholder
        }
        return ProcessedMarkdown(updated, replacements)
    }

}

data class ProcessedMarkdown(
    val markdown: String,
    val replacements: Map<String, String>,
)
