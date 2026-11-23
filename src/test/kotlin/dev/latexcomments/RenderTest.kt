package dev.latexcomments

import dev.latexcomments.render.Formula
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class RenderTest {
    private val font = Font(Font.SANS_SERIF, Font.PLAIN, 12)

    private fun picture(f: Formula, scale: Float, bg: Color, fg: Color): BufferedImage {
        val img = BufferedImage(((f.width + 8) * scale).toInt(), ((f.height + 8) * scale).toInt(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = bg; g.fillRect(0, 0, img.width, img.height)
        g.scale(scale.toDouble(), scale.toDouble())
        f.paint(g, 4f, 4f + f.ascent, fg, Color(0xc94f4f))
        g.dispose()
        return img
    }

    private fun save(img: BufferedImage, name: String) {
        val dir = System.getenv("LATEXCOMMENTS_SHOT_DIR") ?: return
        File(dir).mkdirs(); ImageIO.write(img, "png", File(dir, name))
    }

    private fun inked(img: BufferedImage, bg: Color): Int {
        var n = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if (img.getRGB(x, y) != bg.rgb) n++
        return n
    }

    @Test
    fun typesetsFormulas() {
        val f = Formula.of("L = \\sum_i (\\phi_0 + \\phi_1 x_i - y_i)^2", 16f, false, font)
        assertFalse(f.isError)
        assertTrue(f.width > 100 && f.height > 16, "${f.width} x ${f.height}")
        val light = picture(f, 1f, Color.WHITE, Color(0x5f826b))
        val dark = picture(f, 1f, Color(0x1e1f22), Color(0x7a9e7e))
        save(light, "sum-light.png"); save(dark, "sum-dark.png")
        assertTrue(inked(light, Color.WHITE) > 200)
        val display = Formula.of("\\frac{1}{1+e^{-x}}", 16f, true, font)
        assertTrue(display.height > f.height)
        save(picture(display, 2f, Color.WHITE, Color.BLACK), "frac-200.png")
    }

    @Test
    fun baselineSplitsTheFormula() {
        // "x" sits on the baseline: almost nothing below it. "y" hangs below. A fraction reaches both ways.
        val x = Formula.of("x", 20f, false, font)
        val y = Formula.of("y", 20f, false, font)
        val frac = Formula.of("\\frac{a}{b}", 20f, true, font)
        assertTrue(x.descent < 5f, "x descent ${x.descent}")
        assertTrue(y.descent > x.descent + 2f, "y descent ${y.descent}")
        assertTrue(frac.ascent > 10f && frac.descent > 6f, "frac ${frac.ascent} / ${frac.descent}")
    }

    @Test
    fun sharpAtTwoTimes() {
        val f = Formula.of("\\int_0^1 x^2\\,dx = \\tfrac{1}{3}", 16f, false, font)
        val one = picture(f, 1f, Color.WHITE, Color.BLACK)
        val two = picture(f, 2f, Color.WHITE, Color.BLACK)
        save(two, "integral-200.png")
        assertTrue(kotlin.math.abs(one.width * 2 - two.width) <= 2)
        // Drawn as shapes at 2x: about four times the inked pixels, not a blown-up copy.
        val ratio = inked(two, Color.WHITE).toDouble() / inked(one, Color.WHITE)
        assertTrue(ratio in 2.5..5.5, "ratio $ratio")
    }

    @Test
    fun badLatexBecomesAShortPlainMessage() {
        for ((latex, expected) in listOf(
            "\\foo{x}" to "LaTeX error: unknown command \\foo",
            "\\frac{1}{\\sqroot{2}}" to "LaTeX error: unknown command \\sqroot",
            "\\begin{nothing} x" to "LaTeX error:",
            "\\left( x" to "LaTeX error:",
        )) {
            val f = Formula.of(latex, 16f, false, font)
            assertTrue(f.isError, latex)
            assertTrue(f.error!!.startsWith(expected), "$latex -> ${f.error}")
            assertTrue(f.error!!.length <= 75)
            save(picture(f, 1f, Color.WHITE, Color.BLACK), "error-${latex.hashCode()}.png")
        }
    }

    /** Formulas as they appear in machine-learning and physics textbooks. */
    @Test
    fun textbookFormulasAllRender() {
        val formulas = listOf(
            "\\mathbf{y} = \\mathbf{f}[\\mathbf{x}, \\boldsymbol\\phi]",
            "\\hat{\\boldsymbol\\phi} = \\argmin_{\\boldsymbol\\phi} \\Bigl[ L[\\boldsymbol\\phi] \\Bigr]",
            "\\argmax_x f(x)", "\\operatorname{ReLU}[z] = \\max(0, z)", "\\bm{\\theta}", "a \\coloneqq b",
            "\\mathbb{R}^{D}", "\\mathcal{N}(\\mu, \\sigma^2)", "\\mathbb{E}_{x \\sim p}[x]",
            "\\begin{bmatrix} a & b \\\\ c & d \\end{bmatrix}", "\\begin{pmatrix} 1 \\\\ x \\end{pmatrix}",
            "f(x) = \\begin{cases} 0 & x < 0 \\\\ x & x \\geq 0 \\end{cases}",
            "\\begin{aligned} a &= b \\\\ c &= d \\end{aligned}", "\\text{if } x > 0", "\\tfrac{1}{2} \\dfrac{1}{2}",
            "\\sqrt[3]{x}", "\\left( \\frac{a}{b} \\right)", "\\nabla_{\\phi} L", "\\| \\mathbf{x} \\|_2^2",
            "\\sum_{i=1}^{N} \\log \\Pr(y_i | x_i)", "\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}",
            "\\lim_{n \\to \\infty}", "\\mathbf{W}^\\top", "\\odot \\otimes \\approx \\propto \\leq \\neq",
            "\\{ x \\mid x > 0 \\}", "\\lfloor x \\rfloor", "\\color{red}{x}", "\\underbrace{a+b}_{c}",
            "\\varepsilon \\ell \\hbar \\omega", "\\binom{n}{k}", "\\dots \\cdots", "\\mathrm{softmax}(z)_k",
            "\\frac{\\partial L}{\\partial \\phi_0} = 2 \\sum_i (\\phi_0 + \\phi_1 x_i - y_i)",
        )
        val failed = formulas.filter { Formula.of(it, 16f, true, font).isError }
        assertEquals(emptyList<String>(), failed)
    }

    @Test
    fun cachedAndQuick() {
        val t = System.nanoTime()
        repeat(200) { Formula.of("x_{$it}^2 + \\alpha_{$it}", 14f, false, font) }
        val ms = (System.nanoTime() - t) / 1e6
        assertTrue(ms < 3000, "200 formulas took $ms ms")
        val a = Formula.of("x_{1}^2 + \\alpha_{1}", 14f, false, font)
        assertTrue(a === Formula.of("x_{1}^2 + \\alpha_{1}", 14f, false, font))
    }
}

class DocumentationTest {
    @Test
    fun mathInDocPagesBecomesPictures() {
        val html = d("<div>Rate §\\eta &lt; 1§ and <code>§x§</code>: §§\\frac{a}{b}§§ <a href=\"§no§\">x</a></div>")
        val out = dev.latexcomments.doc.MathDocumentationProvider.withMath(html)
        assertEquals(2, Regex("<img src=\"data:image/png;base64,").findAll(out).count(), out)
        assertTrue(out.contains("alt=\"\\eta &lt; 1\""), out)
        assertTrue(out.contains("<code>\$x\$</code>"), "code is left alone: $out")
        assertTrue(out.contains("href=\"\$no\$\""), "tags are left alone: $out")
        val img = dev.latexcomments.doc.MathDocumentationProvider.imageFor("latexcomments:i:" + java.util.Base64.getUrlEncoder().encodeToString("x^2".toByteArray())) as java.awt.image.BufferedImage
        assertTrue(img.width > 20 && img.height > 10)
        assertEquals(null, dev.latexcomments.doc.MathDocumentationProvider.imageFor("other.png"))
    }
}
