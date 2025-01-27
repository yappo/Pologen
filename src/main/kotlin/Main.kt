import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class Entry(
    val title: String,
    val publishDate: String,
    val body: String
)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Docs directory path is required.")
        return
    }

    val docsDirPath = args[0]
    val docsDir = File(docsDirPath)

    if (!docsDir.exists() || !docsDir.isDirectory) {
        println("Invalid docs directory: $docsDirPath")
        return
    }

    processDirectory(docsDir.toPath())
}

fun processDirectory(dirPath: Path) {
    val indexMdFile = dirPath.resolve("index.md")
    if (Files.exists(indexMdFile)) {
        val entry: Entry = loadMarkdown(indexMdFile)
        createIndexHtml(dirPath, entry)
    }

    Files.list(dirPath).filter { it.toFile().isDirectory }.forEach { processDirectory(it) }
}

// TODO: 雑なのちゃんとしよう。。
fun loadMarkdown(filePath: Path): Entry {
    val lines = Files.readAllLines(filePath)
    val titleLine = lines.firstOrNull { it.startsWith("title: ") }
        ?: error("Error: Missing or malformed title in file $filePath")
    val dateLine = lines.firstOrNull { it.startsWith("date: ") }
        ?: error("Error: Missing or malformed date in file $filePath")

    val title = titleLine.removePrefix("title: ").trim()
    val date = dateLine.removePrefix("date: ").trim()

    val bodyStartIndex = lines.indexOfFirst { it.isBlank() }
    if (bodyStartIndex <= 0 || bodyStartIndex >= lines.size) {
        error("Error: Missing body content in file $filePath")
    }
    val body = lines.drop(bodyStartIndex).joinToString("\n").trim()

    return Entry(title, date, body)
}


fun createIndexHtml(dirPath: Path, entry: Entry) {
    val indexHtml = dirPath.resolve("index.html")

    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(entry.body)
    val entryTitle = entry.title.ifBlank { "Untitled" }
    val publishDate = entry.publishDate.ifBlank { "UntitledDate" }

    // TODO: <body> タグ入れたくないからループ回してるの嫌な感じ
    val html = parsedTree.children.map {
        HtmlGenerator(entry.body, it, flavour).generateHtml()
    }.joinToString(separator = "") { it }

    val htmlContent = createHTML().html {
        lang = "en"
        head {
            meta { charset = "UTF-8" }
            meta { name = "viewport"; content = "width=device-width,initial-scale=1" }
            title { +"$entryTitle - YappoLogs2" }
            // link { rel = "stylesheet"; href = "/style.css" }
            link { rel="icon"; href="/favicon.png" }
            link {
                rel = "alternate";
                type="application/rss+xml";
                title="RSS Feed";
                href="https://blog.yappo.jp/feed" }
        }
        body {
            header {
                h1 {
                    a("https://blog.yappo.jp/") { +"YappoLogs2" }
                }
            }
            article("section") {
                div("eyecatch") {
                }
                div("container") {
                    h1 { +entryTitle }
                    div {
                        img {
                            src = "https://pbs.twimg.com/profile_images/1770102954382831616/H3LXaTgp_normal.jpg"
                            alt = "yappo"
                            width = "16"
                            height = "16"
                        }
                        +"✍ : $publishDate"}
                    // ここにJetBrains/markdownで変換したHTMLを挿入
                    div {
                        unsafe { +html }
                    }
                }
            }
            footer {
                p {
                    small {
                        a("https://x.com/yappo") { +"@Yappo" }
                    }
                }
            }
        }
    }

    Files.writeString(indexHtml, htmlContent)
    println("Created: ${indexHtml.toAbsolutePath()}")
}

