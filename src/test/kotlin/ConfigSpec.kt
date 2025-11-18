package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class ConfigSpec : FunSpec({
    test("loadConfiguration reads TOML into Configuration") {
        val tmp = createTempDirectory("pologen-conf-")
        val file = tmp.resolve("config.toml")
        file.writeText(
            """
            documentRootPath = "docs"
            blogTopUrl = "/"
            documentBaseUrl = "https://example.com"
            feedXmlPath = "feed.xml"
            feedXmlUrl = "/feed.xml"
            indexHtmlPath = "index.html"
            siteTitle = "Example Site"
            siteDescription = "Example Description"
            siteLanguage = "en"
            faviconUrl = "/favicon.png"
            authorName = "@example"
            authorUrl = "https://example.com/me"
            authorIconUrl = "https://example.com/me.png"
            imageThumbWidth = 320
            imageFullMaxWidth = 1280
            imageScaleMethod = "speed"
            imageJpegQuality = 0.8
            recentEntryCount = 5
            links = { Docs = "https://example.com/docs", SNS = "https://sns.example.com" }
            stylesheets = ["/custom.css"]
            scripts = ["/custom.js"]
            """.trimIndent()
        )
        val conf = loadConfiguration(file)
        conf.documentRootPath shouldBe "docs"
        conf.blogTopUrl shouldBe "/"
        conf.documentBaseUrl shouldBe "https://example.com"
        conf.feedXmlPath shouldBe "feed.xml"
        conf.feedXmlUrl shouldBe "/feed.xml"
        conf.indexHtmlPath shouldBe "index.html"
        conf.siteTitle shouldBe "Example Site"
        conf.siteDescription shouldBe "Example Description"
        conf.siteLanguage shouldBe "en"
        conf.faviconUrl shouldBe "/favicon.png"
        conf.authorName shouldBe "@example"
        conf.authorUrl shouldBe "https://example.com/me"
        conf.authorIconUrl shouldBe "https://example.com/me.png"
        conf.imageThumbWidth shouldBe 320
        conf.imageFullMaxWidth shouldBe 1280
        conf.imageScaleMethod shouldBe org.imgscalr.Scalr.Method.SPEED
        conf.imageJpegQuality shouldBe 0.8f
        conf.stylesheets shouldBe listOf("/custom.css")
        conf.scripts shouldBe listOf("/custom.js")
        conf.recentEntryCount shouldBe 5
        conf.links["Docs"] shouldBe "https://example.com/docs"
        conf.links["SNS"] shouldBe "https://sns.example.com"
    }

    test("links preserves insertion order and trims surrounding quotes") {
        val tmp = createTempDirectory("pologen-links-")
        val file = tmp.resolve("config.toml")
        file.writeText(
            """
            documentRootPath = "docs"
            blogTopUrl = "/"
            documentBaseUrl = "https://example.com"
            feedXmlPath = "feed.xml"
            feedXmlUrl = "/feed.xml"
            indexHtmlPath = "index.html"
            siteTitle = "Example Site"
            siteDescription = "Example Description"
            siteLanguage = "en"
            faviconUrl = "/favicon.png"
            authorName = "@example"
            authorUrl = "https://example.com/me"
            authorIconUrl = "https://example.com/me.png"
            links = { "'a'" = "https://a.example.com", "b b" = "https://b.example.com" }
            """.trimIndent()
        )
        val conf = loadConfiguration(file)
        val sanitized = sanitizeLinks(conf.links)
        sanitized.keys.toList() shouldBe listOf("'a'", "b b")
        sanitized.values.toList() shouldBe listOf("https://a.example.com", "https://b.example.com")
    }
})
