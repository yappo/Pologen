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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI
import java.security.MessageDigest
import kotlin.io.path.*
import org.imgscalr.Scalr
import org.apache.commons.text.StringEscapeUtils
import java.util.UUID

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
    val imageThumbWidth: Int = 480,
    val imageFullMaxWidth: Int = 1920,
    @Serializable(with = ScalrMethodSerializer::class)
    val imageScaleMethod: Scalr.Method = Scalr.Method.QUALITY,
    val imageJpegQuality: Float = 0.9f,
)

/**
 * Maps human-readable configuration strings to the imgscalr resize methods.
 */
object ScalrMethodSerializer : KSerializer<Scalr.Method> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ScalrMethod", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Scalr.Method {
        return when (decoder.decodeString().lowercase()) {
            "speed" -> Scalr.Method.SPEED
            "balanced" -> Scalr.Method.BALANCED
            "ultra_quality" -> Scalr.Method.ULTRA_QUALITY
            "automatic" -> Scalr.Method.AUTOMATIC
            "quality" -> Scalr.Method.QUALITY
            else -> Scalr.Method.QUALITY
        }
    }

    override fun serialize(encoder: Encoder, value: Scalr.Method) {
        val text = when (value) {
            Scalr.Method.SPEED -> "speed"
            Scalr.Method.BALANCED -> "balanced"
            Scalr.Method.QUALITY -> "quality"
            Scalr.Method.ULTRA_QUALITY -> "ultra_quality"
            Scalr.Method.AUTOMATIC -> "automatic"
        }
        encoder.encodeString(text)
    }
}

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
        conf,
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

fun recursiveMarkdownFiles(conf: Configuration, rootDirPath: Path, dirPath: Path) : List<Entry> {
    val entries = mutableListOf<Entry>()
    val indexMdFile = dirPath.resolve("index.md")
    if (Files.exists(indexMdFile)) {
        entries.add(loadMarkdown(conf, rootDirPath, indexMdFile))
    }

    Files.list(dirPath)
        .sorted(reverseOrder())
        .filter { it.toFile().isDirectory }
        .forEach {
            val childEntries = recursiveMarkdownFiles(conf, rootDirPath, it)
            entries.addAll(childEntries)
        }

    return entries
}

// TODO: 雑なのちゃんとしよう。。
fun loadMarkdown(conf: Configuration, rootDirPath: Path, filePath: Path): Entry {
    val lines = Files.readAllLines(filePath)
    val titleLine = lines.first()

    val title = titleLine.removePrefix("title: ").trim().ifBlank { "Untitled" }
    val markdown = lines.drop(1).joinToString("\n").trim()

    val relativePath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')
    val urlPath = "/${if (relativePath.isBlank()) "" else "$relativePath/"}"

    val processedMarkdown = processMarkdownImages(markdown, filePath.parent, urlPath, conf)
    val markdownWithImages = processedMarkdown.markdown

    // TODO: <body> タグ入れたくないからループ回してるの嫌な感じ
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdownWithImages)
    val htmlGenerated = parsedTree.children.map {
        HtmlGenerator(markdownWithImages, it, flavour).generateHtml()
    }.joinToString(separator = "") { it }
    val html = processedMarkdown.replacements.entries.fold(htmlGenerated) { acc, (placeholder, snippet) ->
        acc.replace(placeholder, snippet)
    }
    val body = stripHtml(html)
    val bodyDigest = DIGEST.digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // make an absolute path
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
        urlPath,
        title,
        convertToRssDateTimeFormat(publishDate, localZoneId, gmtZoneId),
        convertToRssDateTimeFormat(publishDate, localZoneId, localZoneId),
        markdownWithImages,
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

/**
 * Rewrites Markdown image syntax to responsive HTML placeholders and generates resized assets.
 */
data class ProcessedMarkdown(
    val markdown: String,
    val replacements: Map<String, String>,
)

fun processMarkdownImages(
    markdown: String,
    entryDir: Path,
    entryUrlPath: String,
    conf: Configuration
): ProcessedMarkdown {
    val imageRegex = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
    val replacements = mutableMapOf<String, String>()
    val updated = imageRegex.replace(markdown) { matchResult ->
        val altText = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
        val relativeSource = matchResult.groupValues.getOrNull(2)?.trim().orEmpty()
        if (relativeSource.isBlank()) {
            return@replace matchResult.value
        }

        val sourcePath = entryDir.resolve(relativeSource).normalize()
        if (!sourcePath.isRegularFile()) {
            println("Image not found, skipping: $sourcePath")
            return@replace matchResult.value
        }

        val originalName = sourcePath.fileName.toString()
        val baseName = originalName.substringBeforeLast(".", originalName)
        val extension = originalName.substringAfterLast('.', "jpg")
        val fullName = "$baseName-full.$extension"
        val thumbName = "$baseName-thumb.$extension"
        val destFull = entryDir.resolve(fullName)
        val destThumb = entryDir.resolve(thumbName)

        try {
            generateResizedImages(
                sourcePath,
                destFull,
                destThumb,
                conf.imageFullMaxWidth,
                conf.imageThumbWidth,
                conf.imageScaleMethod,
                conf.imageJpegQuality
            )
        } catch (e: Exception) {
            println("Failed to resize image $sourcePath: ${e.message}")
            return@replace matchResult.value
        }

        val escapedAlt = StringEscapeUtils.escapeHtml4(altText)
        val snippet = """
<figure class="my-8">
  <a href="${fullName}" target="_blank" rel="noopener">
    <img
      src="${thumbName}"
      alt="$escapedAlt"
      loading="lazy"
      class="max-w-full h-auto rounded-xl shadow-md"
    />
  </a>
</figure>
        """.trimIndent()
        val placeholder = "IMG_PLACEHOLDER_${UUID.randomUUID()}"
        replacements[placeholder] = snippet
        placeholder
    }
    return ProcessedMarkdown(updated, replacements)
}

fun writeFile(path: Path, content: String){
    Files.writeString(path, content)
    println("Created: ${path.toAbsolutePath()}")
}
