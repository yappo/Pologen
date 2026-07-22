package jp.yappo.pologen.application.port

import jp.yappo.pologen.domain.config.Configuration
import jp.yappo.pologen.domain.model.Entry
import java.nio.file.Path

interface SiteWriter {
    fun copyAssets(outputRoot: Path)

    fun renderEntries(configuration: Configuration, entries: List<Entry>)

    fun renderIndex(configuration: Configuration, indexHtmlPath: Path, entries: List<Entry>)

    fun renderFeed(configuration: Configuration, feedXmlPath: Path, entries: List<Entry>)

    fun renderArchive(configuration: Configuration, archiveHtmlPath: Path, entries: List<Entry>)
}
