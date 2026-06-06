package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.config.EntryMeta
import jp.yappo.pologen.domain.config.OgpConfig
import jp.yappo.pologen.infrastructure.markdown.EntryMetaState
import jp.yappo.pologen.infrastructure.markdown.OgpImageService
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class OgpImageServiceSpec : FunSpec({
    test("prepare regenerates an existing OGP image when entry metadata changed") {
        val entryDir = createTempDirectory("pologen-ogp-service-")
        entryDir.resolve("ogp.png").writeText("existing")
        val entryFile = entryDir.resolve("index.md")
        var generateCount = 0
        val service = OgpImageService { _, _, _, _, _ ->
            generateCount++
        }

        service.prepare(
            configuration = sampleConfiguration().copy(ogp = OgpConfig(enabled = true)),
            metaState = EntryMetaState(sampleMeta(), entryChanged = true),
            body = "Body",
            title = "Title",
            entryFile = entryFile,
            urlPath = "/post/",
            configBaseDir = Path.of("/tmp/pologen"),
        )

        generateCount shouldBe 1
    }

    test("prepare skips an existing OGP image when entry metadata is unchanged") {
        val entryDir = createTempDirectory("pologen-ogp-service-")
        entryDir.resolve("ogp.png").writeText("existing")
        val entryFile = entryDir.resolve("index.md")
        var generateCount = 0
        val service = OgpImageService { _, _, _, _, _ ->
            generateCount++
        }

        service.prepare(
            configuration = sampleConfiguration().copy(ogp = OgpConfig(enabled = true)),
            metaState = EntryMetaState(sampleMeta(), entryChanged = false),
            body = "Body",
            title = "Title",
            entryFile = entryFile,
            urlPath = "/post/",
            configBaseDir = Path.of("/tmp/pologen"),
        )

        generateCount shouldBe 0
    }
})

private fun sampleMeta(): EntryMeta = EntryMeta(
    publishDate = "2025-01-02 03:04:05",
    updateDate = "2025-01-02 03:04:05",
    bodyMd5 = "body",
    title = "Title",
    summary = "Body",
)
