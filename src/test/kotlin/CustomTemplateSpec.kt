package jp.yappo.pologen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.yappo.pologen.domain.config.TemplatesConfig
import jp.yappo.pologen.infrastructure.config.ConfigurationLoader
import jp.yappo.pologen.infrastructure.markdown.MarkdownService
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

class CustomTemplateSpec : FunSpec({
    test("configuration resolves a complete custom template directory relative to config.toml") {
        val root = createTempDirectory("pologen-custom-template-config-")
        val templateDir = root.resolve("templates").also { it.createDirectories() }
        writeCustomTemplates(templateDir, "CUSTOM")
        val configPath = root.resolve("config.toml")
        configPath.writeText(configurationToml())

        val configuration = ConfigurationLoader().load(configPath)

        configuration.templates.directory shouldBe templateDir.toString()
    }

    test("configuration rejects an incomplete custom template directory") {
        val root = createTempDirectory("pologen-custom-template-missing-")
        val templateDir = root.resolve("templates").also { it.createDirectories() }
        templateDir.resolve("entry.kte").writeText("entry")
        templateDir.resolve("index.kte").writeText("index")
        val configPath = root.resolve("config.toml")
        configPath.writeText(configurationToml())

        val error = shouldThrow<IllegalArgumentException> {
            ConfigurationLoader().load(configPath)
        }

        error.message shouldContain templateDir.resolve("feed.kte").toString()
    }

    test("custom templates render all outputs and changes invalidate cached entries") {
        val root = createTempDirectory("pologen-custom-template-render-")
        val entryDir = root.resolve("post").also { it.createDirectories() }
        entryDir.resolve("index.md").writeText("title: Custom entry\n\nBody")
        val templateDir = root.resolve("templates").also { it.createDirectories() }
        writeCustomTemplates(templateDir, "FIRST")
        val configuration = sampleConfiguration().copy(
            templates = TemplatesConfig(directory = templateDir.toString()),
        )
        val service = MarkdownService()
        val renderer = SiteRenderer()

        val first = service.collectEntries(configuration, root, root).single()
        renderer.renderEntries(configuration, listOf(first))
        renderer.renderIndex(configuration, root.resolve("index.html"), listOf(first))
        renderer.renderFeed(configuration, root.resolve("feed.xml"), listOf(first))
        entryDir.resolve("index.html").readText() shouldContain "FIRST ENTRY Custom entry"
        root.resolve("index.html").readText() shouldContain "FIRST INDEX 1"
        root.resolve("feed.xml").readText() shouldContain "FIRST FEED 1"

        service.collectEntries(configuration, root, root).single().needsRender shouldBe false
        writeCustomTemplates(templateDir, "SECOND")

        val changed = service.collectEntries(configuration, root, root).single()
        changed.needsRender shouldBe true
        renderer.renderEntries(configuration, listOf(changed))
        entryDir.resolve("index.html").readText() shouldContain "SECOND ENTRY Custom entry"
    }
})

private fun writeCustomTemplates(directory: java.nio.file.Path, marker: String) {
    directory.resolve("entry.kte").writeText(
        """
        @import jp.yappo.pologen.infrastructure.rendering.EntryPageModel
        @param model:EntryPageModel
        $marker ENTRY ${'$'}{model.title}
        """.trimIndent()
    )
    directory.resolve("index.kte").writeText(
        """
        @import jp.yappo.pologen.infrastructure.rendering.IndexPageModel
        @param model:IndexPageModel
        $marker INDEX ${'$'}{model.entries.size}
        """.trimIndent()
    )
    directory.resolve("feed.kte").writeText(
        """
        @import jp.yappo.pologen.infrastructure.rendering.FeedPageModel
        @param model:FeedPageModel
        $marker FEED ${'$'}{model.entries.size}
        """.trimIndent()
    )
}

private fun configurationToml(): String =
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

    [author]
    name = "@example"
    url = "https://example.com/me"
    iconUrl = "https://example.com/me.png"

    [templates]
    directory = "templates"
    """.trimIndent()
