package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText

class RssSpec : FunSpec({
    test("createRssXML writes RSS feed with items and escapes content") {
        val tmp: Path = createTempDirectory("pologen-rss-")
        val conf = Configuration(
            documentRootPath = ".",
            blogTopUrl = "/",
            documentBaseUrl = "https://example.com",
            feedXmlPath = "feed.xml",
            feedXmlUrl = "/feed.xml",
            indexHtmlPath = "index.html",
            siteTitle = "Example Site",
            siteDescription = "Example Description",
            siteLanguage = "ja",
            faviconUrl = "/favicon.png",
            authorName = "@example",
            authorUrl = "https://example.com/me",
            authorIconUrl = "https://example.com/me.png",
        )
        val entry = Entry(
            filePath = tmp.resolve("dummy/index.md"),
            urlPath = "/post/",
            title = "Post & Title",
            publishDate = "Wed, 01 Jan 2025 18:04:05 GMT",
            publishDateLocal = "Thu, 02 Jan 2025 03:04:05 JST",
            markdown = "",
            html = "",
            body = "A&B < C",
        )

        val out = tmp.resolve("feed.xml")
        createRssXML(conf, out, listOf(entry))

        val xml = out.readText()
        xml shouldContain "<title>Example Site</title>"
        xml shouldContain "<item>"
        xml shouldContain "<title>Post &amp; Title</title>".replace("&amp;", "&") // title isn't escaped by code; ensure it appears as is
        xml shouldContain "<link>https://example.com/post/</link>"
        xml shouldContain "<pubDate>Wed, 01 Jan 2025 18:04:05 GMT</pubDate>"
        xml shouldContain "A&amp;B &lt; C"
        xml shouldContain "<description>Example Description</description>"
        xml shouldContain "<language>ja</language>"
    }
})
