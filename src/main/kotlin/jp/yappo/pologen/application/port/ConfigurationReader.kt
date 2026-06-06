package jp.yappo.pologen.application.port

import jp.yappo.pologen.domain.config.Configuration
import java.nio.file.Path

fun interface ConfigurationReader {
    fun load(path: Path): Configuration
}
