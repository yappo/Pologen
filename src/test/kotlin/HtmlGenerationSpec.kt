package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
            html = "<p>Hello <em>world</em></p>",
            body = "Hello world",
        )
        val conf = Configuration(
            documentRootPath = ".",
            blogTopUrl = "/",
            documentBaseUrl = "https://example.com",
            feedXmlPath = "feed.xml",
            feedXmlUrl = "/feed.xml",
            indexHtmlPath = "index.html",
            siteTitle = "Example Site",
            siteDescription = "Example Description",
            siteLanguage = "en",
            faviconUrl = "/favicon.png",
            authorName = "@example",
            authorUrl = "https://example.com/me",
            authorIconUrl = "https://example.com/me.png",
        )

        createEntryHtml(conf, entry, listOf(entry))

        val written = dir.resolve("index.html").readText()
        written shouldContain "Hello <em>world</em>"
        written shouldContain entry.title
        written shouldContain "cdn.tailwindcss.com"
        written shouldContain "daisyui"
        written shouldContain "Share on X"
    }

    test("createIndexHtml writes index file listing entries") {
        val tmp: Path = createTempDirectory("pologen-index-")
        val conf = Configuration(
            documentRootPath = ".",
            blogTopUrl = "/",
            documentBaseUrl = "https://example.com",
            feedXmlPath = "feed.xml",
            feedXmlUrl = "/feed.xml",
            indexHtmlPath = "out/index.html",
            siteTitle = "Example Site",
            siteDescription = "Example Description",
            siteLanguage = "en",
            faviconUrl = "/favicon.png",
            authorName = "@example",
            authorUrl = "https://example.com/me",
            authorIconUrl = "https://example.com/me.png",
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
        createIndexHtml(conf, outPath, listOf(entry))

        val html = outPath.readText()
        html shouldContain "PostTitle"
        html shouldContain ">Thu, 02 Jan 2025 03:04:05 JST<"
        html shouldContain "href=\"https://example.com/post/\""
        html shouldContain "cdn.tailwindcss.com"
    }
})
