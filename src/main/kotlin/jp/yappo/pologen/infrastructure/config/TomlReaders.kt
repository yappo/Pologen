package jp.yappo.pologen.infrastructure.config

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import java.nio.file.Path

object TomlReaders {
    private val lenientMetaReader: TomlFileReader by lazy {
        TomlFileReader(
            TomlInputConfig(
                ignoreUnknownNames = true,
                allowNullValues = true,
                allowEmptyValues = true,
                allowEmptyToml = true,
                allowEscapedQuotesInLiteralStrings = true,
            ),
            TomlOutputConfig(),
            EmptySerializersModule()
        )
    }

    fun <T> decodeMeta(deserializer: DeserializationStrategy<T>, path: Path): T {
        return lenientMetaReader.decodeFromFile(deserializer, path.toString())
    }
}
