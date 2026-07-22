package jp.yappo.pologen.infrastructure.markdown

import com.akuleshov7.ktoml.file.TomlFileWriter
import jp.yappo.pologen.domain.config.EntryMeta
import jp.yappo.pologen.domain.config.EntryImageMeta
import jp.yappo.pologen.domain.model.TocEntry
import jp.yappo.pologen.domain.support.sha256Hex
import jp.yappo.pologen.infrastructure.config.TomlReaders
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.isRegularFile

class EntryMetaStore(
    private val clock: Clock = Clock.system(JST),
    private val writer: TomlFileWriter = TomlFileWriter(),
) {
    fun synchronize(
        metaFilePath: Path,
        body: String,
        title: String,
        summary: String,
        toc: List<TocEntry>,
        indexSummary: String,
        sourceSha256: String,
        renderConfigSha256: String,
        navigationSha256: String,
        images: List<EntryImageMeta>,
        initialPublishDate: String = currentDateTimeInJst(),
        existingMeta: EntryMeta? = read(metaFilePath),
    ): EntryMetaState {
        val bodyDigest = sha256Hex(body)
        val currentDateTime = LocalDateTime.now(clock).format(METADATA_DATE_FORMAT)
        val baseMeta = existingMeta ?: EntryMeta(
            publishDate = initialPublishDate,
            updateDate = currentDateTime,
            bodyMd5 = bodyDigest,
            title = title,
            summary = summary,
            toc = toc,
            indexSummary = indexSummary,
            sourceSha256 = sourceSha256,
            renderConfigSha256 = renderConfigSha256,
            navigationSha256 = navigationSha256,
            generatorVersion = ENTRY_CACHE_VERSION,
            images = images,
        )
        val textualContentChanged = existingMeta == null ||
            baseMeta.bodyMd5 != bodyDigest ||
            baseMeta.title != title ||
            baseMeta.summary != summary ||
            baseMeta.toc != toc ||
            (baseMeta.sourceSha256 != null && baseMeta.sourceSha256 != sourceSha256)
        val imageContentChanged = existingMeta?.generatorVersion == ENTRY_CACHE_VERSION && baseMeta.images != images
        val publicationContentChanged = textualContentChanged || imageContentChanged
        val metadataChanged = publicationContentChanged ||
            baseMeta.indexSummary != indexSummary ||
            baseMeta.renderConfigSha256 != renderConfigSha256 ||
            baseMeta.navigationSha256 != navigationSha256 ||
            baseMeta.generatorVersion != ENTRY_CACHE_VERSION ||
            baseMeta.images != images

        val meta = if (metadataChanged) {
            val updated = baseMeta.copy(
                bodyMd5 = bodyDigest,
                title = title,
                summary = summary,
                toc = toc,
                indexSummary = indexSummary,
                sourceSha256 = sourceSha256,
                renderConfigSha256 = renderConfigSha256,
                navigationSha256 = navigationSha256,
                generatorVersion = ENTRY_CACHE_VERSION,
                images = images,
                updateDate = if (publicationContentChanged) currentDateTime else baseMeta.updateDate,
            )
            writeAtomically(metaFilePath, updated)
            val label = if (existingMeta == null) "Created" else "Updated"
            println("$label: ${metaFilePath.toAbsolutePath()}")
            updated
        } else {
            baseMeta
        }

        return EntryMetaState(
            meta = meta,
            entryChanged = textualContentChanged || existingMeta?.renderConfigSha256 != renderConfigSha256,
        )
    }

    fun read(metaFilePath: Path): EntryMeta? {
        if (!metaFilePath.isRegularFile()) {
            return null
        }
        return try {
            TomlReaders.decodeMeta(EntryMeta.serializer(), metaFilePath)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Failed to read metadata file $metaFilePath; the file was not modified: ${error.message}",
                error,
            )
        }
    }

    private fun currentDateTimeInJst(): String = LocalDateTime.now(clock).format(METADATA_DATE_FORMAT)

    private fun writeAtomically(metaFilePath: Path, meta: EntryMeta) {
        val absolutePath = metaFilePath.toAbsolutePath()
        val temporaryPath = absolutePath.resolveSibling(".${absolutePath.fileName}.tmp")
        try {
            writer.encodeToFile(EntryMeta.serializer(), meta, temporaryPath.toString())
            try {
                Files.move(
                    temporaryPath,
                    absolutePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryPath, absolutePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    private companion object {
        val JST: ZoneId = ZoneId.of("Asia/Tokyo")
        val METADATA_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

internal const val ENTRY_CACHE_VERSION: Int = 1

data class EntryMetaState(
    val meta: EntryMeta,
    val entryChanged: Boolean,
)
