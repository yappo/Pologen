package jp.yappo.pologen.infrastructure.rendering

import jp.yappo.pologen.application.port.SiteWriter
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.domain.support.resolveDocumentUrl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class SiteRenderer : SiteWriter {

    override fun copyAssets(outputRoot: Path) {
        val assetsDir = outputRoot.resolve("assets")
        if (!assetsDir.exists()) {
            assetsDir.createDirectories()
        }
        copyAsset("assets/pologen.js", assetsDir.resolve("pologen.js"))
        copyAsset("assets/pologen.css", assetsDir.resolve("pologen.css"))
    }

    override fun renderEntries(configuration: Configuration, entries: List<Entry>) {
        entries.filter { it.needsRender }.forEach { entry ->
            val recentEntries = buildRecentEntries(configuration, entries, entry.urlPath)
            val content = Templates.renderEntry(configuration, entry, recentEntries)
            writeFile(entry.filePath.parent.resolve("index.html"), content)
        }
        val renderedCount = entries.count { it.needsRender }
        println("Created $renderedCount entries; reused ${entries.size - renderedCount} unchanged entries.")
    }

    override fun renderIndex(configuration: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
        val recentEntries = buildRecentEntries(configuration, entries, currentUrlPath = null)
        val content = Templates.renderIndex(configuration, entries, recentEntries)
        writeFile(indexHtmlPath, content)
    }

    override fun renderFeed(configuration: Configuration, feedXmlPath: Path, entries: List<Entry>) {
        val content = Templates.renderFeed(configuration, entries)
        writeFile(feedXmlPath, content)
    }

    override fun renderArchive(configuration: Configuration, archiveHtmlPath: Path, entries: List<Entry>) {
        val content = Templates.renderArchive(configuration, entries)
        writeFile(archiveHtmlPath, content)
    }

    private fun buildRecentEntries(conf: Configuration, entries: List<Entry>, currentUrlPath: String?): List<RecentEntry> {
        return entries.take(conf.sidebar.recentEntryCount).map {
            val href = resolveDocumentUrl(conf.site.documentBaseUrl, it.urlPath)
            RecentEntry(
                title = it.title,
                href = href,
                publishDateLocal = it.publishDateLocal,
                isCurrent = currentUrlPath != null && currentUrlPath == it.urlPath,
            )
        }
    }

    private fun writeFile(path: Path, content: String) {
        path.parent?.createDirectories()
        Files.writeString(path, content)
        println("Created: ${path.toAbsolutePath()}")
    }

    private fun copyAsset(resourcePath: String, outputPath: Path) {
        val resource = requireNotNull(SiteRenderer::class.java.classLoader.getResourceAsStream(resourcePath)) {
            "Bundled asset is missing: $resourcePath"
        }
        resource.use { input ->
            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
