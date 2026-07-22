package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.infrastructure.markdown.MarkdownService
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText

class IncrementalBuildSpec : FunSpec({
    test("second build reuses unchanged entry and configuration changes invalidate it") {
        val root = createTempDirectory("pologen-incremental-")
        val entryDir = root.resolve("post").also { it.createDirectories() }
        entryDir.resolve("index.md").writeText("title: Entry\n\nBody")
        val service = MarkdownService()
        val configuration = sampleConfiguration()

        val first = service.collectEntries(configuration, root, root).single()
        first.needsRender shouldBe true
        SiteRenderer().renderEntries(configuration, listOf(first))
        entryDir.resolve("index.html").exists() shouldBe true

        val second = service.collectEntries(configuration, root, root).single()
        second.needsRender shouldBe false

        val changedConfiguration = configuration.copy(
            site = configuration.site.copy(description = "Changed")
        )
        val third = service.collectEntries(changedConfiguration, root, root).single()
        third.needsRender shouldBe true
    }
})
