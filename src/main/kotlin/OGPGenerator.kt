package jp.yappo.pologen

import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.isRegularFile

/**
 * Generates OGP images for entries using Java2D.
 */
object OGPGenerator {
    private const val AUTHOR_ICON_SIZE = 96
    private const val ACCENT_BAR_HEIGHT = 8
    private const val BODY_ICON_GAP = 24
    private const val BODY_BOTTOM_PADDING = 24
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
        g.fillRect(0, height - ACCENT_BAR_HEIGHT, width, ACCENT_BAR_HEIGHT)

        val baseFont = loadFont(conf.ogpFontPath)
        val siteFont = baseFont.deriveFont(Font.BOLD, 42f)
        val titleFont = baseFont.deriveFont(Font.BOLD, 64f)
        val bodyFont = baseFont.deriveFont(Font.PLAIN, 30f)
        val siteLineHeight = (siteFont.size2D * 1.4f).toInt()
        val titleLineHeight = (titleFont.size2D * 1.3f).toInt()
        val bodyLineHeight = (bodyFont.size2D * 1.6f).toInt()

        val margin = 64
        var cursorY = margin

        g.color = titleColor
        g.font = siteFont
        cursorY = drawWrappedText(g, siteTitle, margin, cursorY, width - margin * 2, siteLineHeight)

        cursorY += 12
        g.color = titleColor
        g.font = titleFont
        cursorY = drawWrappedText(g, entryTitle, margin, cursorY, width - margin * 2, titleLineHeight)

        cursorY += 18
        val bodyText = description.orEmpty()
        val baseBodyMaxWidth = width - margin * 2
        val hasAuthorIcon = !conf.ogpAuthorIconPath.isNullOrBlank()
        val bodyMaxWidth = if (hasAuthorIcon) {
            val reservedWidth = baseBodyMaxWidth - AUTHOR_ICON_SIZE - BODY_ICON_GAP
            maxOf(reservedWidth, baseBodyMaxWidth / 2)
        } else {
            baseBodyMaxWidth
        }
        val reservedBottom = BODY_BOTTOM_PADDING + ACCENT_BAR_HEIGHT
        val availableHeight = (height - reservedBottom) - cursorY
        val bodyMaxLinesByHeight = if (availableHeight > 0) availableHeight / bodyLineHeight else 0
        val bodyMaxLines = minOf(5, bodyMaxLinesByHeight)
        if (bodyMaxLines > 0 && bodyText.isNotBlank()) {
            g.color = bodyColor
            g.font = bodyFont
            drawWrappedText(
                g,
                bodyText,
                margin,
                cursorY,
                bodyMaxWidth,
                bodyLineHeight,
                maxLines = bodyMaxLines
            )
        }

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

        if (words.size == 1) {
            val singleLines = splitWordByWidth(words.first(), fm, maxWidth)
            for (line in singleLines) {
                lines.add(line)
                if (lines.size >= maxLines) break
            }
        } else {
            var current = StringBuilder()
            for (word in words) {
                if (word.isBlank()) continue
                if (fm.stringWidth(word) > maxWidth) {
                    if (current.isNotEmpty()) {
                        lines.add(current.toString())
                        current = StringBuilder()
                        if (lines.size >= maxLines) break
                    }
                    val parts = splitWordByWidth(word, fm, maxWidth)
                    for (part in parts) {
                        lines.add(part)
                        if (lines.size >= maxLines) break
                    }
                    if (lines.size >= maxLines) break
                    continue
                }
                val candidate = if (current.isEmpty()) word else current.toString() + " " + word
                val w = fm.stringWidth(candidate)
                if (w > maxWidth && current.isNotEmpty()) {
                    lines.add(current.toString())
                    if (lines.size >= maxLines) break
                    current = StringBuilder(word)
                } else {
                    current = StringBuilder(candidate)
                }
                if (lines.size >= maxLines) {
                    break
                }
            }
            if (lines.size < maxLines && current.isNotEmpty()) {
                lines.add(current.toString())
            }
        }

        var y = startY
        for (line in lines.take(maxLines)) {
            g.drawString(line, x, y)
            y += lineHeight
        }
        return y
    }

    private fun splitWordByWidth(word: String, fm: FontMetrics, maxWidth: Int): List<String> {
        if (word.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        for (char in word) {
            if (current.isEmpty()) {
                current.append(char)
                continue
            }
            val candidate = current.toString() + char
            val width = fm.stringWidth(candidate)
            if (width > maxWidth) {
                parts.add(current.toString())
                current = StringBuilder().append(char)
            } else {
                current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        return parts
    }

    private fun drawAuthorIcon(
        g: Graphics2D,
        conf: Configuration,
        width: Int,
        height: Int,
        margin: Int,
    ) {
        val icon = loadAuthorIcon(conf.ogpAuthorIconPath) ?: return
        val targetSize = AUTHOR_ICON_SIZE
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
