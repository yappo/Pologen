package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.config.ImagesConfig
import jp.yappo.pologen.infrastructure.markdown.MarkdownImageProcessor
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes

class ImageProcessorSpec : FunSpec({
    test("non-JPEG input produces JPEG-named artifacts and reuses unchanged files") {
        val dir = createTempDirectory("pologen-image-")
        val source = dir.resolve("sample.gif")
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            graphics.color = Color.RED
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        ImageIO.write(image, "gif", source.toFile())
        val processor = MarkdownImageProcessor()

        val first = processor.process("![sample](sample.gif)", dir, ImagesConfig())
        val artifact = first.images.single()
        artifact.fullPath shouldBe "sample-full.jpg"
        artifact.thumbPath shouldBe "sample-thumb.jpg"
        dir.resolve(artifact.fullPath).readBytes().take(2) shouldBe listOf(0xff.toByte(), 0xd8.toByte())

        val oldTime = FileTime.fromMillis(1_000_000)
        Files.setLastModifiedTime(dir.resolve(artifact.fullPath), oldTime)
        Files.setLastModifiedTime(dir.resolve(artifact.thumbPath), oldTime)
        processor.process("![sample](sample.gif)", dir, ImagesConfig(), first.images)

        Files.getLastModifiedTime(dir.resolve(artifact.fullPath)) shouldBe oldTime
        Files.getLastModifiedTime(dir.resolve(artifact.thumbPath)) shouldBe oldTime

        val changedImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        ImageIO.write(changedImage, "gif", source.toFile())
        val changed = processor.process("![sample](sample.gif)", dir, ImagesConfig(), first.images)

        (changed.images.single().sourceSha256 == artifact.sourceSha256) shouldBe false
        (Files.getLastModifiedTime(dir.resolve(artifact.fullPath)) == oldTime) shouldBe false
    }

    test("WebP input produces JPEG artifacts") {
        val dir = createTempDirectory("pologen-webp-")
        val source = dir.resolve("sample.webp")
        Files.write(
            source,
            Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/vuUAAA="
            )
        )

        val result = MarkdownImageProcessor().process(
            "![sample](sample.webp)",
            dir,
            ImagesConfig(),
        )

        val artifact = result.images.single()
        artifact.fullPath shouldBe "sample-full.jpg"
        artifact.thumbPath shouldBe "sample-thumb.jpg"
        dir.resolve(artifact.fullPath).readBytes().take(2) shouldBe
            listOf(0xff.toByte(), 0xd8.toByte())
        dir.resolve(artifact.thumbPath).readBytes().take(2) shouldBe
            listOf(0xff.toByte(), 0xd8.toByte())
    }
})
