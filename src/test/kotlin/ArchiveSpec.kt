package jp.yappo.pologen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.yappo.pologen.domain.config.ArchiveConfig
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.infrastructure.config.validateConfiguration
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText

class ArchiveSpec : FunSpec({
    test("archive groups every entry by month in reverse chronological order") {
        val root = createTempDirectory("pologen-archive-")
        val output = root.resolve("archive/index.html")
        val configuration = sampleConfiguration().copy(
            archive = ArchiveConfig(enabled = true),
        )
        val entries = listOf(
            archiveEntry("Older January", "/older/", "Thu, 02 Jan 2025 03:04:05 JST"),
            archiveEntry("December", "/december/", "Fri, 20 Dec 2024 12:00:00 JST"),
            archiveEntry("Newer January", "/newer/", "Fri, 10 Jan 2025 12:00:00 JST"),
        )

        SiteRenderer().renderArchive(configuration, output, entries)

        val html = output.readText()
        html shouldContain "January 2025"
        html shouldContain "December 2024"
        html shouldContain "href=\"https://example.com/newer/\""
        (html.indexOf("Newer January") < html.indexOf("Older January")) shouldBe true
        (html.indexOf("January 2025") < html.indexOf("December 2024")) shouldBe true
    }

    test("archive output cannot escape the document root") {
        shouldThrow<IllegalArgumentException> {
            validateConfiguration(
                sampleConfiguration().copy(
                    archive = ArchiveConfig(enabled = true, output = "../archive.html"),
                )
            )
        }
    }
})

private fun archiveEntry(title: String, urlPath: String, publishDateLocal: String): Entry = Entry(
    filePath = Path.of(urlPath.trim('/'), "index.md"),
    urlPath = urlPath,
    title = title,
    publishDate = "Fri, 10 Jan 2025 03:00:00 GMT",
    publishDateLocal = publishDateLocal,
    markdown = "",
    html = "",
    body = "$title summary",
)
