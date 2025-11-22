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
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.file.TomlFileWriter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import java.net.URI
import java.security.MessageDigest
import kotlin.io.path.*
import org.imgscalr.Scalr
import org.apache.commons.text.StringEscapeUtils
import java.util.UUID
import java.awt.Color
import java.util.LinkedHashMap

data class Entry(
    val filePath: Path,
    val urlPath: String,
    val title: String,
    val publishDate: String,
    val publishDateLocal: String,
    val markdown: String,
    val html: String,
    val body: String,
    val ogpImageUrl: String? = null,
    val ogpDescription: String? = null,
    val toc: List<TocEntry> = emptyList(),
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
    val stylesheets: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
    val ogpEnabled: Boolean = false,
    val ogpWidth: Int = 1200,
    val ogpHeight: Int = 630,
    val ogpBackgroundColor: String = "#101827",
    val ogpTitleColor: String = "#FFFFFF",
    val ogpBodyColor: String = "#E5E7EB",
    val ogpAccentColor: String = "#F97316",
    val ogpFontPath: String? = null,
    val ogpAuthorIconPath: String? = null,
    val recentEntryCount: Int = 10,
    val links: Map<String, String> = emptyMap(),
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
    val bodyMd5: String,
    val title: String? = null,
    val summary: String? = null,
    val toc: List<TocEntry> = emptyList(),
)

