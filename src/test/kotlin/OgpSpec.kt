package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.config.OgpConfig
import jp.yappo.pologen.domain.support.sanitizeForOgp
import jp.yappo.pologen.infrastructure.ogp.OGPGenerator
import java.nio.file.Path
import java.awt.Color
import javax.imageio.ImageIO
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

    test("invalid OGP colors fall back to configured defaults") {
        val dir = createTempDirectory("pologen-ogp-color-")
        val out = dir.resolve("ogp.png")
        val config = OgpConfig(
            enabled = true,
            backgroundColor = "invalid",
            titleColor = "invalid",
            bodyColor = "invalid",
            accentColor = "invalid",
        )

        OGPGenerator.generate(config, "Site", "Entry", "Body", out)

        val pixel = Color(ImageIO.read(out.toFile()).getRGB(0, 0), true)
        pixel.red shouldBe 0x10
        pixel.green shouldBe 0x18
        pixel.blue shouldBe 0x27
    }
})
