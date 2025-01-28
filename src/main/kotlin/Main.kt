import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.text.StringEscapeUtils
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Entry(
    val filePath: Path,
    val urlPath: String,
    val title: String,
    val publishDate: String,
    val publishDateLocal: String,
    val markdown: String,
    val html: String,
    val body: String,
) {
    val summary: String by lazy {
        if (body.length > 140) {
            body.take(140) + "..."
        } else {
            body
        }
    }
}

fun convertToRssDateTimeFormat(dateTime: String, fromZoneId: ZoneId, toZoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


    val localDateTime = LocalDateTime.parse(dateTime, formatter)
    val localZonedDateTime = localDateTime.atZone(fromZoneId)
    val gmtZonedDateTime = localZonedDateTime.withZoneSameInstant(toZoneId)

    val rssFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
    return gmtZonedDateTime.format(rssFormatter)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: $ app documentRoot")
        return
    }

    val documentRootPath = args[0]

    val documentRootDir = File(documentRootPath)
    if (!documentRootDir.exists() || !documentRootDir.isDirectory) {
        println("Invalid docs directory: $documentRootPath")
        return
    }

    val docsRootDir = File(documentRootPath).resolve("entry")
    if (!docsRootDir.exists() || !docsRootDir.isDirectory) {
        println("Invalid docs directory: ${docsRootDir.toPath()}")
        return
    }

    val entries = recursiveMarkdownFiles(documentRootDir.toPath(), docsRootDir.toPath())
    createEntriesHtml(entries)

    val indexEntries = entries.take(30)
    createIndexHtml(documentRootDir.toPath(), indexEntries)
    createRssXML(documentRootDir.toPath(), indexEntries)
}

fun recursiveMarkdownFiles(rootDirPath: Path, dirPath: Path) : List<Entry> {
    val entries = mutableListOf<Entry>()
    val indexMdFile = dirPath.resolve("index.md")
    if (Files.exists(indexMdFile)) {
        entries.add(loadMarkdown(rootDirPath, indexMdFile))
    }

    Files.list(dirPath)
        .sorted(reverseOrder())
        .filter { it.toFile().isDirectory }
        .forEach {
            val childEntries = recursiveMarkdownFiles(rootDirPath, it)
            entries.addAll(childEntries)
        }

    return entries
}

// TODO: 雑なのちゃんとしよう。。
fun loadMarkdown(rootDirPath: Path, filePath: Path): Entry {
    val lines = Files.readAllLines(filePath)
    val titleLine = lines.firstOrNull { it.startsWith("title: ") }
        ?: error("Error: Missing or malformed title in file $filePath")
    val dateLine = lines.firstOrNull { it.startsWith("date: ") }
        ?: error("Error: Missing or malformed date in file $filePath")

    val title = titleLine.removePrefix("title: ").trim().ifBlank { "Untitled" }
    val date = dateLine.removePrefix("date: ").trim().ifBlank { "UntitledDate" }

    val bodyStartIndex = lines.indexOfFirst { it.isBlank() }
    if (bodyStartIndex <= 0 || bodyStartIndex >= lines.size) {
        error("Error: Missing body content in file $filePath")
    }
    val markdown = lines.drop(bodyStartIndex).joinToString("\n").trim()

    // TODO: <body> タグ入れたくないからループ回してるの嫌な感じ
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    val html = parsedTree.children.map {
        HtmlGenerator(markdown, it, flavour).generateHtml()
    }.joinToString(separator = "") { it }
    val body = stripHtml(html)

    // make an absolute path
    val urlPath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')

    val localZoneId = ZoneId.of("Asia/Tokyo")
    val gmtZoneId = ZoneId.of("GMT")

    return Entry(filePath,
        "/$urlPath/",
        title,
        convertToRssDateTimeFormat(date, localZoneId, gmtZoneId),
        convertToRssDateTimeFormat(date, localZoneId, localZoneId),
        markdown,
        html,
        body)
}


fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), "").trim()
}

fun createEntriesHtml(entries: List<Entry>) {
    entries.forEach { createEntryHtml(it) }
    println("Created ${entries.size} entries.")
}

fun createEntryHtml(entry: Entry) {
    val content = createHTML().html {
        lang = "en"
        head {
            meta { charset = "UTF-8" }
            meta { name = "viewport"; content = "width=device-width,initial-scale=1" }
            title { +"${entry.title} - YappoLogs2" }
            // link { rel = "stylesheet"; href = "/style.css" }
            link { rel="icon"; href="/favicon.png" }
            link {
                rel = "alternate";
                type="application/rss+xml";
                title="RSS Feed";
                href="https://blog.yappo.jp/feed.xml" }
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
                    h1 { +entry.title }
                    div {
                        img {
                            src = "https://pbs.twimg.com/profile_images/1770102954382831616/H3LXaTgp_normal.jpg"
                            alt = "yappo"
                            width = "16"
                            height = "16"
                        }
                        +"✍ : ${entry.publishDateLocal}"}
                    // ここにJetBrains/markdownで変換したHTMLを挿入
                    div {
                        unsafe { +entry.html }
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
    writeFile(entry.filePath.parent.resolve("index.html"), content)
}


fun createIndexHtml(documentRootDir: Path, entries: List<Entry>) {
    val content = createHTML().html {
        lang = "en"
        head {
            meta { charset = "UTF-8" }
            link { rel = "icon"; href = "/favicon.png" }
            link {
                rel = "alternate"
                type = "application/rss+xml"
                title = "RSS Feed"
                href = "https://blog.yappo.jp/feed.xml"
            }
            title { +"YappoLogs2" }
        }
        body {
            div {
                header {
                    div {
                        a("/") { +"YappoLogs2" }
                    }
                }
                main {
                    ul {
                        entries.forEach { entry ->
                            li {
                                a(href = entry.urlPath) { +entry.title }
                                p { +entry.publishDateLocal }
                                p { +entry.summary }
                            }
                        }
                    }
                }
                footer {
                    div {
                        a("https://x.com/yappo") { +"@Yappo" }
                    }
                }
            }
        }
    }
    writeFile(documentRootDir.resolve("index.html"), content)
}

fun createRssXML(documentRootDir: Path, entries: List<Entry>) {
    val lastPublishDate : String = entries.firstOrNull()?.publishDate ?: ""

    val itemsXml = entries.joinToString(separator = "\n") { entry ->
        val content = StringEscapeUtils.escapeXml10(entry.summary)

        """
    <item>
        <title>${entry.title}</title>
        <link>https://blog.yappo.jp/entry/${entry.urlPath}</link>
        <description/>
        <content:encoded>
$content
        </content:encoded>
        <pubDate>${entry.publishDate}</pubDate>
        <guid>https://blog.yappo.jp/entry/${entry.urlPath}</guid>
    </item>
        """.trimIndent()
    }

    val content = """
<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:atom="http://www.w3.org/2005/Atom" version="2.0">
    <channel>
    <title>YappoLogs2</title>
    <link>https://blog.yappo.jp/</link>
    <atom:link href="https://blog.yappo.jp/feed.xml" rel="self" type="application/rss+xml"/>
    <description>The latest articles from my blog</description>
    <language>en</language>
    <pubDate>$lastPublishDate</pubDate>
$itemsXml
    </channel>
</rss>
"""


    writeFile(documentRootDir.resolve("feed.xml"), content)
}

fun writeFile(path: Path, content: String){
    Files.writeString(path, content)
    println("Created: ${path.toAbsolutePath()}")
}