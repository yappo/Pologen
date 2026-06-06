package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.config.OgpConfig
import jp.yappo.pologen.domain.support.truncateSummary
import jp.yappo.pologen.infrastructure.ogp.OGPGenerator
import jp.yappo.pologen.infrastructure.util.resolveConfiguredPath
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class OgpImageService(
    private val generateImage: (OgpConfig, String, String, String?, Path) -> Unit = OGPGenerator::generate,
) {
    fun prepare(
        configuration: Configuration,
        metaState: EntryMetaState,
        body: String,
        title: String,
        entryFile: Path,
        urlPath: String,
        configBaseDir: Path,
    ): OgpImageMetadata {
        if (!configuration.ogp.enabled) {
            return OgpImageMetadata()
        }

        val ogpPath = entryFile.parent.resolve("ogp.png")
        val description = truncateSummary(body)
        val needsOgp = !ogpPath.isRegularFile() || metaState.entryChanged
        if (needsOgp) {
            try {
                val resolvedFont = resolveConfiguredPath(configBaseDir, configuration.ogp.fontPath)
                val resolvedIcon = resolveConfiguredPath(configBaseDir, configuration.ogp.authorIconPath)
                val resolvedOgp = configuration.ogp.copy(
                    fontPath = resolvedFont?.toString(),
                    authorIconPath = resolvedIcon?.toString(),
                )
                generateImage(
                    resolvedOgp,
                    truncateSummary(configuration.site.title, 60),
                    truncateSummary(title, 80),
                    description,
                    ogpPath,
                )
            } catch (e: Exception) {
                println("Failed to generate OGP image for $entryFile: ${e.message}")
            }
        }

        return OgpImageMetadata(
            imageUrl = resolveOgpUrl(configuration.site.documentBaseUrl, urlPath, ogpPath.fileName.toString()),
            description = description,
        )
    }

    private fun resolveOgpUrl(documentBaseUrl: String, urlPath: String, fileName: String): String {
        val urlSegment = urlPath.trimStart('/')
        val uri = if (urlSegment.isBlank()) {
            URI(documentBaseUrl).resolve(fileName)
        } else {
            URI(documentBaseUrl).resolve("$urlSegment$fileName")
        }
        return uri.normalize().toString()
    }
}

data class OgpImageMetadata(
    val imageUrl: String? = null,
    val description: String? = null,
)
