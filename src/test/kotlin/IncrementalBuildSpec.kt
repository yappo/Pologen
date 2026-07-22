package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.config.OgpConfig
import jp.yappo.pologen.infrastructure.markdown.MarkdownService
import jp.yappo.pologen.infrastructure.rendering.SiteRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import javax.imageio.ImageIO
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

    test("second build preserves article image and OGP artifacts") {
        val root = createTempDirectory("pologen-incremental-artifacts-")
        val entryDir = root.resolve("post").also { it.createDirectories() }
        entryDir.resolve("index.md").writeText("title: Entry\n\n![sample](sample.png)\n\nBody")
        val sourceImage = BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            graphics.color = Color.ORANGE
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        ImageIO.write(sourceImage, "png", entryDir.resolve("sample.png").toFile())
        val service = MarkdownService()
        val renderer = SiteRenderer()
        val configuration = sampleConfiguration().copy(
            ogp = OgpConfig(enabled = true, width = 320, height = 168),
        )

        val first = service.collectEntries(configuration, root, root).single()
        first.needsRender shouldBe true
        renderer.renderEntries(configuration, listOf(first))

        val artifacts = listOf(
            entryDir.resolve("index.html"),
            entryDir.resolve("sample-full.png"),
            entryDir.resolve("sample-thumb.png"),
            entryDir.resolve("ogp.png"),
            entryDir.resolve("meta.toml"),
        )
        artifacts.all { it.exists() } shouldBe true
        val unchangedTime = FileTime.fromMillis(1_000_000)
        artifacts.forEach { Files.setLastModifiedTime(it, unchangedTime) }

        val second = service.collectEntries(configuration, root, root).single()
        second.needsRender shouldBe false
        renderer.renderEntries(configuration, listOf(second))

        artifacts.forEach { artifact ->
            Files.getLastModifiedTime(artifact) shouldBe unchangedTime
        }
    }
})
