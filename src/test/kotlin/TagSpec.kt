package jp.yappo.pologen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.yappo.pologen.domain.config.TagsConfig
import jp.yappo.pologen.infrastructure.config.validateConfiguration
import jp.yappo.pologen.infrastructure.markdown.EntryMetaStore
import jp.yappo.pologen.infrastructure.markdown.MarkdownService
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import jp.yappo.pologen.infrastructure.rendering.tagSlug
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class TagSpec : FunSpec({
    test("tag declarations generate tag pages and ranked related entries") {
        val root = createTempDirectory("pologen-tags-")
        writeEntry(root, "current", "Current", listOf("Kotlin", "Web", "Kotlin"))
        writeEntry(root, "both", "Both tags", listOf("Web", "Kotlin"))
        writeEntry(root, "one", "One tag", listOf("Kotlin"))
        writeEntry(root, "japanese", "Japanese", listOf("日本語"))
        writeEntry(root, "dotdot", "Dot dot", listOf(".."))
        val configuration = sampleConfiguration().copy(
            tags = TagsConfig(enabled = true, relatedEntryCount = 2),
        )
        val service = MarkdownService()
        val renderer = SiteRenderer()

        val entries = service.collectEntries(configuration, root, root)
        val current = entries.single { it.title == "Current" }
        current.tags shouldBe listOf("Kotlin", "Web")
        EntryMetaStore().read(current.filePath.parent.resolve("meta.toml"))?.tags shouldBe listOf("Kotlin", "Web")

        renderer.renderEntries(configuration, entries)
        renderer.renderTags(configuration, root.resolve("tags"), entries)

        val currentHtml = current.filePath.parent.resolve("index.html").readText()
        currentHtml shouldContain "#Kotlin"
        currentHtml shouldContain "href=\"https://example.com/tags/Kotlin/\""
        currentHtml shouldContain "Related posts"
        (currentHtml.indexOf("Both tags") < currentHtml.indexOf("One tag")) shouldBe true

        val tagIndex = root.resolve("tags/index.html").readText()
        tagIndex shouldContain "#Kotlin"
        tagIndex shouldContain ">3<"
        root.resolve("tags/${tagSlug("日本語")}/index.html").exists() shouldBe true
        root.resolve("tags/%2E%2E/index.html").exists() shouldBe true
        root.resolve("index.html").exists() shouldBe false
        val kotlinPage = root.resolve("tags/Kotlin/index.html").readText()
        kotlinPage shouldContain "Current"
        kotlinPage shouldContain "Both tags"
        kotlinPage shouldContain "One tag"

        service.collectEntries(configuration, root, root).all { !it.needsRender } shouldBe true
        writeEntry(root, "both", "Both tags", listOf("Web"))
        service.collectEntries(configuration, root, root).all { it.needsRender } shouldBe true
    }

    test("tag output cannot escape the document root") {
        shouldThrow<IllegalArgumentException> {
            validateConfiguration(
                sampleConfiguration().copy(tags = TagsConfig(enabled = true, output = "../tags"))
            )
        }
    }
})

private fun writeEntry(root: java.nio.file.Path, directory: String, title: String, tags: List<String>) {
    val entryDir = root.resolve(directory).also { it.createDirectories() }
    entryDir.resolve("index.md").writeText(
        "title: $title\ntags: ${tags.joinToString(", ")}\n\nBody for $title"
    )
}
