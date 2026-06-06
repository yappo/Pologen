package jp.yappo.pologen.domain.support

import java.security.MessageDigest

fun sha256Hex(text: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