@Serializable
data class EntryMetaLegacy(
    val publishDate: String,
    val updateDate: String,
    val bodyMd5: String,
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

/**
 * Produces a safe OGP description by truncating to roughly 100 code points.
 */
fun truncateForOgp(text: String, limit: Int = 100): String {
    val normalized = text.replace("\n", " ").trim()
    var count = 0
    val builder = StringBuilder()
    normalized.codePoints().forEachOrdered { cp ->
        if (count < limit) {
            builder.appendCodePoint(cp)
            count++
        }
    }
    val originalCount = normalized.codePoints().count()
    return if (originalCount > limit) builder.append("…").toString() else builder.toString()
}

/**
 * Generates a summary from plain text, keeping roughly [limit] code points.
 */
fun truncateSummary(text: String, limit: Int = 100): String = truncateForOgp(text, limit)

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

    val baseDir = configFile.parent
    val entries = recursiveMarkdownFiles(
        conf,
        configFile.parent.resolve(conf.documentRootPath).normalize(),
        docsRootDir,
        baseDir)
    copyOverlayScript(docsRootDir)
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

fun recursiveMarkdownFiles(conf: Configuration, rootDirPath: Path, dirPath: Path, configBaseDir: Path) : List<Entry> {
    val entries = mutableListOf<Entry>()
    val indexMdFile = dirPath.resolve("index.md")
    if (Files.exists(indexMdFile)) {
        entries.add(loadMarkdown(conf, rootDirPath, indexMdFile, configBaseDir))
    }

    Files.list(dirPath)
        .sorted(reverseOrder())
        .filter { it.toFile().isDirectory }
        .forEach {
            val childEntries = recursiveMarkdownFiles(conf, rootDirPath, it, configBaseDir)
            entries.addAll(childEntries)
        }

    return entries
}

// TODO: 雑なのちゃんとしよう。。
fun loadMarkdown(conf: Configuration, rootDirPath: Path, filePath: Path, configBaseDir: Path): Entry {
    val lines = Files.readAllLines(filePath)
    val titleLine = lines.first()

    val title = titleLine.removePrefix("title: ").trim().ifBlank { "Untitled" }
    val markdown = lines.drop(1).joinToString("\n").trim()
    val tocItems = extractToc(markdown)

    val relativePath = rootDirPath.relativize(filePath.parent).toString().replace(File.separatorChar, '/')
    val urlPath = "/${if (relativePath.isBlank()) "" else "$relativePath/"}"

    val processedMarkdown = processMarkdownImages(markdown, filePath.parent, conf)
    val markdownWithImages = processedMarkdown.markdown

    // TODO: <body> タグ入れたくないからループ回してるの嫌な感じ
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdownWithImages)
    val htmlGenerated = parsedTree.children.map {
        HtmlGenerator(markdownWithImages, it, flavour).generateHtml()
    }.joinToString(separator = "") { it }
    val htmlWithIds = injectHeadingIds(htmlGenerated, tocItems)
    val html = processedMarkdown.replacements.entries.fold(htmlWithIds) { acc, (placeholder, snippet) ->
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
    val metaSummary = truncateSummary(body)
    var parsedFromLegacy = false
    val tomlLenient = TomlFileReader(
        TomlInputConfig(ignoreUnknownNames = true, allowNullValues = true),
        TomlOutputConfig(),
        EmptySerializersModule()
    )
    val existingMeta = if (metaFilePath.isRegularFile()) {
        runCatching {
            tomlLenient.decodeFromFile(
                EntryMeta.serializer(),
                metaFilePath.toAbsolutePath().toString()
            )
        }.getOrElse { ex ->
            val isMissing = ex::class.simpleName == "MissingRequiredPropertyException"
            if (isMissing) {
                println("Failed to read meta.toml ($metaFilePath), attempting legacy parse. ${ex.message}")
                val legacy = runCatching {
                    tomlLenient.decodeFromFile(EntryMetaLegacy.serializer(), metaFilePath.toAbsolutePath().toString())
                }.getOrNull()
                if (legacy != null) {
                    parsedFromLegacy = true
                    EntryMeta(
                        publishDate = legacy.publishDate,
                        updateDate = legacy.updateDate,
                        bodyMd5 = legacy.bodyMd5,
                        title = title,
                        summary = metaSummary,
                        toc = tocItems
                    )
                } else {
                    null
                }
            } else {
                println("Failed to parse meta.toml ($metaFilePath): ${ex.message}. Recreating.")
                null
            }
        }
    } else {
        null
    }

    val baseMeta = existingMeta ?: EntryMeta(
        publishDate = currentDatTime,
        updateDate = currentDatTime,
        bodyMd5 = bodyDigest,
        title = title,
        summary = metaSummary,
        toc = tocItems
    )
    val needsUpdate = existingMeta == null ||
        baseMeta.bodyMd5 != bodyDigest ||
        baseMeta.title != title ||
        baseMeta.summary != metaSummary ||
        baseMeta.toc != tocItems

    val meta = if (needsUpdate) {
        val updated = baseMeta.copy(
            bodyMd5 = bodyDigest,
            title = title,
            summary = metaSummary,
            toc = tocItems,
            updateDate = currentDatTime
        )
        TomlFileWriter().encodeToFile(
            EntryMeta.serializer(),
            updated,
            metaFilePath.toAbsolutePath().toString()
        )
        val label = if (existingMeta == null) "Created" else "Updated"
        println("$label: ${metaFilePath.toAbsolutePath()}")
        updated
    } else {
        baseMeta
    }

    val publishDate = meta.publishDate

    var ogpImageUrl: String? = null
    var ogpDescription: String? = null
    if (conf.ogpEnabled) {
        val ogpPath = filePath.parent.resolve("ogp.png")
        ogpDescription = truncateForOgp(body)
        val ogpSiteTitle = truncateForOgp(conf.siteTitle, 60)
        val ogpEntryTitle = truncateForOgp(title, 80)
        val needsOgp = !ogpPath.isRegularFile() || meta.bodyMd5 != bodyDigest
        if (needsOgp) {
            try {
                val resolvedFont = resolveConfiguredPath(configBaseDir, conf.ogpFontPath)
                val resolvedIcon = resolveConfiguredPath(configBaseDir, conf.ogpAuthorIconPath)
                val ogpConf = conf.copy(
                    ogpFontPath = resolvedFont?.toString(),
                    ogpAuthorIconPath = resolvedIcon?.toString()
                )
                OGPGenerator.generate(
                    conf = ogpConf,
                    siteTitle = ogpSiteTitle,
                    entryTitle = ogpEntryTitle,
                    description = ogpDescription,
                    output = ogpPath
                )
            } catch (e: Exception) {
                println("Failed to generate OGP image for $filePath: ${e.message}")
            }
        }
        val urlSegment = urlPath.trimStart('/')
        val ogpUrl = if (urlSegment.isBlank()) URI(conf.documentBaseUrl).resolve(ogpPath.fileName.toString())
        else URI(conf.documentBaseUrl).resolve("$urlSegment${ogpPath.fileName}")
        ogpImageUrl = ogpUrl.normalize().toString()
    }

    return Entry(filePath,
        urlPath,
        title,
        convertToRssDateTimeFormat(publishDate, localZoneId, gmtZoneId),
        convertToRssDateTimeFormat(publishDate, localZoneId, localZoneId),
        markdownWithImages,
        html,
        body,
        ogpImageUrl = ogpImageUrl,
        ogpDescription = ogpDescription,
        toc = tocItems)
}


fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), "").trim()
}

