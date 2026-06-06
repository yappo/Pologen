package jp.yappo.pologen.infrastructure.rendering

import jp.yappo.pologen.application.port.SiteWriter
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
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
        val scriptPath = assetsDir.resolve("pologen.js")
        val resource = SiteRenderer::class.java.classLoader.getResourceAsStream("assets/pologen.js")
        if (resource != null) {
            resource.use { input ->
                Files.copy(input, scriptPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    override fun renderEntries(conf: Configuration, entries: List<Entry>) {
        entries.forEach { entry ->
            val recentEntries = buildRecentEntries(conf, entries, entry.urlPath)
            val content = Templates.renderEntry(conf, entry, recentEntries)
            writeFile(entry.filePath.parent.resolve("index.html"), content)
        }
        println("Created ${entries.size} entries.")
    }

    override fun renderIndex(conf: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
        val recentEntries = buildRecentEntries(conf, entries, currentUrlPath = null)
        val content = Templates.renderIndex(conf, entries, recentEntries)
        writeFile(indexHtmlPath, content)
    }

    override fun renderFeed(conf: Configuration, feedXmlPath: Path, entries: List<Entry>) {
        val content = Templates.renderFeed(conf, entries)
        writeFile(feedXmlPath, content)
    }

    private fun buildRecentEntries(conf: Configuration, entries: List<Entry>, currentUrlPath: String?): List<RecentEntry> {
        return entries.take(conf.sidebar.recentEntryCount).map {
            val href = java.net.URI(conf.site.documentBaseUrl + it.urlPath).normalize().toString()
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
}
