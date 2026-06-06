package jp.yappo.pologen.application

import jp.yappo.pologen.domain.config.Configuration
import java.nio.file.Path

data class SiteBuildPaths(
    val configFile: Path,
    val configBaseDir: Path,
    val documentRoot: Path,
    val indexHtml: Path,
    val feedXml: Path,
) {
    companion object {
        fun resolve(configPath: Path, configuration: Configuration): SiteBuildPaths {
            val configFile = configPath.toAbsolutePath().normalize()
            val configBaseDir = configFile.parent ?: Path.of(".").toAbsolutePath().normalize()
            return SiteBuildPaths(
                configFile = configFile,
                configBaseDir = configBaseDir,
                documentRoot = configBaseDir.resolve(configuration.paths.documentRoot).normalize(),
                indexHtml = configBaseDir.resolve(configuration.paths.indexHtml).normalize(),
                feedXml = configBaseDir.resolve(configuration.paths.feedXml).normalize(),
            )
        }
    }
}
