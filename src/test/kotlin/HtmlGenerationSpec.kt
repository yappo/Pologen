package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.yappo.pologen.domain.support.convertToRssDateTimeFormat
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText

class HtmlGenerationSpec : FunSpec({
    test("createEntryHtml writes index.html with entry content") {
        val tmp: Path = createTempDirectory("pologen-entry-")
        val dir = tmp.resolve("entry"); Files.createDirectories(dir)
        val entry = Entry(
            filePath = dir.resolve("index.md"),
            urlPath = "/entry/",
            title = "T",
            publishDate = convertToRssDateTimeFormat("2025-01-02 03:04:05", ZoneId.of("Asia/Tokyo"), ZoneId.of("GMT")),
            publishDateLocal = convertToRssDateTimeFormat("2025-01-02 03:04:05", ZoneId.of("Asia/Tokyo"), ZoneId.of("Asia/Tokyo")),
            markdown = "Hello <em>world</em>",
            html = "<p>Hello <em>world</em> and <a href=\"https://example.com/reference\">reference</a></p>",
            body = "Hello world",
        )
        val conf = sampleConfiguration()

        val renderer = SiteRenderer()
        renderer.renderEntries(conf, listOf(entry))

        val written = dir.resolve("index.html").readText()
        written shouldContain "Hello <em>world</em>"
        written shouldContain "[&_a]:text-sky-300"
        written shouldContain "[&_a]:underline"
        written shouldContain "[&_a:focus-visible]:ring-2"
        written shouldContain "<a href=\"https://example.com/reference\">reference</a>"
        written shouldContain entry.title
        written shouldContain "cdn.tailwindcss.com"
        written shouldContain "daisyui"
        written shouldContain "Share on X"
    }

    test("createIndexHtml writes index file listing entries") {
        val tmp: Path = createTempDirectory("pologen-index-")
        val base = sampleConfiguration()
        val conf = base.copy(
            paths = base.paths.copy(indexHtml = "out/index.html")
        )
        val entry = Entry(
            filePath = tmp.resolve("dummy/index.md"),
            urlPath = "/post/",
            title = "PostTitle",
            publishDate = "Wed, 01 Jan 2025 18:04:05 GMT",
            publishDateLocal = "Thu, 02 Jan 2025 03:04:05 JST",
            markdown = "",
            html = "",
            body = "summary & more",
        )

        val outPath = tmp.resolve("out/index.html")
        Files.createDirectories(outPath.parent)
        val renderer = SiteRenderer()
        renderer.renderIndex(conf, outPath, listOf(entry))

        val html = outPath.readText()
        html shouldContain "PostTitle"
        html shouldContain ">Thu, 02 Jan 2025 03:04:05 JST<"
        html shouldContain "href=\"https://example.com/post/\""
        html shouldContain "cdn.tailwindcss.com"
    }
})
