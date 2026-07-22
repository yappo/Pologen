package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.application.port.EntrySource
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.config.EntryImageMeta
import jp.yappo.pologen.domain.config.EntryMeta
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.domain.support.convertToRssDateTimeFormat
import jp.yappo.pologen.domain.support.currentDateTimeInJST
import jp.yappo.pologen.domain.support.resolveDocumentUrl
import jp.yappo.pologen.domain.support.sha256Hex
import jp.yappo.pologen.domain.support.stripHtml
import jp.yappo.pologen.domain.support.truncateSummary
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.util.Comparator
import kotlin.io.path.isRegularFile

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
        val newEntryPublishDate = currentDateTimeInJST()
        val drafts = discoverMarkdownFiles(dirPath).map { filePath ->
            createDraft(rootDirPath, filePath, newEntryPublishDate)
        }
        val renderConfigSha256 = sha256Hex(
            "$ENTRY_CACHE_VERSION|${templateFingerprint(conf, configBaseDir)}|${configBaseDir.toAbsolutePath().normalize()}|$conf"
        )
        val navigationSha256 = sha256Hex(
            drafts.joinToString("\n") { draft ->
                listOf(draft.urlPath, draft.title, draft.publishDate, draft.tags.joinToString(",")).joinToString("|")
            }
        )
        return drafts.map { draft ->
            if (canReuse(draft, conf, renderConfigSha256, navigationSha256)) {
                reuseEntry(draft, conf)
            } else {
                loadMarkdown(conf, draft, configBaseDir, renderConfigSha256, navigationSha256)
            }
        }
    }

    private fun discoverMarkdownFiles(dirPath: Path): List<Path> = buildList {
        val indexMdFile = dirPath.resolve("index.md")
        if (indexMdFile.isRegularFile()) {
            add(indexMdFile)
        }
        Files.list(dirPath).use { children ->
            children
                .sorted(Comparator.reverseOrder())
                .filter(Files::isDirectory)
                .forEach { child -> addAll(discoverMarkdownFiles(child)) }
        }
    }

    private fun createDraft(rootDirPath: Path, filePath: Path, newEntryPublishDate: String): EntryDraft {
        val lines = Files.readAllLines(filePath)
        val titleLine = lines.firstOrNull()
        require(titleLine?.startsWith("title: ") == true && titleLine.removePrefix("title: ").isNotBlank()) {
            "The first line of $filePath must use the format: title: Your Title"
        }
        val title = titleLine.removePrefix("title: ").trim()
        val tagsLine = lines.getOrNull(1)?.takeIf { it.startsWith("tags:") }
        val tags = tagsLine
            ?.removePrefix("tags:")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
        val markdown = lines.drop(if (tagsLine == null) 1 else 2).joinToString("\n").trim()
        val relativePath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')
        val urlPath = "/${if (relativePath.isBlank()) "" else "$relativePath/"}"
        val metaFilePath = filePath.parent.resolve("meta.toml")
        val existingMeta = metaStore.read(metaFilePath)
        return EntryDraft(
            filePath = filePath,
            metaFilePath = metaFilePath,
            title = title,
            markdown = markdown,
            urlPath = urlPath,
            sourceSha256 = sha256Hex(filePath),
            existingMeta = existingMeta,
            publishDate = existingMeta?.publishDate ?: newEntryPublishDate,
            tags = tags,
        )
    }

    private fun canReuse(
        draft: EntryDraft,
        conf: Configuration,
        renderConfigSha256: String,
        navigationSha256: String,
    ): Boolean {
        val meta = draft.existingMeta ?: return false
        if (meta.generatorVersion != ENTRY_CACHE_VERSION ||
            meta.sourceSha256 != draft.sourceSha256 ||
            meta.renderConfigSha256 != renderConfigSha256 ||
            meta.navigationSha256 != navigationSha256 ||
            meta.title == null ||
            meta.title != draft.title ||
            meta.summary == null ||
            meta.indexSummary == null ||
            meta.tags != draft.tags ||
            !draft.filePath.parent.resolve("index.html").isRegularFile()
        ) {
            return false
        }
        if (conf.ogp.enabled && !draft.filePath.parent.resolve("ogp.png").isRegularFile()) {
            return false
        }
        if (draft.markdown.contains("![") && meta.images.isEmpty()) {
            return false
        }
        return meta.images.all { imageCacheIsValid(draft.filePath.parent, it) }
    }

    private fun imageCacheIsValid(entryDir: Path, image: EntryImageMeta): Boolean {
        val source = entryDir.resolve(image.sourcePath).normalize()
        return source.isRegularFile() &&
            sha256Hex(source) == image.sourceSha256 &&
            entryDir.resolve(image.fullPath).isRegularFile() &&
            entryDir.resolve(image.thumbPath).isRegularFile()
    }

    private fun reuseEntry(draft: EntryDraft, conf: Configuration): Entry {
        val meta = requireNotNull(draft.existingMeta)
        val ogpPath = draft.filePath.parent.resolve("ogp.png")
        return Entry(
            filePath = draft.filePath,
            urlPath = draft.urlPath,
            title = requireNotNull(meta.title),
            publishDate = rssDate(meta.publishDate, GMT),
            publishDateLocal = rssDate(meta.publishDate, JST),
            markdown = "",
            html = "",
            body = requireNotNull(meta.indexSummary),
            ogpImageUrl = if (conf.ogp.enabled && ogpPath.isRegularFile()) {
                resolveDocumentUrl(conf.site.documentBaseUrl, "${draft.urlPath}ogp.png")
            } else {
                null
            },
            ogpDescription = if (conf.ogp.enabled) meta.summary else null,
            toc = meta.toc,
            needsRender = false,
            tags = meta.tags,
        )
    }

    private fun loadMarkdown(
        conf: Configuration,
        draft: EntryDraft,
        configBaseDir: Path,
        renderConfigSha256: String,
        navigationSha256: String,
    ): Entry {
        val processedMarkdown = imageProcessor.process(
            draft.markdown,
            draft.filePath.parent,
            conf.images,
            draft.existingMeta?.images.orEmpty(),
        )
        val renderedMarkdown = markdownRenderer.render(processedMarkdown.markdown)
        val html = processedMarkdown.replacements.entries.fold(renderedMarkdown.html) { acc, (placeholder, snippet) ->
            acc.replace(placeholder, snippet)
        }
        val body = stripHtml(html)
        val indexSummary = if (body.length > 140) body.take(140) + "..." else body
        val metaState = metaStore.synchronize(
            metaFilePath = draft.metaFilePath,
            body = body,
            title = draft.title,
            summary = truncateSummary(body),
            toc = renderedMarkdown.toc,
            indexSummary = indexSummary,
            sourceSha256 = draft.sourceSha256,
            renderConfigSha256 = renderConfigSha256,
            navigationSha256 = navigationSha256,
            images = processedMarkdown.images,
            tags = draft.tags,
            initialPublishDate = draft.publishDate,
            existingMeta = draft.existingMeta,
        )
        val ogpMetadata = ogpImageService.prepare(
            conf,
            metaState,
            body,
            draft.title,
            draft.filePath,
            draft.urlPath,
            configBaseDir,
        )
        return Entry(
            filePath = draft.filePath,
            urlPath = draft.urlPath,
            title = draft.title,
            publishDate = rssDate(metaState.meta.publishDate, GMT),
            publishDateLocal = rssDate(metaState.meta.publishDate, JST),
            markdown = processedMarkdown.markdown,
            html = html,
            body = body,
            ogpImageUrl = ogpMetadata.imageUrl,
            ogpDescription = ogpMetadata.description,
            toc = renderedMarkdown.toc,
            tags = draft.tags,
        )
    }

    private fun rssDate(value: String, zoneId: ZoneId): String = convertToRssDateTimeFormat(value, JST, zoneId)

    private fun templateFingerprint(conf: Configuration, configBaseDir: Path): String {
        val customDirectory = conf.templates.directory?.let { configuredPath ->
            val path = Path.of(configuredPath)
            if (path.isAbsolute) path.normalize() else configBaseDir.resolve(path).normalize()
        }
        if (customDirectory != null) {
            val fingerprints = templateNames(conf).map { templateName ->
                val templatePath = customDirectory.resolve(templateName)
                require(templatePath.isRegularFile()) { "Custom template is missing: $templatePath" }
                sha256Hex(templatePath)
            }
            return sha256Hex(fingerprints.joinToString("|"))
        }
        val classLoader = MarkdownService::class.java.classLoader
        val fingerprints = templateNames(conf).map { templateName ->
            val resourcePath = "templates/$templateName"
            val bytes = requireNotNull(classLoader.getResourceAsStream(resourcePath)) {
                "Bundled template is missing: $resourcePath"
            }.use { it.readBytes() }
            sha256Hex(bytes)
        }
        return sha256Hex(fingerprints.joinToString("|"))
    }

    private fun templateNames(conf: Configuration): List<String> =
        TEMPLATE_NAMES +
            (if (conf.archive.enabled) listOf("archive.kte") else emptyList()) +
            (if (conf.tags.enabled) listOf("tags.kte", "tag.kte") else emptyList())

    private data class EntryDraft(
        val filePath: Path,
        val metaFilePath: Path,
        val title: String,
        val markdown: String,
        val urlPath: String,
        val sourceSha256: String,
        val existingMeta: EntryMeta?,
        val publishDate: String,
        val tags: List<String>,
    )

    private companion object {
        val JST: ZoneId = ZoneId.of("Asia/Tokyo")
        val GMT: ZoneId = ZoneId.of("GMT")
        val TEMPLATE_NAMES = listOf("entry.kte", "index.kte", "feed.kte")
    }
}
