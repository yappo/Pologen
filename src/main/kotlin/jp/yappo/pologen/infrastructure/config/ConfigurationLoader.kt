package jp.yappo.pologen.infrastructure.config

import com.akuleshov7.ktoml.file.TomlFileReader
import jp.yappo.pologen.application.port.ConfigurationReader
import jp.yappo.pologen.domain.config.Configuration
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class ConfigurationLoader(
    private val reader: TomlFileReader = TomlFileReader(),
) : ConfigurationReader {
    override fun load(path: Path): Configuration {
        require(path.isRegularFile()) {
            "Configuration file does not exist: $path"
        }
        return reader.decodeFromFile(Configuration.serializer(), path.toString()).also(::validateConfiguration)
    }
}

internal fun validateConfiguration(configuration: Configuration) {
    require(configuration.paths.documentRoot.isNotBlank()) { "paths.documentRoot must not be blank" }
    require(configuration.paths.indexHtml.isNotBlank()) { "paths.indexHtml must not be blank" }
    require(configuration.paths.feedXml.isNotBlank()) { "paths.feedXml must not be blank" }
    require(configuration.site.title.isNotBlank()) { "site.title must not be blank" }
    require(configuration.site.language.isNotBlank()) { "site.language must not be blank" }
    require(configuration.author.name.isNotBlank()) { "author.name must not be blank" }
    require(configuration.images.thumbWidth > 0) { "images.thumbWidth must be greater than zero" }
    require(configuration.images.fullMaxWidth > 0) { "images.fullMaxWidth must be greater than zero" }
    require(configuration.images.jpegQuality in 0.0f..1.0f) { "images.jpegQuality must be between 0.0 and 1.0" }
    require(configuration.sidebar.recentEntryCount >= 0) { "sidebar.recentEntryCount must not be negative" }
    if (configuration.ogp.enabled) {
        require(configuration.ogp.width > 0) { "ogp.width must be greater than zero" }
        require(configuration.ogp.height > 0) { "ogp.height must be greater than zero" }
    }
    val documentBaseUri = runCatching { URI(configuration.site.documentBaseUrl) }.getOrNull()
    require(
        documentBaseUri?.isAbsolute == true &&
            documentBaseUri.scheme?.lowercase() in setOf("http", "https") &&
            documentBaseUri.rawQuery == null &&
            documentBaseUri.rawFragment == null
    ) {
        "site.documentBaseUrl must be an absolute HTTP or HTTPS URL without a query or fragment"
    }
}
