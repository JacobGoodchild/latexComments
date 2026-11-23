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
