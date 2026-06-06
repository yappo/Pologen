package jp.yappo.pologen.infrastructure.markdown

import jp.yappo.pologen.domain.config.ImagesConfig
import jp.yappo.pologen.infrastructure.image.generateResizedImages
import org.apache.commons.text.StringEscapeUtils
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

class MarkdownImageProcessor {
    fun process(markdown: String, entryDir: Path, config: ImagesConfig): ProcessedMarkdown {
        val replacements = mutableMapOf<String, String>()
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
            val extension = originalName.substringAfterLast('.', "jpg")
            val destFull = entryDir.resolve("$baseName-full.$extension")
            val destThumb = entryDir.resolve("$baseName-thumb.$extension")

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

            val placeholder = UUID.randomUUID().toString()
            replacements[placeholder] = imageSnippet(
                fullPath = destFull.relativeTo(entryDir).toString().replace(File.separatorChar, '/'),
                thumbPath = destThumb.relativeTo(entryDir).toString().replace(File.separatorChar, '/'),
                altText = altText,
            )
            placeholder
        }
        return ProcessedMarkdown(updated, replacements)
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
)
