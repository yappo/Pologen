package jp.yappo.pologen.infrastructure.config

import com.akuleshov7.ktoml.file.TomlFileReader
import jp.yappo.pologen.application.port.ConfigurationReader
import jp.yappo.pologen.domain.config.Configuration
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class ConfigurationLoader(
    private val reader: TomlFileReader = TomlFileReader(),
) : ConfigurationReader {
    override fun load(path: Path): Configuration {
        require(path.isRegularFile()) {
            "Configuration file does not exist: $path"
        }
        return reader.decodeFromFile(Configuration.serializer(), path.toString())
    }
}
