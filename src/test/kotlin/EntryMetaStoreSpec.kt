package jp.yappo.pologen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.config.EntryImageMeta
import jp.yappo.pologen.domain.support.sha256Hex
import jp.yappo.pologen.infrastructure.markdown.EntryMetaStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

class EntryMetaStoreSpec : FunSpec({
    test("malformed metadata is reported without overwriting the file") {
        val metaPath = createTempDirectory("pologen-meta-").resolve("meta.toml")
        val malformed = "publishDate = ["
        metaPath.writeText(malformed)

        shouldThrow<IllegalArgumentException> {
            EntryMetaStore().read(metaPath)
        }
        metaPath.readText() shouldBe malformed
    }

    test("changing only the title updates updateDate and preserves publishDate") {
        val metaPath = createTempDirectory("pologen-meta-").resolve("meta.toml")
        val body = "Body"
        val sourceSha = sha256Hex("title: Old\nBody")
        metaPath.writeText(
            """
            publishDate = "2024-01-01 00:00:00"
            updateDate = "2024-01-02 00:00:00"
            bodyMd5 = "${sha256Hex(body)}"
            title = "Old"
            summary = "Body"
            indexSummary = "Body"
            sourceSha256 = "$sourceSha"
            renderConfigSha256 = "config"
            navigationSha256 = "navigation"
            generatorVersion = 1
            """.trimIndent()
        )
        val store = EntryMetaStore(
            clock = Clock.fixed(Instant.parse("2025-02-03T04:05:06Z"), ZoneId.of("Asia/Tokyo"))
        )

        val state = store.synchronize(
            metaFilePath = metaPath,
            body = body,
            title = "New",
            summary = body,
            toc = emptyList(),
            indexSummary = body,
            sourceSha256 = sourceSha,
            renderConfigSha256 = "config",
            navigationSha256 = "navigation",
            images = emptyList(),
        )

        state.meta.publishDate shouldBe "2024-01-01 00:00:00"
        state.meta.updateDate shouldBe "2025-02-03 13:05:06"
    }

    test("derived body changes with unchanged source preserve updateDate") {
        val metaPath = createTempDirectory("pologen-meta-derived-").resolve("meta.toml")
        val sourceSha = sha256Hex("Article title\n\nParagraph\n\n## Heading")
        metaPath.writeText(
            """
            publishDate = "2024-01-01 00:00:00"
            updateDate = "2024-01-02 00:00:00"
            bodyMd5 = "${sha256Hex("ParagraphHeading")}"
            title = "Article title"
            summary = "ParagraphHeading"
            indexSummary = "ParagraphHeading"
            sourceSha256 = "$sourceSha"
            renderConfigSha256 = "old-config"
            navigationSha256 = "navigation"
            generatorVersion = 1
            """.trimIndent()
        )
        val store = EntryMetaStore(
            clock = Clock.fixed(Instant.parse("2025-02-03T04:05:06Z"), ZoneId.of("Asia/Tokyo"))
        )

        val state = store.synchronize(
            metaFilePath = metaPath,
            body = "Paragraph Heading",
            title = "Article title",
            summary = "Paragraph Heading",
            toc = emptyList(),
            indexSummary = "Paragraph Heading",
            sourceSha256 = sourceSha,
            renderConfigSha256 = "new-config",
            navigationSha256 = "navigation",
            images = emptyList(),
        )

        state.meta.bodyMd5 shouldBe sha256Hex("Paragraph Heading")
        state.meta.updateDate shouldBe "2024-01-02 00:00:00"
    }

    test("legacy metadata migration without a source fingerprint preserves updateDate") {
        val metaPath = createTempDirectory("pologen-meta-legacy-").resolve("meta.toml")
        metaPath.writeText(
            """
            publishDate = "2024-01-01 00:00:00"
            updateDate = "2024-01-02 00:00:00"
            bodyMd5 = "${sha256Hex("ParagraphHeading")}"
            title = "Article title"
            summary = "ParagraphHeading"
            """.trimIndent()
        )
        val store = EntryMetaStore(
            clock = Clock.fixed(Instant.parse("2025-02-03T04:05:06Z"), ZoneId.of("Asia/Tokyo"))
        )
        val sourceSha = sha256Hex("Article title\n\nParagraph\n\n## Heading")

        val state = store.synchronize(
            metaFilePath = metaPath,
            body = "Paragraph Heading",
            title = "Article title",
            summary = "Paragraph Heading",
            toc = emptyList(),
            indexSummary = "Paragraph Heading",
            sourceSha256 = sourceSha,
            renderConfigSha256 = "config",
            navigationSha256 = "navigation",
            images = emptyList(),
        )

        state.meta.sourceSha256 shouldBe sourceSha
        state.meta.updateDate shouldBe "2024-01-02 00:00:00"
    }

    test("changing the source fingerprint updates updateDate") {
        val metaPath = createTempDirectory("pologen-meta-source-").resolve("meta.toml")
        metaPath.writeText(
            """
            publishDate = "2024-01-01 00:00:00"
            updateDate = "2024-01-02 00:00:00"
            bodyMd5 = "${sha256Hex("Old body")}"
            title = "Article title"
            summary = "Old body"
            indexSummary = "Old body"
            sourceSha256 = "${sha256Hex("Article title\n\nOld body")}"
            renderConfigSha256 = "config"
            navigationSha256 = "navigation"
            generatorVersion = 2
            """.trimIndent()
        )
        val store = EntryMetaStore(
            clock = Clock.fixed(Instant.parse("2025-02-03T04:05:06Z"), ZoneId.of("Asia/Tokyo"))
        )

        val state = store.synchronize(
            metaFilePath = metaPath,
            body = "New body",
            title = "Article title",
            summary = "New body",
            toc = emptyList(),
            indexSummary = "New body",
            sourceSha256 = sha256Hex("Article title\n\nNew body"),
            renderConfigSha256 = "config",
            navigationSha256 = "navigation",
            images = emptyList(),
        )

        state.meta.updateDate shouldBe "2025-02-03 13:05:06"
    }

    test("image processing changes preserve updateDate but image content changes update it") {
        val metaPath = createTempDirectory("pologen-meta-image-").resolve("meta.toml")
        val sourceSha = sha256Hex("Article title\n\n![image](image.png)")
        metaPath.writeText(
            """
            publishDate = "2024-01-01 00:00:00"
            updateDate = "2024-01-02 00:00:00"
            bodyMd5 = "${sha256Hex("Article body")}"
            title = "Article title"
            summary = "Article body"
            indexSummary = "Article body"
            sourceSha256 = "$sourceSha"
            renderConfigSha256 = "old-config"
            navigationSha256 = "navigation"
            generatorVersion = 2

            [[images]]
                sourcePath = "image.png"
                sourceSha256 = "old-image"
                fullPath = "image-full.jpg"
                thumbPath = "image-thumb.jpg"
                configSha256 = "old-image-config"
            """.trimIndent()
        )
        val store = EntryMetaStore(
            clock = Clock.fixed(Instant.parse("2025-02-03T04:05:06Z"), ZoneId.of("Asia/Tokyo"))
        )
        val reconfiguredImage = EntryImageMeta(
            sourcePath = "image.png",
            sourceSha256 = "old-image",
            fullPath = "image-full.jpg",
            thumbPath = "image-thumb.jpg",
            configSha256 = "new-image-config",
        )

        val reconfigured = store.synchronize(
            metaFilePath = metaPath,
            body = "Article body",
            title = "Article title",
            summary = "Article body",
            toc = emptyList(),
            indexSummary = "Article body",
            sourceSha256 = sourceSha,
            renderConfigSha256 = "new-config",
            navigationSha256 = "navigation",
            images = listOf(reconfiguredImage),
        )

        reconfigured.meta.updateDate shouldBe "2024-01-02 00:00:00"

        val changedImage = reconfiguredImage.copy(sourceSha256 = "new-image")
        val changed = store.synchronize(
            metaFilePath = metaPath,
            body = "Article body",
            title = "Article title",
            summary = "Article body",
            toc = emptyList(),
            indexSummary = "Article body",
            sourceSha256 = sourceSha,
            renderConfigSha256 = "new-config",
            navigationSha256 = "navigation",
            images = listOf(changedImage),
        )

        changed.meta.updateDate shouldBe "2025-02-03 13:05:06"
    }
})
