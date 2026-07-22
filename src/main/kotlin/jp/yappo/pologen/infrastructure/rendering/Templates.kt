package jp.yappo.pologen.infrastructure.rendering

import gg.jte.ContentType
import gg.jte.CodeResolver
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
import jp.yappo.pologen.domain.model.TocEntry
import jp.yappo.pologen.domain.support.resolveDocumentUrl
import org.apache.commons.text.StringEscapeUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

data class AuthorMeta(
    val name: String,
    val url: String,
    val iconUrl: String,
)

data class SiteMeta(
    val title: String,
    val description: String,
    val language: String,
    val blogTopUrl: String,
    val feedXmlUrl: String,
    val faviconUrl: String,
    val stylesheets: List<String>,
    val scripts: List<String>,
    val author: AuthorMeta,
)

data class EntryPageModel(
    val site: SiteMeta,
    val title: String,
    val publishDateLocal: String,
    val bodyHtml: String,
    val permalink: String,
    val shareTargets: List<ShareTarget>,
    val ogpDescription: String?,
    val ogpImageUrl: String?,
    val toc: List<TocEntry>,
    val recentEntries: List<RecentEntry>,
    val links: Map<String, String>,
    val rssUrl: String,
)

data class IndexEntrySummary(
    val title: String,
    val href: String,
    val publishDateLocal: String,
    val summary: String,
)

data class IndexPageModel(
    val site: SiteMeta,
    val entries: List<IndexEntrySummary>,
    val recentEntries: List<RecentEntry>,
    val links: Map<String, String>,
    val rssUrl: String,
)

data class FeedEntryModel(
    val title: String,
    val link: String,
    val publishDate: String,
    val summary: String,
)

data class FeedPageModel(
    val languageEscaped: String,
    val channelLink: String,
    val channelFeedUrl: String,
    val lastPublishDate: String?,
    val entries: List<FeedEntryModel>,
    val siteTitleEscaped: String,
    val siteDescriptionEscaped: String,
)

object Templates {
    private val DEFAULT_STYLESHEETS = listOf(
        "/assets/pologen.css",
    )
    private val DEFAULT_SCRIPTS = listOf(
        "/assets/pologen.js",
    )

    private val htmlTemplateEngine: TemplateEngine by lazy { createEngine(ContentType.Html) }
    private val plainTemplateEngine: TemplateEngine by lazy { createEngine(ContentType.Plain) }
    private val customTemplateEngines = ConcurrentHashMap<TemplateEngineKey, TemplateEngine>()

    fun renderEntry(conf: Configuration, entry: Entry, recentEntries: List<RecentEntry>): String {
        val permalink = resolveDocumentUrl(conf.site.documentBaseUrl, entry.urlPath)
        val model = EntryPageModel(
            site = conf.toSiteMeta(),
            title = entry.title,
            publishDateLocal = entry.publishDateLocal,
            bodyHtml = entry.html,
            permalink = permalink,
            shareTargets = buildShareTargets(permalink, entry.title, conf.site.title),
            ogpDescription = entry.ogpDescription,
            ogpImageUrl = entry.ogpImageUrl,
            toc = entry.toc,
            recentEntries = recentEntries,
            links = sanitizeLinks(conf.links),
            rssUrl = conf.site.feedXmlUrl,
        )
        val output = StringOutput()
        templateEngine(conf, ContentType.Html).render("entry.kte", model, output)
        return output.toString()
    }

    fun renderIndex(conf: Configuration, entries: List<Entry>, recentEntries: List<RecentEntry>): String {
        val viewEntries = entries.map { entry ->
            val href = resolveDocumentUrl(conf.site.documentBaseUrl, entry.urlPath)
            IndexEntrySummary(
                title = entry.title,
                href = href,
                publishDateLocal = entry.publishDateLocal,
                summary = entry.summary,
            )
        }
        val model = IndexPageModel(
            site = conf.toSiteMeta(),
            entries = viewEntries,
            recentEntries = recentEntries,
            links = sanitizeLinks(conf.links),
            rssUrl = conf.site.feedXmlUrl,
        )
        val output = StringOutput()
        templateEngine(conf, ContentType.Html).render("index.kte", model, output)
        return output.toString()
    }

