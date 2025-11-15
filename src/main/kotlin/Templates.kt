package jp.yappo.pologen

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.ResourceCodeResolver
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

object Templates {
    private val templateEngine: TemplateEngine by lazy {
        val classDirectory = Files.createTempDirectory("pologen-jte-classes").apply {
            toFile().deleteOnExit()
        }
        val resolver = ResourceCodeResolver("templates")
        val parentClassLoader = Templates::class.java.classLoader
        TemplateEngine.create(resolver, classDirectory, ContentType.Html, parentClassLoader)
    }

    fun renderEntry(conf: Configuration, entry: Entry): String {
        val model = EntryPageModel(
            site = conf.toSiteMeta(),
            title = entry.title,
            publishDateLocal = entry.publishDateLocal,
            bodyHtml = entry.html,
        )
        val output = StringOutput()
        templateEngine.render("entry.kte", model, output)
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
        templateEngine.render("index.kte", model, output)
        return output.toString()
    }

    private fun Configuration.toSiteMeta(): SiteMeta {
        return SiteMeta(
            title = SITE_TITLE,
            blogTopUrl = blogTopUrl,
            feedXmlUrl = feedXmlUrl,
        )
    }
}
