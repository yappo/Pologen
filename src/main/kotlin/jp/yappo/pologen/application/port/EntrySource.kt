package jp.yappo.pologen.application.port

import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
import java.nio.file.Path

fun interface EntrySource {
    fun collectEntries(configuration: Configuration, documentRoot: Path, configBaseDir: Path): List<Entry>
}
