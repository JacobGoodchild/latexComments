package dev.latexcomments.doc

import com.intellij.lang.LanguageDocumentation
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import dev.latexcomments.render.Formula
import dev.latexcomments.scan.MathFinder
import dev.latexcomments.settings.LatexSettings
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.ceil

/**
 * Shows the maths of documentation comments as formulas in Quick Documentation. It asks the language's own
 * documentation provider for the page and swaps each formula for a picture of it.
 */
class MathDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || !LatexSettings.get().enabled) return null
        val others = LanguageDocumentation.INSTANCE.allForLanguage(element.language).filter { it !is MathDocumentationProvider }
        for (p in others) {
            val html = p.generateDoc(element, originalElement) ?: continue
            return withMath(html)
        }
        return null
    }

    override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? = imageFor(imageSpec)

    companion object {
        const val PREFIX = "latexcomments:"

        /** Replaces the maths in a documentation page by pictures. Text inside tags and code blocks is left alone. */
        fun withMath(html: String): String {
            val out = StringBuilder()
            var i = 0
            // Only text between tags is searched, and not inside <code> or <pre>.
            var inCode = 0
            val tag = Regex("<(/?)([a-zA-Z0-9]+)[^>]*>")
            var m = tag.find(html, 0)
            while (true) {
                val textEnd = m?.range?.first ?: html.length
                val text = html.substring(i, textEnd)
                out.append(if (inCode > 0) text else replaceIn(text))
                if (m == null) break
                val name = m.groupValues[2].lowercase()
                if (name == "code" || name == "pre") inCode += if (m.groupValues[1] == "/") -1 else 1
                if (inCode < 0) inCode = 0
                out.append(m.value)
                i = m.range.last + 1
                m = tag.find(html, i)
            }
            return out.toString()
        }

        private fun replaceIn(text: String): String {
            val spans = MathFinder.findIn(text, (0..text.length).toList())
            if (spans.isEmpty()) return text
            val sb = StringBuilder()
            var at = 0
            for (s in spans) {
                sb.append(text, at, s.start)
                val latex = unescape(s.latex)
                val f = formula(latex, s.display)
                val spec = PREFIX + (if (s.display) "d:" else "i:") + Base64.getUrlEncoder().withoutPadding().encodeToString(latex.toByteArray(StandardCharsets.UTF_8))
                // Embedded as a PNG drawn at twice the size, so it is sharp on scaled screens.
                val png = pngDataUrl(spec)
                val img = "<img src=\"${png ?: spec}\" alt=\"${escape(latex)}\" width=\"${ceil(f.width).toInt()}\" height=\"${ceil(f.height).toInt()}\" style=\"vertical-align: middle\">"
                sb.append(if (s.display) "<div style=\"margin: 4px 0\">$img</div>" else img)
                at = s.end
            }
            sb.append(text, at, text.length)
            return sb.toString()
        }

        private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        private fun pngDataUrl(spec: String): String? {
            val img = imageFor(spec) as? BufferedImage ?: return null
            val bytes = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "png", bytes)
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray())
        }

        private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

        private fun formula(latex: String, display: Boolean): Formula {
            val font = JBFont.regular()
            return Formula.of(latex, font.size2D * 1.25f, display, font)
        }

        /** The picture for an image reference made by [withMath], drawn for the screen's scale so it is sharp. */
        fun imageFor(spec: String): Image? {
            if (!spec.startsWith(PREFIX)) return null
            val rest = spec.removePrefix(PREFIX)
            val display = rest.startsWith("d:")
            val latex = try { String(Base64.getUrlDecoder().decode(rest.substring(2)), StandardCharsets.UTF_8) } catch (_: IllegalArgumentException) { return null }
            val f = formula(latex, display)
            val scale = JBUIScale.sysScale().coerceAtLeast(1f) * 2f
            val img = BufferedImage(ceil(f.width * scale).toInt().coerceAtLeast(1), ceil(f.height * scale).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.scale(scale.toDouble(), scale.toDouble())
            f.paint(g, 0f, f.ascent, UIUtil.getLabelForeground(), UIUtil.getErrorForeground())
            g.dispose()
            return img
        }
    }
}
