package jp.yappo.pologen.infrastructure.markdown

import com.akuleshov7.ktoml.file.TomlFileWriter
import jp.yappo.pologen.domain.config.EntryMeta
import jp.yappo.pologen.domain.model.TocEntry
import jp.yappo.pologen.domain.support.sha256Hex
import jp.yappo.pologen.infrastructure.config.TomlReaders
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.isRegularFile

class EntryMetaStore(
    private val clock: Clock = Clock.system(JST),
    private val writer: TomlFileWriter = TomlFileWriter(),
) {
    fun synchronize(metaFilePath: Path, body: String, title: String, summary: String, toc: List<TocEntry>): EntryMetaState {
        val bodyDigest = sha256Hex(body)
        val currentDateTime = LocalDateTime.now(clock).format(METADATA_DATE_FORMAT)
        val existingMeta = readExistingMeta(metaFilePath)
        val baseMeta = existingMeta ?: EntryMeta(
            publishDate = currentDateTime,
            updateDate = currentDateTime,
            bodyMd5 = bodyDigest,
            title = title,
            summary = summary,
            toc = toc,
        )
        val entryChanged = existingMeta == null ||
            baseMeta.bodyMd5 != bodyDigest ||
            baseMeta.title != title ||
            baseMeta.summary != summary ||
            baseMeta.toc != toc

        val meta = if (entryChanged) {
            val updated = baseMeta.copy(
                bodyMd5 = bodyDigest,
                title = title,
                summary = summary,
                toc = toc,
                updateDate = if (baseMeta.bodyMd5 == bodyDigest) baseMeta.updateDate else currentDateTime,
            )
            writer.encodeToFile(EntryMeta.serializer(), updated, metaFilePath.toAbsolutePath().toString())
            val label = if (existingMeta == null) "Created" else "Updated"
            println("$label: ${metaFilePath.toAbsolutePath()}")
            updated
        } else {
            baseMeta
        }

        return EntryMetaState(
            meta = meta,
            entryChanged = entryChanged,
        )
    }

    private fun readExistingMeta(metaFilePath: Path): EntryMeta? {
        if (!metaFilePath.isRegularFile()) {
            return null
        }
        return runCatching {
            TomlReaders.decodeMeta(EntryMeta.serializer(), metaFilePath)
        }.getOrNull()
    }

    private companion object {
        val JST: ZoneId = ZoneId.of("Asia/Tokyo")
        val METADATA_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

data class EntryMetaState(
    val meta: EntryMeta,
    val entryChanged: Boolean,
)