    fun renderFeed(conf: Configuration, entries: List<Entry>): String {
        val feedEntries = entries.map { entry ->
            val href = resolveDocumentUrl(conf.site.documentBaseUrl, entry.urlPath)
            val safeTitle = StringEscapeUtils.escapeXml10(entry.title)
            FeedEntryModel(
                title = safeTitle,
                link = StringEscapeUtils.escapeXml10(href),
                publishDate = entry.publishDate,
                summary = StringEscapeUtils.escapeXml10(entry.summary),
            )
        }
        val model = FeedPageModel(
            languageEscaped = StringEscapeUtils.escapeXml10(conf.site.language),
            channelLink = StringEscapeUtils.escapeXml10(conf.site.blogTopUrl),
            channelFeedUrl = StringEscapeUtils.escapeXml10(conf.site.feedXmlUrl),
            lastPublishDate = entries.firstOrNull()?.publishDate,
            entries = feedEntries,
            siteTitleEscaped = StringEscapeUtils.escapeXml10(conf.site.title),
            siteDescriptionEscaped = StringEscapeUtils.escapeXml10(conf.site.description),
        )
        val output = StringOutput()
        templateEngine(conf, ContentType.Plain).render("feed.kte", model, output)
        return output.toString()
    }

    private fun Configuration.toSiteMeta(): SiteMeta {
        val resolvedStyles = (DEFAULT_STYLESHEETS + assets.stylesheets).distinct()
        val resolvedScripts = (DEFAULT_SCRIPTS + assets.scripts).distinct()
        return SiteMeta(
            title = site.title,
            description = site.description,
            language = site.language,
            blogTopUrl = site.blogTopUrl,
            feedXmlUrl = site.feedXmlUrl,
            faviconUrl = site.faviconUrl,
            stylesheets = resolvedStyles,
            scripts = resolvedScripts,
            author = AuthorMeta(
                name = author.name,
                url = author.url,
                iconUrl = author.iconUrl,
            )
        )
    }

    private fun templateEngine(conf: Configuration, contentType: ContentType): TemplateEngine {
        val customDirectory = conf.templates.directory?.let { Path.of(it).toAbsolutePath().normalize() }
        if (customDirectory == null) {
            return if (contentType == ContentType.Html) htmlTemplateEngine else plainTemplateEngine
        }
        val key = TemplateEngineKey(customDirectory, contentType)
        return customTemplateEngines.computeIfAbsent(key) {
            createEngine(contentType, customDirectory)
        }
    }

    private fun createEngine(contentType: ContentType, customDirectory: Path? = null): TemplateEngine {
        val classDirectory = Files.createTempDirectory("pologen-jte-${contentType.name.lowercase()}").apply {
            toFile().deleteOnExit()
        }
        val resolver: CodeResolver = if (customDirectory == null) {
            ResourceCodeResolver("templates")
        } else {
            DirectoryCodeResolver(customDirectory)
        }
        val parentClassLoader = Templates::class.java.classLoader
        return TemplateEngine.create(resolver, classDirectory, contentType, parentClassLoader)
    }

    private fun buildShareTargets(permalink: String, title: String, siteTitle: String): List<ShareTarget> {
        val encodedUrl = URLEncoder.encode(permalink, StandardCharsets.UTF_8)
        val encodedTitle = URLEncoder.encode("$siteTitle - $title", StandardCharsets.UTF_8)
        val xShareUrl = "https://x.com/intent/post?text=$encodedTitle%20$encodedUrl"
        return listOf(
            ShareTarget(
                name = "x",
                label = "X.com",
                url = xShareUrl,
            )
        )
    }
}

private data class TemplateEngineKey(
    val directory: Path,
    val contentType: ContentType,
)

data class ShareTarget(
    val name: String,
    val label: String,
    val url: String,
)

data class RecentEntry(
    val title: String,
    val href: String,
    val publishDateLocal: String,
    val isCurrent: Boolean = false,
)

fun sanitizeLinks(links: Map<String, String>): Map<String, String> {
    val sanitized = LinkedHashMap<String, String>()
    links.forEach { (k, v) ->
        val trimmed = k.trim()
        val label = when {
            trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2 ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
        sanitized[label] = v
    }
    return sanitized
}
