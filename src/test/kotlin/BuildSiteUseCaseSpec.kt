package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.application.BuildSiteUseCase
import jp.yappo.pologen.application.port.ConfigurationReader
import jp.yappo.pologen.application.port.EntrySource
import jp.yappo.pologen.application.port.SiteWriter
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
import java.nio.file.Path

class BuildSiteUseCaseSpec : FunSpec({
    test("execute resolves configured paths and writes generated site through ports") {
        val configPath = Path.of("/tmp/pologen/config.toml")
        val configuration = sampleConfiguration().copy(
            paths = sampleConfiguration().paths.copy(
                documentRoot = "htdocs",
                indexHtml = "public/index.html",
                feedXml = "public/feed.xml",
            )
        )
        val entry = Entry(
            filePath = Path.of("/tmp/pologen/htdocs/post/index.md"),
            urlPath = "/post/",
            title = "Post",
            publishDate = "Wed, 01 Jan 2025 18:04:05 GMT",
            publishDateLocal = "Thu, 02 Jan 2025 03:04:05 JST",
            markdown = "",
            html = "",
            body = "Body",
        )
        val entrySource = RecordingEntrySource(listOf(entry))
        val siteWriter = RecordingSiteWriter()

        BuildSiteUseCase(
            configurationReader = ConfigurationReader { configuration },
            entrySource = entrySource,
            siteWriter = siteWriter,
        ).execute(configPath)

        entrySource.documentRoot shouldBe Path.of("/tmp/pologen/htdocs")
        entrySource.configBaseDir shouldBe Path.of("/tmp/pologen")
        siteWriter.outputRoot shouldBe Path.of("/tmp/pologen/htdocs")
        siteWriter.indexHtmlPath shouldBe Path.of("/tmp/pologen/public/index.html")
        siteWriter.feedXmlPath shouldBe Path.of("/tmp/pologen/public/feed.xml")
        siteWriter.entriesForPages shouldBe listOf(entry)
        siteWriter.entriesForIndex shouldBe listOf(entry)
        siteWriter.entriesForFeed shouldBe listOf(entry)
    }
})

private class RecordingEntrySource(
    private val entries: List<Entry>,
) : EntrySource {
    lateinit var documentRoot: Path
    lateinit var configBaseDir: Path

    override fun collectEntries(configuration: Configuration, documentRoot: Path, configBaseDir: Path): List<Entry> {
        this.documentRoot = documentRoot
        this.configBaseDir = configBaseDir
        return entries
    }
}

private class RecordingSiteWriter : SiteWriter {
    lateinit var outputRoot: Path
    lateinit var indexHtmlPath: Path
    lateinit var feedXmlPath: Path
    lateinit var entriesForPages: List<Entry>
    lateinit var entriesForIndex: List<Entry>
    lateinit var entriesForFeed: List<Entry>

    override fun copyAssets(outputRoot: Path) {
        this.outputRoot = outputRoot
    }

    override fun renderEntries(configuration: Configuration, entries: List<Entry>) {
        entriesForPages = entries
    }

    override fun renderIndex(configuration: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
        this.indexHtmlPath = indexHtmlPath
        entriesForIndex = entries
    }

    override fun renderFeed(configuration: Configuration, feedXmlPath: Path, entries: List<Entry>) {
        this.feedXmlPath = feedXmlPath
        entriesForFeed = entries
    }
}
