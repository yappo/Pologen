package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jp.yappo.pologen.domain.support.resolveDocumentUrl

class UrlSpec : FunSpec({
    test("document URLs preserve a configured base path") {
        resolveDocumentUrl("https://example.com/blog", "/2026/post/") shouldBe
            "https://example.com/blog/2026/post/"
        resolveDocumentUrl("https://example.com/blog/", "2026/post/") shouldBe
            "https://example.com/blog/2026/post/"
    }
})
