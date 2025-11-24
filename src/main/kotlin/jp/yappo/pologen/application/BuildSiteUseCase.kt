package jp.yappo.pologen.application

import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.infrastructure.config.ConfigurationLoader
import jp.yappo.pologen.infrastructure.markdown.MarkdownService
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import java.nio.file.Path
import java.security.MessageDigest

class BuildSiteUseCase(
    private val configurationLoader: ConfigurationLoader = ConfigurationLoader(),
    private val markdownService: MarkdownService = MarkdownService(MessageDigest.getInstance("SHA-256")),
    private val siteRenderer: SiteRenderer = SiteRenderer(),
) {

    fun execute(configPath: Path) {
        val configuration = configurationLoader.load(configPath)
        val docsRootDir = configPath.parent.resolve(configuration.paths.documentRoot).normalize()
        val entries = markdownService.collectEntries(configuration, docsRootDir, docsRootDir, configPath.parent)
        siteRenderer.copyOverlayScript(docsRootDir)
        siteRenderer.renderEntries(configuration, entries)
        val indexEntries = entries.take(30)
        siteRenderer.renderIndex(configuration, configPath.parent.resolve(configuration.paths.indexHtml).normalize(), indexEntries)
        siteRenderer.renderFeed(configuration, configPath.parent.resolve(configuration.paths.feedXml).normalize(), indexEntries)
    }
}
