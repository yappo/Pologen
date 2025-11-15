package jp.yappo.pologen

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.ResourceCodeResolver
import org.apache.commons.text.StringEscapeUtils
import java.net.URI
import java.nio.file.Files

private const val SITE_TITLE = "YappoLogs2"

data class SiteMeta(
    val title: String,
    val blogTopUrl: String,
    val feedXmlUrl: String,
)

data class EntryPageModel(
    val site: SiteMeta,
    val title: String,
    val publishDateLocal: String,
    val bodyHtml: String,
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
)

data class FeedEntryModel(
    val title: String,
    val link: String,
    val publishDate: String,
    val summary: String,
)

data class FeedPageModel(
    val site: SiteMeta,
    val channelLink: String,
    val channelFeedUrl: String,
    val lastPublishDate: String,
    val entries: List<FeedEntryModel>,
)

object Templates {
    private val htmlTemplateEngine: TemplateEngine by lazy { createEngine(ContentType.Html) }
    private val plainTemplateEngine: TemplateEngine by lazy { createEngine(ContentType.Plain) }

    fun renderEntry(conf: Configuration, entry: Entry): String {
        val model = EntryPageModel(
            site = conf.toSiteMeta(),
            title = entry.title,
            publishDateLocal = entry.publishDateLocal,
            bodyHtml = entry.html,
        )
        val output = StringOutput()
        htmlTemplateEngine.render("entry.kte", model, output)
        return output.toString()
    }

    fun renderIndex(conf: Configuration, entries: List<Entry>): String {
        val viewEntries = entries.map { entry ->
            val href = URI(conf.documentBaseUrl + entry.urlPath).normalize().toString()
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
        )
        val output = StringOutput()
        htmlTemplateEngine.render("index.kte", model, output)
        return output.toString()
    }

    fun renderFeed(conf: Configuration, entries: List<Entry>): String {
        val feedEntries = entries.map { entry ->
            val href = URI(conf.documentBaseUrl + entry.urlPath).normalize().toString()
            FeedEntryModel(
                title = entry.title,
                link = href,
                publishDate = entry.publishDate,
                summary = StringEscapeUtils.escapeXml10(entry.summary),
            )
        }
        val model = FeedPageModel(
            site = conf.toSiteMeta(),
            channelLink = conf.blogTopUrl,
            channelFeedUrl = conf.feedXmlUrl,
            lastPublishDate = entries.firstOrNull()?.publishDate ?: "",
            entries = feedEntries,
        )
        val output = StringOutput()
        plainTemplateEngine.render("feed.kte", model, output)
        return output.toString()
    }

    private fun Configuration.toSiteMeta(): SiteMeta {
        return SiteMeta(
            title = SITE_TITLE,
            blogTopUrl = blogTopUrl,
            feedXmlUrl = feedXmlUrl,
        )
    }

    private fun createEngine(contentType: ContentType): TemplateEngine {
        val classDirectory = Files.createTempDirectory("pologen-jte-${contentType.name.lowercase()}").apply {
            toFile().deleteOnExit()
        }
        val resolver = ResourceCodeResolver("templates")
        val parentClassLoader = Templates::class.java.classLoader
        return TemplateEngine.create(resolver, classDirectory, contentType, parentClassLoader)
    }
}
