package jp.yappo.pologen.application

import jp.yappo.pologen.application.port.ConfigurationReader
import jp.yappo.pologen.application.port.EntrySource
import jp.yappo.pologen.application.port.SiteWriter
import jp.yappo.pologen.infrastructure.config.ConfigurationLoader
import jp.yappo.pologen.infrastructure.markdown.MarkdownService
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import java.nio.file.Files
import java.nio.file.Path

class BuildSiteUseCase(
    private val configurationReader: ConfigurationReader = ConfigurationLoader(),
    private val entrySource: EntrySource = MarkdownService(),
    private val siteWriter: SiteWriter = SiteRenderer(),
) {

    fun execute(configPath: Path): Boolean {
        val configuration = configurationReader.load(configPath)
        val paths = SiteBuildPaths.resolve(configPath, configuration)
        if (!Files.exists(paths.documentRoot) || !Files.isDirectory(paths.documentRoot)) {
            println("Invalid docs directory: ${paths.documentRoot}")
            return false
        }
        val entries = entrySource.collectEntries(configuration, paths.documentRoot, paths.configBaseDir)
        siteWriter.copyAssets(paths.documentRoot)
        siteWriter.renderEntries(configuration, entries)
        val indexEntries = entries.take(30)
        siteWriter.renderIndex(configuration, paths.indexHtml, indexEntries)
        siteWriter.renderFeed(configuration, paths.feedXml, indexEntries)
        paths.archiveHtml?.let { archiveHtmlPath ->
            siteWriter.renderArchive(configuration, archiveHtmlPath, entries)
        }
        paths.tagOutputRoot?.let { tagOutputRoot ->
            siteWriter.renderTags(configuration, tagOutputRoot, entries)
        }
        return true
    }
}
