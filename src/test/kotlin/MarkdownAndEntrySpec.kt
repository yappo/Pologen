package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class MarkdownAndEntrySpec : FunSpec({
    test("loadMarkdown parses index.md and meta.toml, producing Entry with expected fields") {
        val root: Path = createTempDirectory("pologen-md-")
        val dir = root.resolve("2025/10/post").also { it.createDirectories() }
        val md = dir.resolve("index.md")
        val meta = dir.resolve("meta.toml")

        // markdown: first line is title, remaining is markdown body
        md.writeText(
            """
            title: My Title
            Hello <em>world</em> and **markdown**
            """.trimIndent()
        )

        // prepare meta with a fixed publishDate; bodyMd5 can be anything
        meta.writeText(
            """
            publishDate = "2025-01-02 03:04:05"
            updateDate = "2025-01-02 03:04:05"
            bodyMd5 = ""
            """.trimIndent()
        )

        val entry = loadMarkdown(testConfiguration(), root, md, root)

        entry.title shouldBe "My Title"
        entry.urlPath shouldBe "/2025/10/post/"
        entry.markdown.contains("Hello <em>world</em>") shouldBe true
        entry.html.contains("<em>world</em>") shouldBe true
        entry.body shouldBe "Hello world and markdown"

        val expectedPub = run {
            val jst = ZoneId.of("Asia/Tokyo")
            val gmt = ZoneId.of("GMT")
            convertToRssDateTimeFormat("2025-01-02 03:04:05", jst, gmt)
        }
        entry.publishDate shouldBe expectedPub
        // local publishDate for index listing
        val expectedLocal = run {
            val jst = ZoneId.of("Asia/Tokyo")
            convertToRssDateTimeFormat("2025-01-02 03:04:05", jst, jst)
        }
        entry.publishDateLocal shouldBe expectedLocal
    }

    test("recursiveMarkdownFiles collects index.md from nested directories") {
        val root: Path = createTempDirectory("pologen-rec-")
        val a = root.resolve("a").also { it.createDirectories() }
        val b = root.resolve("b").also { it.createDirectories() }

        a.resolve("index.md").writeText("""
            title: A Title
            A body
        """.trimIndent())
        a.resolve("meta.toml").writeText("""
            publishDate = "2025-01-02 03:04:05"
            updateDate = "2025-01-02 03:04:05"
            bodyMd5 = ""
        """.trimIndent())

        b.resolve("index.md").writeText("""
            title: B Title
            B body
        """.trimIndent())
        b.resolve("meta.toml").writeText("""
            publishDate = "2025-01-02 04:05:06"
            updateDate = "2025-01-02 04:05:06"
            bodyMd5 = ""
        """.trimIndent())

        val list = recursiveMarkdownFiles(testConfiguration(), root, root, root)
        list.size shouldBe 2
        list.map { it.urlPath } shouldContainAll listOf("/a/", "/b/")
    }
})

private fun testConfiguration() = Configuration(
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