fun createEntriesHtml(conf: Configuration, entries: List<Entry>) {
    entries.forEach { createEntryHtml(conf, it, entries) }
    println("Created ${entries.size} entries.")
}

fun createEntryHtml(conf: Configuration, entry: Entry, allEntries: List<Entry>) {
    val recentEntries = buildRecentEntries(conf, allEntries, currentUrlPath = entry.urlPath)
    val content = Templates.renderEntry(conf, entry, recentEntries)
    writeFile(entry.filePath.parent.resolve("index.html"), content)
}


fun createIndexHtml(conf: Configuration, indexHtmlPath: Path, entries: List<Entry>) {
    val recentEntries = buildRecentEntries(conf, entries, currentUrlPath = null)
    val content = Templates.renderIndex(conf, entries, recentEntries)
    writeFile(indexHtmlPath, content)
}

fun createRssXML(conf: Configuration, feedXmlPath: Path, entries: List<Entry>) {
    val content = Templates.renderFeed(conf, entries)
    writeFile(feedXmlPath, content)
}

/**
 * Builds a list of recent entries limited by configuration.
 */
fun buildRecentEntries(conf: Configuration, entries: List<Entry>, currentUrlPath: String?): List<RecentEntry> {
    return entries.take(conf.recentEntryCount).map {
        val href = URI(conf.documentBaseUrl + it.urlPath).normalize().toString()
        RecentEntry(
            title = it.title,
            href = href,
            publishDateLocal = it.publishDateLocal,
            isCurrent = currentUrlPath != null && currentUrlPath == it.urlPath,
        )
    }
}

/**
 * Rewrites Markdown image syntax to responsive HTML placeholders and generates resized assets.
 */
data class ProcessedMarkdown(
    val markdown: String,
    val replacements: Map<String, String>,
)

fun extractToc(markdown: String): List<TocEntry> {
    val toc = mutableListOf<TocEntry>()
    markdown.lines().forEach { line ->
        val trimmed = line.trimStart()
        val level = when {
            trimmed.startsWith("### ") -> 3
            trimmed.startsWith("## ") -> 2
            else -> null
        }
        if (level != null) {
            val text = trimmed.removePrefix("#".repeat(level)).trim()
            val id = slugify(text)
            toc.add(TocEntry(level, text, id))
        }
    }
    return toc
}

fun slugify(text: String): String {
    val normalized = text.lowercase().trim()
    val cleaned = normalized
        .replace(Regex("[^a-z0-9\\s-]"), " ")
        .trim()
        .replace(Regex("\\s+"), "-")
    if (cleaned.isNotBlank()) {
        return cleaned
    }
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "heading-${hash.take(16)}"
}

fun injectHeadingIds(html: String, toc: List<TocEntry>): String {
    var result = html
    toc.forEach { item ->
        val tag = "<h${item.level}>"
        val replacement = """<h${item.level} id="${item.id}">"""
        result = result.replaceFirst(tag, replacement)
    }
    return result
}

fun processMarkdownImages(
    markdown: String,
    entryDir: Path,
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
  <button
    type="button"
    class="pologen-image-thumb block"
    data-full-src="${fullName}"
    data-alt="$escapedAlt"
  >
    <img
      src="${thumbName}"
      alt="$escapedAlt"
      loading="lazy"
      class="max-w-full h-auto rounded-xl shadow-md my-4"
    />
  </button>
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

/**
 * Copies the image overlay helper script into the document root so generated HTML can load it.
 */
fun copyOverlayScript(outputRoot: Path) {
    val target = outputRoot.resolve("assets/pologen.js")
    if (target.exists()) return
    val resource = Templates::class.java.classLoader.getResourceAsStream("assets/pologen.js")
        ?: return
    Files.createDirectories(target.parent)
    resource.use { input ->
        Files.copy(input, target)
    }
    println("Created: ${target.toAbsolutePath()}")
}

/**
 * Resolves a configured path relative to the configuration base directory.
 */
fun resolveConfiguredPath(baseDir: Path, pathStr: String?): Path? {
    if (pathStr.isNullOrBlank()) return null
    val candidate = Path.of(pathStr)
    return if (candidate.isAbsolute) candidate.normalize() else baseDir.resolve(candidate).normalize()
}
