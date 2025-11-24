package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class EntryMetaParseSpec : FunSpec({
    test("meta with toc array parses into EntryMeta") {
        val tempDir = createTempDirectory("meta-parse")
        val metaFile = tempDir.resolve("meta.toml")
        metaFile.writeText(
            """
            publishDate = "2024-01-01 09:00:00"
            updateDate = "2024-01-02 12:00:00"
            bodyMd5 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            title = "Sample entry title"
            summary = "Sample summary that describes the entry body contents in a single sentence."
            
            [[toc]]
                level = 2
                text = "Getting started"
                id = "getting-started"
            
            [[toc]]
                level = 3
                text = "OGP Image"
                id = "ogp-image"
            
            [[toc]]
                level = 3
                text = "Sidebar"
                id = "sidebar"
            
            [[toc]]
                level = 3
                text = "TOC"
                id = "toc"
            
            [[toc]]
                level = 3
                text = "Meta handling"
                id = "meta-handling"
            
            [[toc]]
                level = 2
                text = "Styling"
                id = "styling"
            
            [[toc]]
                level = 2
                text = "Conclusion"
                id = "conclusion"
            """.trimIndent()
        )

        val meta = TomlReaders.decodeMeta(EntryMeta.serializer(), metaFile)
        meta.publishDate shouldBe "2024-01-01 09:00:00"
        meta.toc.size shouldBe 7
        meta.toc.first().text shouldBe "Getting started"
    }
})
