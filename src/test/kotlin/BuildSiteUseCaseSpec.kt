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
import kotlin.io.path.createTempDirectory

class BuildSiteUseCaseSpec : FunSpec({
    test("execute resolves configured paths and writes generated site through ports") {
        val tempDir = createTempDirectory("pologen-usecase-")
        val configPath = tempDir.resolve("config.toml")
        val documentRoot = tempDir.resolve("htdocs").also { it.toFile().mkdirs() }
        val configuration = sampleConfiguration().copy(
            paths = sampleConfiguration().paths.copy(
                documentRoot = "htdocs",
                indexHtml = "public/index.html",
                feedXml = "public/feed.xml",
            )
        )
        val entry = Entry(
            filePath = documentRoot.resolve("post/index.md"),
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

        val result = BuildSiteUseCase(
            configurationReader = ConfigurationReader { configuration },
            entrySource = entrySource,
            siteWriter = siteWriter,
        ).execute(configPath)

        result shouldBe true

        entrySource.documentRoot shouldBe tempDir.resolve("htdocs")
        entrySource.configBaseDir shouldBe tempDir
        siteWriter.outputRoot shouldBe tempDir.resolve("htdocs")
        siteWriter.indexHtmlPath shouldBe tempDir.resolve("public/index.html")
        siteWriter.feedXmlPath shouldBe tempDir.resolve("public/feed.xml")
        siteWriter.entriesForPages shouldBe listOf(entry)
        siteWriter.entriesForIndex shouldBe listOf(entry)
        siteWriter.entriesForFeed shouldBe listOf(entry)
    }

    test("execute returns before writing when document root is invalid") {
        val tempDir = createTempDirectory("pologen-usecase-invalid-")
        val configuration = sampleConfiguration().copy(
            paths = sampleConfiguration().paths.copy(documentRoot = "missing")
        )
        val entrySource = RecordingEntrySource(emptyList())
        val siteWriter = RecordingSiteWriter()

        val result = BuildSiteUseCase(
            configurationReader = ConfigurationReader { configuration },
            entrySource = entrySource,
            siteWriter = siteWriter,
        ).execute(tempDir.resolve("config.toml"))

        result shouldBe false
        entrySource.wasCalled shouldBe false
        siteWriter.wasCalled shouldBe false
    }
})

private class RecordingEntrySource(
    private val entries: List<Entry>,
) : EntrySource {
    var wasCalled: Boolean = false
    lateinit var documentRoot: Path
    lateinit var configBaseDir: Path

    override fun collectEntries(configuration: Configuration, documentRoot: Path, configBaseDir: Path): List<Entry> {
        wasCalled = true
        this.documentRoot = documentRoot
        this.configBaseDir = configBaseDir
        return entries
    }
}

private class RecordingSiteWriter : SiteWriter {
    var wasCalled: Boolean = false
    lateinit var outputRoot: Path
    lateinit var indexHtmlPath: Path
    lateinit var feedXmlPath: Path
    lateinit var entriesForPages: List<Entry>
    lateinit var entriesForIndex: List<Entry>
    lateinit var entriesForFeed: List<Entry>

    override fun copyAssets(outputRoot: Path) {
        wasCalled = true
        this.outputRoot = outputRoot
    }

    override fun renderEntries(configuration: Configuration, entries: List<Entry>) {
        wasCalled = true
        entriesForPages = entries
    }

    override fun renderIndex(configuration: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
        wasCalled = true
        this.indexHtmlPath = indexHtmlPath
        entriesForIndex = entries
    }

    override fun renderFeed(configuration: Configuration, feedXmlPath: Path, entries: List<Entry>) {
        wasCalled = true
        this.feedXmlPath = feedXmlPath
        entriesForFeed = entries
    }
}
