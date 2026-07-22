package jp.yappo.pologen.domain.support

import java.net.URI

fun resolveDocumentUrl(documentBaseUrl: String, path: String): String {
    val base = documentBaseUrl.trimEnd('/')
    val relative = path.trimStart('/')
    return URI("$base/$relative").normalize().toString()
}
