package jp.yappo.pologen.domain.support

import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path

fun sha256Hex(text: String): String {
    return sha256Hex(text.toByteArray(Charsets.UTF_8))
}

fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

fun sha256Hex(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
