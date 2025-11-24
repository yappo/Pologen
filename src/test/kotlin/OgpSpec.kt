package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists

class OgpSpec : FunSpec({
    test("sanitizeForOgp limits to 100 code points with ellipsis and unescapes HTML entities") {
        val text = "あ".repeat(150)
        val truncated = sanitizeForOgp("&lt;p>$text&lt;/p>")
        truncated.codePointCount(0, truncated.length) shouldBe 101
        truncated.endsWith("…") shouldBe true
        truncated.contains("<p>") shouldBe true
    }

    test("ogp generation writer creates png when enabled") {
        val dir: Path = createTempDirectory("pologen-ogp-")
        val conf = sampleConfiguration().copy(
            ogp = OgpConfig(enabled = true)
        )
        val out = dir.resolve("ogp/test.png")
        OGPGenerator.generate(conf.ogp, "Site Title", "Entry Title", "Body", out)
        out.exists() shouldBe true
    }
})
