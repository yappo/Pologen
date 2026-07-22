package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.domain.config.ImagesConfig
import jp.yappo.pologen.domain.config.EntryImageMeta
import jp.yappo.pologen.domain.support.sha256Hex
import jp.yappo.pologen.infrastructure.image.generateResizedImages
import org.apache.commons.text.StringEscapeUtils
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

class MarkdownImageProcessor {
    fun process(
        markdown: String,
        entryDir: Path,
        config: ImagesConfig,
        previousImages: List<EntryImageMeta> = emptyList(),
    ): ProcessedMarkdown {
        val replacements = mutableMapOf<String, String>()
        val images = mutableListOf<EntryImageMeta>()
        val configSha256 = sha256Hex(
            "${config.thumbWidth}:${config.fullMaxWidth}:${config.scaleMethod}:${config.jpegQuality}"
        )
        val updated = IMAGE_REGEX.replace(markdown) { matchResult ->
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
            val destFull = entryDir.resolve("$baseName-full.jpg")
            val destThumb = entryDir.resolve("$baseName-thumb.jpg")
            val sourcePathText = sourcePath.relativeTo(entryDir).toString().replace(File.separatorChar, '/')
            val sourceSha256 = sha256Hex(sourcePath)
            val previous = previousImages.firstOrNull {
                it.sourcePath == sourcePathText &&
                    it.sourceSha256 == sourceSha256 &&
                    it.configSha256 == configSha256 &&
                    entryDir.resolve(it.fullPath).isRegularFile() &&
                    entryDir.resolve(it.thumbPath).isRegularFile()
            }

            if (previous == null) {
                try {
                    generateResizedImages(
                        source = sourcePath,
                        destFull = destFull,
                        destThumb = destThumb,
                        fullMaxWidth = config.fullMaxWidth,
                        thumbWidth = config.thumbWidth,
                        scaleMethod = config.scaleMethod,
                        jpegQuality = config.jpegQuality,
                    )
                } catch (e: Exception) {
                    println("Failed to resize image $sourcePath: ${e.message}")
                    return@replace matchResult.value
                }
            }

            val imageMeta = EntryImageMeta(
                sourcePath = sourcePathText,
                sourceSha256 = sourceSha256,
                fullPath = destFull.relativeTo(entryDir).toString().replace(File.separatorChar, '/'),
                thumbPath = destThumb.relativeTo(entryDir).toString().replace(File.separatorChar, '/'),
                configSha256 = configSha256,
            )
            images += imageMeta

            val placeholder = UUID.randomUUID().toString()
            replacements[placeholder] = imageSnippet(
                fullPath = imageMeta.fullPath,
                thumbPath = imageMeta.thumbPath,
                altText = altText,
            )
            placeholder
        }
        return ProcessedMarkdown(updated, replacements, images)
    }

    private fun imageSnippet(fullPath: String, thumbPath: String, altText: String): String {
        val escapedAlt = StringEscapeUtils.escapeHtml4(altText)
        return """
            <button type="button" class="pologen-image-thumb block" data-full-src="./$fullPath" data-alt="$escapedAlt">
                <img src="./$thumbPath" alt="$escapedAlt" loading="lazy" class="max-w-full h-auto rounded-xl shadow-md my-4"/>
            </button>
        """.trimIndent()
    }

    private companion object {
        val IMAGE_REGEX = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
    }
}

data class ProcessedMarkdown(
    val markdown: String,
    val replacements: Map<String, String>,
    val images: List<EntryImageMeta>,
)
