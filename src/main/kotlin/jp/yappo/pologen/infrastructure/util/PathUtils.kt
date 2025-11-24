package jp.yappo.pologen.infrastructure.util

import java.nio.file.Path

fun resolveConfiguredPath(baseDir: Path, configuredPath: String?): Path? {
    if (configuredPath.isNullOrBlank()) return null
    val path = Path.of(configuredPath)
    return if (path.isAbsolute) path.normalize() else baseDir.resolve(path).normalize()
}
