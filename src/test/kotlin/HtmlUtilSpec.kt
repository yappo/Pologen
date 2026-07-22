package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.support.stripHtml

class HtmlUtilSpec : FunSpec({
    test("stripHtml removes tags and trims") {
        stripHtml("<p>Hello <b>World</b></p>") shouldBe "Hello World"
        stripHtml("  <div> spaced </div>  ") shouldBe "spaced"
        stripHtml("") shouldBe ""
        stripHtml("<p>A &amp; B</p>") shouldBe "A & B"
    }
})
