package jp.yappo.pologen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
})
