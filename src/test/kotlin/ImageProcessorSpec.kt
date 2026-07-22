package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.config.EntryImageMeta
import jp.yappo.pologen.domain.config.ImagesConfig
import jp.yappo.pologen.domain.support.sha256Hex
import jp.yappo.pologen.infrastructure.markdown.MarkdownImageProcessor
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class ImageProcessorSpec : FunSpec({
    test("PNG artifacts retain their source extension and reuse unchanged files") {
        val dir = createTempDirectory("pologen-image-")
        val source = dir.resolve("sample.png")
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            graphics.color = Color.RED
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        ImageIO.write(image, "png", source.toFile())
        val processor = MarkdownImageProcessor()

        val first = processor.process("![sample](sample.png)", dir, ImagesConfig())
        val artifact = first.images.single()
        artifact.fullPath shouldBe "sample-full.png"
        artifact.thumbPath shouldBe "sample-thumb.png"
        dir.resolve(artifact.fullPath).readBytes().take(4) shouldBe
            listOf(0x89.toByte(), 0x50, 0x4e, 0x47)

        val oldTime = FileTime.fromMillis(1_000_000)
        Files.setLastModifiedTime(dir.resolve(artifact.fullPath), oldTime)
        Files.setLastModifiedTime(dir.resolve(artifact.thumbPath), oldTime)
        processor.process("![sample](sample.png)", dir, ImagesConfig(), first.images)

        Files.getLastModifiedTime(dir.resolve(artifact.fullPath)) shouldBe oldTime
        Files.getLastModifiedTime(dir.resolve(artifact.thumbPath)) shouldBe oldTime

        val changedImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        ImageIO.write(changedImage, "png", source.toFile())
        val changed = processor.process("![sample](sample.png)", dir, ImagesConfig(), first.images)

        (changed.images.single().sourceSha256 == artifact.sourceSha256) shouldBe false
        (Files.getLastModifiedTime(dir.resolve(artifact.fullPath)) == oldTime) shouldBe false
    }

    test("JPEG and GIF artifacts retain their source extensions and formats") {
        val cases = listOf(
            Triple("jpeg", listOf(0xff.toByte(), 0xd8.toByte()), 2),
            Triple("gif", "GIF".encodeToByteArray().toList(), 3),
        )
        cases.forEach { (extension, signature, signatureLength) ->
            val dir = createTempDirectory("pologen-$extension-")
            val source = dir.resolve("sample.$extension")
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).apply {
                val graphics = createGraphics()
                graphics.color = Color.RED
                graphics.fillRect(0, 0, width, height)
                graphics.dispose()
            }
            ImageIO.write(image, extension, source.toFile())

            val result = MarkdownImageProcessor().process(
                "![sample](sample.$extension)",
                dir,
                ImagesConfig(),
            )

            val artifact = result.images.single()
            artifact.fullPath shouldBe "sample-full.$extension"
            artifact.thumbPath shouldBe "sample-thumb.$extension"
            dir.resolve(artifact.fullPath).readBytes().take(signatureLength) shouldBe signature
            dir.resolve(artifact.thumbPath).readBytes().take(signatureLength) shouldBe signature
        }
    }

    test("old JPEG cache entries do not prevent source-format artifacts from being generated") {
        val dir = createTempDirectory("pologen-image-migration-")
        val source = dir.resolve("sample.png")
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "png", source.toFile())
        val oldFull = dir.resolve("sample-full.jpg")
        val oldThumb = dir.resolve("sample-thumb.jpg")
        ImageIO.write(image, "jpg", oldFull.toFile())
        ImageIO.write(image, "jpg", oldThumb.toFile())
        val oldConfigSha256 = sha256Hex(
            "${ImagesConfig().thumbWidth}:${ImagesConfig().fullMaxWidth}:${ImagesConfig().scaleMethod}:${ImagesConfig().jpegQuality}"
        )
        val previous = EntryImageMeta(
            sourcePath = "sample.png",
            sourceSha256 = sha256Hex(source),
            fullPath = "sample-full.jpg",
            thumbPath = "sample-thumb.jpg",
            configSha256 = oldConfigSha256,
        )

        val result = MarkdownImageProcessor().process(
            "![sample](sample.png)",
            dir,
            ImagesConfig(),
            listOf(previous),
        )

        result.images.single().fullPath shouldBe "sample-full.png"
        result.images.single().thumbPath shouldBe "sample-thumb.png"
        dir.resolve("sample-full.png").exists() shouldBe true
        dir.resolve("sample-thumb.png").exists() shouldBe true
        oldFull.exists() shouldBe true
        oldThumb.exists() shouldBe true
    }

    test("unsupported WebP input is left unchanged") {
        val dir = createTempDirectory("pologen-webp-")
        val source = dir.resolve("sample.webp")
        Files.write(
            source,
            Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/vuUAAA="
            )
        )

        val markdown = "![sample](sample.webp)"
        val result = MarkdownImageProcessor().process(
            markdown,
            dir,
            ImagesConfig(),
        )

        result.markdown shouldBe markdown
        result.images.shouldBeEmpty()
        dir.resolve("sample-full.webp").exists() shouldBe false
        dir.resolve("sample-thumb.webp").exists() shouldBe false
    }
})
