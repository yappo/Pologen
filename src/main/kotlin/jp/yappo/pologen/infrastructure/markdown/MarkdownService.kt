package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.application.port.EntrySource
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.domain.support.convertToRssDateTimeFormat
import jp.yappo.pologen.domain.support.stripHtml
import jp.yappo.pologen.domain.support.truncateSummary
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.util.Comparator

class MarkdownService(
    private val imageProcessor: MarkdownImageProcessor = MarkdownImageProcessor(),
    private val markdownRenderer: MarkdownRenderer = MarkdownRenderer(),
    private val metaStore: EntryMetaStore = EntryMetaStore(),
    private val ogpImageService: OgpImageService = OgpImageService(),
) : EntrySource {
    override fun collectEntries(configuration: Configuration, documentRoot: Path, configBaseDir: Path): List<Entry> {
        return collectEntries(configuration, documentRoot, documentRoot, configBaseDir)
    }

    fun collectEntries(conf: Configuration, rootDirPath: Path, dirPath: Path, configBaseDir: Path): List<Entry> {
        return buildList {
            val indexMdFile = dirPath.resolve("index.md")
            if (Files.exists(indexMdFile)) {
                add(loadMarkdown(conf, rootDirPath, indexMdFile, configBaseDir))
            }

            Files.list(dirPath).use { children ->
                children
                    .sorted(Comparator.reverseOrder())
                    .filter { Files.isDirectory(it) }
                    .forEach { child ->
                        addAll(collectEntries(conf, rootDirPath, child, configBaseDir))
                    }
            }
        }
    }

    private fun loadMarkdown(conf: Configuration, rootDirPath: Path, filePath: Path, configBaseDir: Path): Entry {
        val lines = Files.readAllLines(filePath)
        val titleLine = lines.firstOrNull().orEmpty()

        val title = titleLine.removePrefix("title: ").trim().ifBlank { "Untitled" }
        val markdown = lines.drop(1).joinToString("\n").trim()

        val relativePath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')
        val urlPath = "/${if (relativePath.isBlank()) "" else "$relativePath/"}"

        val processedMarkdown = imageProcessor.process(markdown, filePath.parent, conf.images)
        val markdownWithImages = processedMarkdown.markdown
        val renderedMarkdown = markdownRenderer.render(markdownWithImages)
        val html = processedMarkdown.replacements.entries.fold(renderedMarkdown.html) { acc, (placeholder, snippet) ->
            acc.replace(placeholder, snippet)
        }
        val body = stripHtml(html)

        val localZoneId = ZoneId.of("Asia/Tokyo")
        val gmtZoneId = ZoneId.of("GMT")

        val metaFilePath = filePath.parent.resolve("meta.toml")
        val metaSummary = truncateSummary(body)
        val metaState = metaStore.synchronize(
            metaFilePath = metaFilePath,
            body = body,
            title = title,
            summary = metaSummary,
            toc = renderedMarkdown.toc,
        )
        val ogpMetadata = ogpImageService.prepare(conf, metaState, body, title, filePath, urlPath, configBaseDir)

        return Entry(
            filePath = filePath,
            urlPath = urlPath,
            title = title,
            publishDate = convertToRssDateTimeFormat(metaState.meta.publishDate, localZoneId, gmtZoneId),
            publishDateLocal = convertToRssDateTimeFormat(metaState.meta.publishDate, localZoneId, localZoneId),
            markdown = markdownWithImages,
            html = html,
            body = body,
            ogpImageUrl = ogpMetadata.imageUrl,
            ogpDescription = ogpMetadata.description,
            toc = renderedMarkdown.toc,
        )
    }
}
