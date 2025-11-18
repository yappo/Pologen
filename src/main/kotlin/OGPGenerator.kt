package jp.yappo.pologen

import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.isRegularFile

/**
 * Generates OGP images for entries using Java2D.
 */
object OGPGenerator {
    private var cachedFontPath: Path? = null
    private var cachedFont: Font? = null
    private var cachedAuthorIconPath: Path? = null
    private var cachedAuthorIcon: BufferedImage? = null

    fun generate(
        conf: Configuration,
        siteTitle: String,
        entryTitle: String,
        description: String?,
        output: Path,
    ) {
        val width = conf.ogpWidth
        val height = conf.ogpHeight
        val bgColor = parseColor(conf.ogpBackgroundColor, Color(0x10, 0x18, 0x27))
        val titleColor = parseColor(conf.ogpTitleColor, Color.WHITE)
        val bodyColor = parseColor(conf.ogpBodyColor, Color(0xE5, 0xE7, 0xEB))
        val accentColor = parseColor(conf.ogpAccentColor, Color(0xF9, 0x73, 0x16))

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = bgColor
        g.fillRect(0, 0, width, height)

        g.color = accentColor
        g.fillRect(0, height - 8, width, 8)

        val baseFont = loadFont(conf.ogpFontPath)
        val siteFont = baseFont.deriveFont(Font.BOLD, 42f)
        val titleFont = baseFont.deriveFont(Font.BOLD, 64f)
        val bodyFont = baseFont.deriveFont(Font.PLAIN, 30f)

        val margin = 64
        var cursorY = margin

        g.color = titleColor
        g.font = siteFont
        cursorY = drawWrappedText(g, siteTitle, margin, cursorY, width - margin * 2, 50)

        cursorY += 12
        g.color = titleColor
        g.font = titleFont
        cursorY = drawWrappedText(g, entryTitle, margin, cursorY, width - margin * 2, 70)

        cursorY += 18
        g.color = bodyColor
        g.font = bodyFont
        cursorY = drawWrappedText(g, description.orEmpty(), margin, cursorY, width - margin * 2, 46, maxLines = 5)

        drawAuthorIcon(g, conf, width, height, margin)

        g.dispose()
        Files.createDirectories(output.parent)
        ImageIO.write(image, "png", output.toFile())

        println("Created: ${output.toAbsolutePath()}")
    }

    private fun loadFont(fontPath: String?): Font {
        if (fontPath.isNullOrBlank()) return Font("SansSerif", Font.PLAIN, 32)
        val path = Path.of(fontPath)
        if (!path.isRegularFile()) return Font("SansSerif", Font.PLAIN, 32)
        if (cachedFontPath == path && cachedFont != null) return cachedFont!!
        return try {
            Files.newInputStream(path).use { input ->
                val font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(Font.PLAIN, 32f)
                cachedFontPath = path
                cachedFont = font
                font
            }
        } catch (e: Exception) {
            println("Failed to load font $fontPath: ${e.message}")
            Font("SansSerif", Font.PLAIN, 32)
        }
    }

    private fun parseColor(value: String, default: Color): Color {
        return try {
            Color.decode(value)
        } catch (_: Exception) {
            default
        }
    }

    private fun drawWrappedText(
        g: Graphics2D,
        text: String,
        x: Int,
        startY: Int,
        maxWidth: Int,
        lineHeight: Int,
        maxLines: Int = Int.MAX_VALUE,
    ): Int {
        val fm = g.fontMetrics
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else current.toString() + " " + word
            val w = fm.stringWidth(candidate)
            if (w > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
            if (lines.size >= maxLines) break
        }
        if (lines.size < maxLines && current.isNotEmpty()) {
            lines.add(current.toString())
        }

        var y = startY
        for (line in lines.take(maxLines)) {
            g.drawString(line, x, y)
            y += lineHeight
        }
        return y
    }

    private fun drawAuthorIcon(
        g: Graphics2D,
        conf: Configuration,
        width: Int,
        height: Int,
        margin: Int,
    ) {
        val icon = loadAuthorIcon(conf.ogpAuthorIconPath) ?: return
        val targetSize = 96
        val scaled = icon.getScaledInstance(targetSize, targetSize, Image.SCALE_SMOOTH)
        val x = width - margin - targetSize
        val y = height - margin - targetSize
        val clip = Ellipse2D.Float(x.toFloat(), y.toFloat(), targetSize.toFloat(), targetSize.toFloat())
        val oldClip = g.clip
        g.clip = clip
        g.drawImage(scaled, x, y, null)
        g.clip = oldClip
        g.color = Color.WHITE
        g.stroke = BasicStroke(3f)
        g.draw(clip)
    }

    private fun loadAuthorIcon(pathStr: String?): BufferedImage? {
        if (pathStr.isNullOrBlank()) return null
        val path = Path.of(pathStr)
        if (!path.isRegularFile()) return null
        if (cachedAuthorIconPath == path && cachedAuthorIcon != null) return cachedAuthorIcon
        return try {
            ImageIO.read(path.toFile())?.also {
                cachedAuthorIconPath = path
                cachedAuthorIcon = it
            }
        } catch (e: Exception) {
            println("Failed to load author icon $pathStr: ${e.message}")
            null
        }
    }
}
