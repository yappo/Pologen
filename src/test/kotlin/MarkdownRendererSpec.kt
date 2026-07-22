package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.yappo.pologen.infrastructure.markdown.MarkdownRenderer

class MarkdownRendererSpec : FunSpec({
    test("TOC uses rendered headings, ignores code fences, and makes duplicate ids unique") {
        val rendered = MarkdownRenderer().render(
            """
            ```text
            ## Not a heading
            ```
            ## Same *heading*
            ## Same *heading*
            ### 日本語
            """.trimIndent()
        )

        rendered.toc.map { it.text } shouldBe listOf("Same heading", "Same heading", "日本語")
        rendered.toc.map { it.id } shouldBe listOf(
            "same-heading",
            "same-heading-2",
            rendered.toc.last().id,
        )
        rendered.toc.last().id.startsWith("heading-") shouldBe true
        rendered.html shouldContain "id=\"same-heading-2\""
    }

    test("raw HTML remains available for trusted article content") {
        val rendered = MarkdownRenderer().render("<video src=\"demo.mp4\" controls></video>")

        rendered.html shouldContain "<video src=\"demo.mp4\" controls></video>"
    }
})
