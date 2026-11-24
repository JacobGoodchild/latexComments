package dev.latexcomments

import dev.latexcomments.asciimath.AsciiMath
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

class AsciiMathTest {
    /** The LaTeX without spaces (spacing inside LaTeX doesn't change how it looks). */
    private fun tex(s: String) = AsciiMath.toLatex(s).replace(Regex("\\s+"), "")
    private val font = Font(Font.SANS_SERIF, Font.PLAIN, 12)

    @Test
    fun basics() {
        assertEquals("x^{2}", tex("x^2"))
        assertEquals("x_{i}", tex("x_i"))
        assertEquals("x_{i}^{2}", tex("x_i^2"))
        assertEquals("x_{i}^{2}", tex("x^2_i"))
        assertEquals("\\sqrt{x}", tex("sqrt(x)"))
        assertEquals("\\sqrt{x}", tex("sqrt x"))
        assertEquals("\\sqrt[3]{x}", tex("root(3)(x)"))
        assertEquals("\\frac{a}{b}", tex("a/b"))
        assertEquals("\\frac{a+b}{2}", tex("(a+b)/2"))
        assertEquals("\\frac{\\frac{a}{b}}{\\frac{c}{d}}", tex("(a/b)/(c/d)"))
        assertEquals("a/b", tex("a//b"))
        assertEquals("\\phi_{0}", tex("phi_0"))
        assertEquals("\\alpha+\\beta", tex("alpha+beta"))
        assertEquals("x^{-1}", tex("x^-1"))
        assertEquals("\\text{if}", tex("text(if)"))
        assertEquals("\\text{hello world}", AsciiMath.toLatex("\"hello world\""))
        assertEquals("\\text{if}", tex("text(if)"))
        assertEquals("\\left(a,b\\right)", tex("(a,b)"))
        assertEquals("\\left|x\\right|", tex("abs(x)"))
        assertEquals("\\left|x\\right|", tex("|x|"))
        assertEquals("\\left\\lfloorx\\right\\rfloor", tex("floor(x)"))
        assertEquals("\\mathbf{x}", tex("bb(x)"))
        assertEquals("\\mathbb{R}", tex("RR"))
        assertEquals("\\hat{y}", tex("hat y"))
    }

    @Test
    fun theUsersExample() {
        assertEquals("L=\\sum_{i}\\left(\\phi_{0}+\\phi_{1}x_{i}-y_{i}\\right)^{2}",
            tex("L = sum_i (phi_0 + phi_1 x_i - y_i)^2"))
    }

    @Test
    fun matrices() {
        assertTrue(tex("[[a,b],[c,d]]").contains("\\begin{matrix}a&b\\\\c&d\\end{matrix}"))
        assertTrue(tex("((1,0),(0,1))").startsWith("\\left(\\begin{matrix}1&0\\\\0&1"))
        val cases = tex("x/x={(1,if x!=0),(text{undefined},if x=0):}")
        assertTrue(cases.contains("\\left\\{\\begin{matrix}"), cases)
        assertTrue(cases.contains("\\text{undefined}"), cases)
        assertTrue(cases.endsWith("\\right."), cases)
    }

    @Test
    fun wordsAreNotSplitIntoSymbols() {
        // "in" inside "index", "int" inside "into": letters, not symbols.
        assertEquals("\\mathrm{index}", tex("index"))
        assertEquals("\\mathrm{softmax}\\left(z\\right)", tex("softmax(z)"))
        assertEquals("xy", tex("xy"))
        assertEquals("\\underline{A}\\dot{x}", tex("ulA dotx"))
        assertTrue(tex("x in RR").contains("\\in"))
    }

    @Test
    fun brokenInputNeverThrows() {
        for (s in listOf("", "(", ")", "((a", "a/", "/b", "x^", "_", "sqrt", "root(2)", "frac", "|", "\"unclosed", "{:", ":}", "a,,b", "^^^^", "\\"))
            AsciiMath.toLatex(s)
    }

    /** The examples on asciimath.org: each converts and renders without a LaTeX error. */
    @Test
    fun examplesFromAsciimathOrg() {
        val examples = listOf(
            "sum_(i=1)^n i^3=((n(n+1))/2)^2",
            "[[a,b],[c,d]]",
            "((1,0),(0,1))",
            "x/x={(1,if x!=0),(text{undefined},if x=0):}",
            "a//b",
            "(a/b)/(c/d)",
            "a/b/c/d",
            "((a*b))/c",
            "sqrt sqrt root3x",
            "<< a,b >> and {:(x,y),(u,v):}",
            "(a,b]={x in RR | a < x <= b}",
            "abc-123.45^-1.1",
            "hat(ab) bar(xy) ulA vec v dotx ddot y",
            "bb{AB3}.bbb(AB].cc(AB).fr{AB}.tt[AB].sf(AB)",
            "stackrel\"def\"= or stackrel{\\ \\ \\ }(->)",
            "obrace(1+2+3+4)^(\"4 terms\")",
            "lim_(N->oo) sum_(i=0)^N",
            "int_0^1 f(x)dx",
            "d/dx f(x)=lim_(h->0)(f(x+h)-f(x))/h",
            "f(x)=sum_(n=0)^oo(f^((n))(a))/(n!)(x-a)^n",
            "[[a,b],[c,d]]((n),(k))",
            "color(red)(x) and cancel(x)",
            "sin^2 x + cos^2 x = 1",
            "e^(i pi) + 1 = 0",
            "nabla xx vec E = - del vec B / del t",
            "P(A|B) = (P(B|A) P(A))/(P(B))",
            "mu = 1/N sum_(i=1)^N x_i",
            "sigma^2 = 1/N sum_i (x_i - mu)^2",
            "hat y = W x + b",
            "theta := theta - eta grad_theta L(theta)",
            "softmax(z)_j = e^(z_j)/(sum_k e^(z_k))",
            "N(mu, sigma^2)",
            "||x||_2^2",
            "x in RR^n",
            "argmin_theta L(theta)",
        )
        val failed = examples.filter { Formula.of(AsciiMath.toLatex(it), 16f, true, font).isError }
        assertEquals(emptyList<String>(), failed.map { it + "  ->  " + AsciiMath.toLatex(it) })
        val dir = System.getenv("LATEXCOMMENTS_SHOT_DIR") ?: return
        File(dir).mkdirs()
        val formulas = examples.map { Formula.of(AsciiMath.toLatex(it), 16f, true, font) }
        val w = (formulas.maxOf { it.width } + 20).toInt()
        val h = formulas.sumOf { (it.height + 10).toDouble() }.toInt() + 10
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE; g.fillRect(0, 0, w, h)
        var y = 10f
        for (f in formulas) { f.paint(g, 10f, y + f.ascent, Color.BLACK, Color.RED); y += f.height + 10 }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "asciimath-examples.png"))
    }
}
