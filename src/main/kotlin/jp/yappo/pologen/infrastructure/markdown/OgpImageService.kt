package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.config.OgpConfig
import jp.yappo.pologen.domain.support.truncateSummary
import jp.yappo.pologen.domain.support.resolveDocumentUrl
import jp.yappo.pologen.infrastructure.ogp.OGPGenerator
import jp.yappo.pologen.infrastructure.util.resolveConfiguredPath
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
        var imageAvailable = ogpPath.isRegularFile()
        if (needsOgp) {
            val temporaryPath = ogpPath.resolveSibling("${ogpPath.fileName}.tmp")
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
                    temporaryPath,
                )
                require(temporaryPath.isRegularFile()) { "OGP generator did not create $temporaryPath" }
                try {
                    Files.move(
                        temporaryPath,
                        ogpPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporaryPath, ogpPath, StandardCopyOption.REPLACE_EXISTING)
                }
                imageAvailable = true
            } catch (e: Exception) {
                println("Failed to generate OGP image for $entryFile: ${e.message}")
                Files.deleteIfExists(temporaryPath)
                imageAvailable = false
            }
        }

        return OgpImageMetadata(
            imageUrl = if (imageAvailable) {
                resolveOgpUrl(configuration.site.documentBaseUrl, urlPath, ogpPath.fileName.toString())
            } else {
                null
            },
            description = description,
        )
    }

    private fun resolveOgpUrl(documentBaseUrl: String, urlPath: String, fileName: String): String {
        return resolveDocumentUrl(documentBaseUrl, "${urlPath.trimEnd('/')}/$fileName")
    }
}

data class OgpImageMetadata(
    val imageUrl: String? = null,
    val description: String? = null,
)
