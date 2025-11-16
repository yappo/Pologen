package jp.yappo.pologen

import com.akuleshov7.ktoml.file.TomlFileReader
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
    val siteTitle: String,
    val siteDescription: String,
    val siteLanguage: String,
    val faviconUrl: String,
    val authorName: String,
    val authorUrl: String,
    val authorIconUrl: String,
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
    val content = Templates.renderEntry(conf, entry)
    writeFile(entry.filePath.parent.resolve("index.html"), content)
}


fun createIndexHtml(conf: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
    val content = Templates.renderIndex(conf, entries)
    writeFile(indexHtmlPath, content)
}

fun createRssXML(conf: Configuration, feedXmlPath: Path, entries: List<Entry>) {
    val content = Templates.renderFeed(conf, entries)
    writeFile(feedXmlPath, content)
}

fun writeFile(path: Path, content: String){
    Files.writeString(path, content)
    println("Created: ${path.toAbsolutePath()}")
}
