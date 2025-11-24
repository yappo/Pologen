package jp.yappo.pologen.infrastructure.config

import com.akuleshov7.ktoml.file.TomlFileReader
import jp.yappo.pologen.domain.config.Configuration
import kotlin.io.path.isRegularFile
import java.nio.file.Path

class ConfigurationLoader(
    private val reader: TomlFileReader = TomlFileReader(),
) {
    fun load(path: Path): Configuration {
        require(path.isRegularFile()) {
            "Configuration file does not exist: $path"
        }
        return reader.decodeFromFile(Configuration.serializer(), path.toString())
    }
}
