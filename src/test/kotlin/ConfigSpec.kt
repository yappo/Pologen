import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class ConfigSpec : FunSpec({
    test("loadConfiguration reads TOML into Configuration") {
        val tmp = createTempDirectory("pologen-conf-")
        val file = tmp.resolve("config.toml")
        file.writeText(
            """
            documentRootPath = "docs"
            blogTopUrl = "/"
            documentBaseUrl = "https://example.com"
            feedXmlPath = "feed.xml"
            feedXmlUrl = "/feed.xml"
            indexHtmlPath = "index.html"
            """.trimIndent()
        )
        val conf = loadConfiguration(file)
        conf.documentRootPath shouldBe "docs"
        conf.blogTopUrl shouldBe "/"
        conf.documentBaseUrl shouldBe "https://example.com"
        conf.feedXmlPath shouldBe "feed.xml"
        conf.feedXmlUrl shouldBe "/feed.xml"
        conf.indexHtmlPath shouldBe "index.html"
    }
})
