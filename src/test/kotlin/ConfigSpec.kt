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
            [paths]
            documentRoot = "docs"
            indexHtml = "index.html"
            feedXml = "feed.xml"

            [site]
            blogTopUrl = "/"
            documentBaseUrl = "https://example.com"
            feedXmlUrl = "/feed.xml"
            title = "Example Site"
            description = "Example Description"
            language = "en"
            faviconUrl = "/favicon.png"

            [author]
            name = "@example"
            url = "https://example.com/me"
            iconUrl = "https://example.com/me.png"

            [images]
            thumbWidth = 320
            fullMaxWidth = 1280
            scaleMethod = "speed"
            jpegQuality = 0.8

            [assets]
            stylesheets = ["/custom.css"]
            scripts = ["/custom.js"]

            [sidebar]
            recentEntryCount = 5

            [links]
            Docs = "https://example.com/docs"
            SNS = "https://sns.example.com"
            """.trimIndent()
        )
        val conf = loadConfiguration(file)
        conf.paths.documentRoot shouldBe "docs"
        conf.paths.indexHtml shouldBe "index.html"
        conf.paths.feedXml shouldBe "feed.xml"
        conf.site.blogTopUrl shouldBe "/"
        conf.site.documentBaseUrl shouldBe "https://example.com"
        conf.site.feedXmlUrl shouldBe "/feed.xml"
        conf.site.title shouldBe "Example Site"
        conf.site.description shouldBe "Example Description"
        conf.site.language shouldBe "en"
        conf.site.faviconUrl shouldBe "/favicon.png"
        conf.author.name shouldBe "@example"
        conf.author.url shouldBe "https://example.com/me"
        conf.author.iconUrl shouldBe "https://example.com/me.png"
        conf.images.thumbWidth shouldBe 320
        conf.images.fullMaxWidth shouldBe 1280
        conf.images.scaleMethod shouldBe org.imgscalr.Scalr.Method.SPEED
        conf.images.jpegQuality shouldBe 0.8f
        conf.assets.stylesheets shouldBe listOf("/custom.css")
        conf.assets.scripts shouldBe listOf("/custom.js")
        conf.sidebar.recentEntryCount shouldBe 5
        conf.links["Docs"] shouldBe "https://example.com/docs"
        conf.links["SNS"] shouldBe "https://sns.example.com"
    }

    test("links preserves insertion order and trims surrounding quotes") {
        val tmp = createTempDirectory("pologen-links-")
        val file = tmp.resolve("config.toml")
        file.writeText(
            """
            [paths]
            documentRoot = "docs"
            indexHtml = "index.html"
            feedXml = "feed.xml"

            [site]
            blogTopUrl = "/"
            documentBaseUrl = "https://example.com"
            feedXmlUrl = "/feed.xml"
            title = "Example Site"
            description = "Example Description"
            language = "en"
            faviconUrl = "/favicon.png"

            [author]
            name = "@example"
            url = "https://example.com/me"
            iconUrl = "https://example.com/me.png"

            [links]
            "'a'" = "https://a.example.com"
            "b b" = "https://b.example.com"
            """.trimIndent()
        )
        val conf = loadConfiguration(file)
        val sanitized = sanitizeLinks(conf.links)
        sanitized.keys.toList() shouldBe listOf("'a'", "b b")
        sanitized.values.toList() shouldBe listOf("https://a.example.com", "https://b.example.com")
    }
})
