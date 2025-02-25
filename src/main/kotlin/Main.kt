import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.text.StringEscapeUtils
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import com.akuleshov7.ktoml.file.TomlFileWriter
import kotlinx.serialization.Serializable
import java.net.URI
import java.security.MessageDigest
import kotlin.io.path.*

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

@Serializable
data class Configuration (
    val documentRootPath: String,
    val blogTopUrl: String,
    val documentBaseUrl: String,
    val feedXmlPath: String,
    val feedXmlUrl: String,
    val indexHtmlPath: String,
)

@Serializable
data class EntryMeta(
    val publishDate: String,
    val updateDate: String,
    val bodyMd5: String
)

val DIGEST = MessageDigest.getInstance("SHA-256") ?: error("Failed to make a digest instance.")

fun convertToRssDateTimeFormat(dateTime: String, fromZoneId: ZoneId, toZoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


    val localDateTime = LocalDateTime.parse(dateTime, formatter)
    val localZonedDateTime = localDateTime.atZone(fromZoneId)
    val gmtZonedDateTime = localZonedDateTime.withZoneSameInstant(toZoneId)

    val rssFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
    return gmtZonedDateTime.format(rssFormatter)
}

fun currentDateTimeInJST(): String {
    val currentDateTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return currentDateTime.format(formatter)
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: $ app config.toml")
        return
    }

    val configFile = Path(args[0]).toAbsolutePath().normalize()
    println("configuration file path: $configFile")
    val conf = loadConfiguration(configFile)

    val docsRootDir = configFile.parent.resolve(conf.documentRootPath).normalize()
    if (!docsRootDir.exists() || !docsRootDir.isDirectory()) {
        println("Invalid docs directory: $docsRootDir")
        return
    }

    val entries = recursiveMarkdownFiles(
        configFile.parent.resolve(conf.documentRootPath).normalize(),
        docsRootDir)
    createEntriesHtml(conf, entries)

    val indexEntries = entries.take(30)
    createIndexHtml(conf, configFile.parent.resolve(conf.indexHtmlPath).normalize(), indexEntries)
    createRssXML(conf, configFile.parent.resolve(conf.feedXmlPath).normalize(), indexEntries)
}

fun loadConfiguration(path: Path): Configuration {
    val configuration = if (path.isRegularFile()) { // TODO: 雑なの直す。。
        TomlFileReader().decodeFromFile(Configuration.serializer(), path.toString())
    } else {
        error("Configuration file does not exists: $path")
    }

    return configuration
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
    val titleLine = lines.first()

    val title = titleLine.removePrefix("title: ").trim().ifBlank { "Untitled" }
    val markdown = lines.drop(1).joinToString("\n").trim()

    // TODO: <body> タグ入れたくないからループ回してるの嫌な感じ
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    val html = parsedTree.children.map {
        HtmlGenerator(markdown, it, flavour).generateHtml()
    }.joinToString(separator = "") { it }
    val body = stripHtml(html)
    val bodyDigest = DIGEST.digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // make an absolute path
    val urlPath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')

    val localZoneId = ZoneId.of("Asia/Tokyo")
    val gmtZoneId = ZoneId.of("GMT")

    // meta file
    val currentDatTime = currentDateTimeInJST()
    val metaFilePath = filePath.parent.resolve("meta.toml")
    val meta = if (metaFilePath.isRegularFile()) { // TODO: 雑なの直す。。
        TomlFileReader().decodeFromFile(EntryMeta.serializer(),
                                        metaFilePath.toAbsolutePath().toString())
    } else {
        val createMeta = EntryMeta(currentDatTime, currentDatTime, bodyDigest)
        TomlFileWriter().encodeToFile(
            EntryMeta.serializer(),
            createMeta,
            metaFilePath.toAbsolutePath().toString()
        )
        println("Created: ${metaFilePath.toAbsolutePath()}")

        createMeta
    }

    val publishDate = if (meta.bodyMd5 != bodyDigest) {
        val newMeta = meta.copy(bodyMd5 = bodyDigest, updateDate = currentDatTime)
        TomlFileWriter().encodeToFile(
            EntryMeta.serializer(),
            newMeta,
            metaFilePath.toAbsolutePath().toString()
        )
        println("Updated: ${metaFilePath.toAbsolutePath()}")

        newMeta.publishDate
    } else {
        meta.publishDate
    }

    return Entry(filePath,
        "/$urlPath/",
        title,
        convertToRssDateTimeFormat(publishDate, localZoneId, gmtZoneId),
        convertToRssDateTimeFormat(publishDate, localZoneId, localZoneId),
        markdown,
        html,
        body)
}


fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), "").trim()
}

fun createEntriesHtml(conf: Configuration, entries: List<Entry>) {
    entries.forEach { createEntryHtml(conf, it) }
    println("Created ${entries.size} entries.")
}

fun createEntryHtml(conf: Configuration, entry: Entry) {
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
                href=conf.feedXmlUrl
            }
        }
        body {
            header {
                h1 {
                    a(conf.blogTopUrl) { +"YappoLogs2" }
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


fun createIndexHtml(conf: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
    val content = createHTML().html {
        lang = "en"
        head {
            meta { charset = "UTF-8" }
            link { rel = "icon"; href = "/favicon.png" }
            link {
                rel = "alternate"
                type = "application/rss+xml"
                title = "RSS Feed"
                href = conf.feedXmlUrl
            }
            title { +"YappoLogs2" }
        }
        body {
            div {
                header {
                    div {
                        a(conf.blogTopUrl) { +"YappoLogs2" }
                    }
                }
                main {
                    ul {
                        entries.forEach { entry ->
                            li {
                                val href = URI(conf.documentBaseUrl + entry.urlPath).normalize().toString()
                                a(href = href) { +entry.title }
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
    writeFile(indexHtmlPath, content)
}

fun createRssXML(conf: Configuration, feedXmlPath: Path, entries: List<Entry>) {
    val lastPublishDate : String = entries.firstOrNull()?.publishDate ?: ""

    val itemsXml = entries.joinToString(separator = "\n") { entry ->
        val link = URI(conf.documentBaseUrl + entry.urlPath).normalize().toString()

        val content = StringEscapeUtils.escapeXml10(entry.summary)

        """
    <item>
        <title>${entry.title}</title>
        <link>$link</link>
        <description/>
        <content:encoded>
$content
        </content:encoded>
        <pubDate>${entry.publishDate}</pubDate>
        <guid>$link</guid>
    </item>
        """.trimIndent()
    }

    val content = """<?xml version="1.0" encoding="UTF-8"?>
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


    writeFile(feedXmlPath, content)
}

fun writeFile(path: Path, content: String){
    Files.writeString(path, content)
    println("Created: ${path.toAbsolutePath()}")
}