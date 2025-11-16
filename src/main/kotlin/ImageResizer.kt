package jp.yappo.pologen

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageOutputStream
import org.imgscalr.Scalr

/**
 * Generates responsive image variants (full and thumbnail) for a source asset.
 */
fun generateResizedImages(
    source: Path,
    destFull: Path,
    destThumb: Path,
    fullMaxWidth: Int,
    thumbWidth: Int,
    scaleMethod: Scalr.Method,
    jpegQuality: Float
) {
    destFull.parent?.let { Files.createDirectories(it) }
    destThumb.parent?.let { Files.createDirectories(it) }

    val original = ImageIO.read(source.toFile()) ?: error("Unsupported image: $source")
    val fullImage = resizeToWidth(original, fullMaxWidth, scaleMethod)
    val thumbImage = resizeToWidth(original, thumbWidth, scaleMethod)

    writeImage(fullImage, destFull, jpegQuality)
    writeImage(thumbImage, destThumb, jpegQuality)
}

private fun resizeToWidth(
    image: BufferedImage,
    targetWidth: Int,
    method: Scalr.Method
): BufferedImage {
    if (targetWidth <= 0) return image
    if (image.width <= targetWidth) {
        return ensureRgb(image)
    }
    val resized = Scalr.resize(image, method, Scalr.Mode.FIT_TO_WIDTH, targetWidth)
    return ensureRgb(resized)
}

private fun ensureRgb(image: BufferedImage): BufferedImage {
    if (image.type == BufferedImage.TYPE_INT_RGB) return image
    val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    val graphics = converted.createGraphics()
    graphics.drawImage(image, 0, 0, Color.WHITE, null)
    graphics.dispose()
    return converted
}

private fun writeImage(image: BufferedImage, dest: Path, quality: Float) {
    when (dest.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "png" -> writePng(image, dest)
        else -> writeJpeg(image, dest, quality)
    }
}

/**
 * Writes the supplied [image] to [dest] as a JPEG at the requested [quality].
 */
fun writeJpeg(image: BufferedImage, dest: Path, quality: Float) {
    val clamped = quality.coerceIn(0f, 1f)
    val writer = ImageIO.getImageWritersByFormatName("jpg").asSequence().firstOrNull()
        ?: error("No JPEG writer found")
    dest.parent?.let { Files.createDirectories(it) }
    ImageIO.createImageOutputStream(dest.toFile()).use { stream: ImageOutputStream ->
        writer.output = stream
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = clamped
        }
        writer.write(null, IIOImage(image, null, null), params)
        writer.dispose()
    }
}

private fun writePng(image: BufferedImage, dest: Path) {
    dest.parent?.let { Files.createDirectories(it) }
    ImageIO.write(image, "png", dest.toFile())
}
