package jp.yappo.pologen.domain.config

import jp.yappo.pologen.domain.model.TocEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.imgscalr.Scalr

@Serializable
data class Configuration(
    val paths: PathsConfig,
    val site: SiteConfig,
    val author: AuthorConfig,
    val assets: AssetsConfig = AssetsConfig(),
    val images: ImagesConfig = ImagesConfig(),
    val ogp: OgpConfig = OgpConfig(),
    val sidebar: SidebarConfig = SidebarConfig(),
    val links: Map<String, String> = emptyMap(),
)

@Serializable
data class PathsConfig(
    val documentRoot: String,
    val indexHtml: String,
    val feedXml: String,
)

@Serializable
data class SiteConfig(
    val blogTopUrl: String,
    val documentBaseUrl: String,
    val feedXmlUrl: String,
    val title: String,
    val description: String,
    val language: String = "en",
    val faviconUrl: String = "/favicon.ico",
)

@Serializable
data class AuthorConfig(
    val name: String,
    val url: String,
    val iconUrl: String,
)

@Serializable
data class AssetsConfig(
    val stylesheets: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
)

@Serializable
data class ImagesConfig(
    val thumbWidth: Int = 480,
    val fullMaxWidth: Int = 1920,
    @Serializable(with = ScalrMethodSerializer::class)
    val scaleMethod: Scalr.Method = Scalr.Method.QUALITY,
    val jpegQuality: Float = 0.9f,
)

@Serializable
data class OgpConfig(
    val enabled: Boolean = false,
    val width: Int = 1200,
    val height: Int = 630,
    val backgroundColor: String = "#101827",
    val titleColor: String = "#FFFFFF",
    val bodyColor: String = "#E5E7EB",
    val accentColor: String = "#F97316",
    val fontPath: String? = null,
    val authorIconPath: String? = null,
)

@Serializable
data class SidebarConfig(
    val recentEntryCount: Int = 10,
)

@Serializable
data class EntryMeta(
    val publishDate: String,
    val updateDate: String,
    val bodyMd5: String,
    val title: String? = null,
    val summary: String? = null,
    val toc: List<TocEntry> = emptyList(),
    val indexSummary: String? = null,
    val sourceSha256: String? = null,
    val renderConfigSha256: String? = null,
    val navigationSha256: String? = null,
    val generatorVersion: Int? = null,
    val images: List<EntryImageMeta> = emptyList(),
)

@Serializable
data class EntryImageMeta(
    val sourcePath: String,
    val sourceSha256: String,
    val fullPath: String,
    val thumbPath: String,
    val configSha256: String,
)

object ScalrMethodSerializer : KSerializer<Scalr.Method> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ScalrMethod", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Scalr.Method {
        return when (decoder.decodeString().lowercase()) {
            "speed" -> Scalr.Method.SPEED
            "balanced" -> Scalr.Method.BALANCED
            "ultra_quality" -> Scalr.Method.ULTRA_QUALITY
            "automatic" -> Scalr.Method.AUTOMATIC
            "quality" -> Scalr.Method.QUALITY
            else -> throw SerializationException(
                "Invalid images.scaleMethod. Expected speed, balanced, quality, ultra_quality, or automatic."
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Scalr.Method) {
        val text = when (value) {
            Scalr.Method.SPEED -> "speed"
            Scalr.Method.BALANCED -> "balanced"
            Scalr.Method.QUALITY -> "quality"
            Scalr.Method.ULTRA_QUALITY -> "ultra_quality"
            Scalr.Method.AUTOMATIC -> "automatic"
        }
        encoder.encodeString(text)
    }
}
