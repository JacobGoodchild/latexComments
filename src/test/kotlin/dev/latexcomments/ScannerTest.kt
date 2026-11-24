package dev.latexcomments

import dev.latexcomments.scan.CommentKind
import dev.latexcomments.scan.CommentScanner
import dev.latexcomments.scan.CommentSyntax
import dev.latexcomments.scan.MathFinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Test text uses § for a dollar sign, because Kotlin strings treat $ specially. */
internal fun d(s: String) = s.replace('§', '$')

class ScannerTest {
    private fun comments(text: String, syntax: CommentSyntax) = CommentScanner.scan(d(text), syntax)
    private fun texts(text: String, syntax: CommentSyntax) =
        comments(text, syntax).map { c -> c.segments.joinToString("|") { d(text).substring(it.start, it.end) } }
    private fun maths(text: String, syntax: CommentSyntax) =
        comments(text, syntax).flatMap { MathFinder.find(d(text), it) }.map { it.latex + if (it.display) " (display)" else "" }

    @Test
    fun csharpLineBlockAndDocComments() {
        val code = """
            |// §L = \sum_i (\phi_0 + \phi_1 x_i - y_i)^2§
            |var x = 1; // trailing §a^2§
            |/* block
            | * second §b§ */
            |/// <summary>Doc §\alpha§</summary>
            |/// more
            |var s = "not // a comment §x§";
            |var v = @"C:\path\" + "// still a string";
            |var r = QQQ
            |   // raw string, not a comment
            |   QQQ;
            |char c = '/'; var d = '"'; // real §c§
            |var i = §"{x} // no";
            """.trimMargin().replace("QQQ", "\"\"\"")
        val cs = comments(code, CommentSyntax.CSHARP)
        assertEquals(listOf(CommentKind.LINE, CommentKind.LINE, CommentKind.BLOCK, CommentKind.DOC, CommentKind.LINE), cs.map { it.kind })
        assertEquals(listOf(false, true, false, false, true), cs.map { it.trailing })
        assertEquals(listOf(""" §L = \sum_i (\phi_0 + \phi_1 x_i - y_i)^2§""", " trailing §a^2§", " block| second §b§ ", " <summary>Doc §\\alpha§</summary>| more", " real §c§").map(::d),
            texts(code, CommentSyntax.CSHARP))
        assertEquals(listOf("L = \\sum_i (\\phi_0 + \\phi_1 x_i - y_i)^2", "a^2", "b", "\\alpha", "c"), maths(code, CommentSyntax.CSHARP))
    }

    @Test
    fun consecutiveLineCommentsMergeSoDisplayMathCanSpanLines() {
        val code = "// The loss:\n//   §§\n//   L = x^2\n//   §§\nint a; // other\n// next\n\n// apart"
        val cs = comments(code, CommentSyntax.C_LIKE)
        assertEquals(4, cs.size)
        assertEquals(4, cs[0].segments.size)
        assertEquals(listOf("L = x^2 (display)"), maths(code, CommentSyntax.C_LIKE))
        val span = cs.flatMap { MathFinder.find(d(code), it) }.single()
        assertEquals("$$", d(code).substring(span.start, span.start + 2))
        assertEquals("$$", d(code).substring(span.end - 2, span.end))
    }

    @Test
    fun javaAndKotlin() {
        val java = "/**\n * Computes §\\sigma(x)§.\n * @return §§\\frac{1}{1+e^{-x}}§§\n */\nString t = \"\"\"\n  // text block\n  \"\"\"; /* end §e§ */"
        assertEquals(listOf("\\sigma(x)", "\\frac{1}{1+e^{-x}} (display)", "e"), maths(java, CommentSyntax.JAVA))
        assertEquals(CommentKind.DOC, comments(java, CommentSyntax.JAVA)[0].kind)
        val kotlin = "/* outer /* nested §a§ */ still §b§ */\nval s = \"§{x} // not\"\n// §c§"
        assertEquals(listOf("a", "b", "c"), maths(kotlin, CommentSyntax.KOTLIN))
        assertEquals(2, comments(kotlin, CommentSyntax.KOTLIN).size)
    }

    @Test
    fun pythonHashCommentsAndDocstrings() {
        val py = "def f(x):\n    \"\"\"Returns §x^2§.\n\n    §§\\int_0^1 x\\,dx§§\n    \"\"\"\n    s = '# not a comment §q§'\n    return x * x  # trailing §y§\n"
        assertEquals(listOf("x^2", "\\int_0^1 x\\,dx (display)", "y"), maths(py, CommentSyntax.PYTHON))
    }

    @Test
    fun rustLifetimesAndOtherLanguages() {
        assertEquals(listOf("x"), maths("fn f<'a>(s: &'a str) {} // §x§", CommentSyntax.RUST))
        assertEquals(listOf("y"), maths("let t = `// template §{z}`; // §y§", CommentSyntax.JS))
        assertEquals(listOf("z"), maths("(* §z§ *) let a = 1", CommentSyntax.FSHARP))
        assertEquals(listOf("w"), maths("-- §w§\nSELECT '-- no §v§'", CommentSyntax.SQL))
        assertEquals(listOf("u"), maths("% §u§\nx = 'a'", CommentSyntax.MATLAB))
    }

    @Test
    fun syntaxByFileName() {
        assertEquals(CommentSyntax.CSHARP, CommentSyntax.forFileName("Model.cs"))
        assertEquals(CommentSyntax.KOTLIN, CommentSyntax.forFileName("build.gradle.kts"))
        assertEquals(CommentSyntax.PYTHON, CommentSyntax.forFileName("train.PY"))
        assertEquals(CommentSyntax.HASH, CommentSyntax.forFileName("CMakeLists.txt"))
        assertNull(CommentSyntax.forFileName("notes.md"))
        assertNull(CommentSyntax.forFileName("README"))
    }

    @Test
    fun unterminatedThingsDoNotBreak() {
        assertEquals(1, comments("int a; /* never closed §x§", CommentSyntax.C_LIKE).size)
        assertEquals(0, comments("var s = \"never closed // no", CommentSyntax.CSHARP).size)
        assertEquals(1, comments("var s = \"never closed\n// yes", CommentSyntax.CSHARP).size)
        assertEquals(listOf("x"), maths("int a; /* never closed §x§", CommentSyntax.C_LIKE))
        assertTrue(comments("", CommentSyntax.CSHARP).isEmpty())
        assertEquals(1, comments("/**/ int a;", CommentSyntax.JAVA).size)
        assertEquals(CommentKind.BLOCK, comments("/**/ int a;", CommentSyntax.JAVA)[0].kind)
        // Windows line endings.
        assertEquals(listOf("a", "b"), maths("// §a§\r\n// §b§\r\n", CommentSyntax.CSHARP))
        assertEquals(1, comments("// §a§\r\n// §b§\r\n", CommentSyntax.CSHARP).size)
    }

    @Test
    fun bigFileIsQuick() {
        val line = "    var y = Forward(x); // §y = W x + b§ and \"quoted // text\" here\n"
        val text = d(line.repeat(50_000))
        val t = System.nanoTime()
        val cs = comments(text, CommentSyntax.CSHARP)
        val spans = cs.flatMap { MathFinder.find(text, it) }
        val ms = (System.nanoTime() - t) / 1e6
        assertEquals(50_000, spans.size)
        assertTrue(ms < 1500, "50,000 lines took " + ms + " ms")
    }
}

class MathFinderTest {
    private fun find(s: String) = MathFinder.findIn(d(s), (0..s.length).toList()).map { it.latex to it.display }

    @Test
    fun dollarsThatAreNotMaths() {
        assertEquals(emptyList<Pair<String, Boolean>>(), find("costs §5 and §10 today"))
        assertEquals(emptyList<Pair<String, Boolean>>(), find("set §HOME and §PATH first"))
        assertEquals(emptyList<Pair<String, Boolean>>(), find("price \\§3 with \\§ signs"))
        assertEquals(emptyList<Pair<String, Boolean>>(), find("code `§x§` is code"))
        assertEquals(emptyList<Pair<String, Boolean>>(), find("§ x§ and §y §"))
        assertEquals(emptyList<Pair<String, Boolean>>(), find("§§   §§"))
        assertEquals(emptyList<Pair<String, Boolean>>(), find("inline §a\nb§ can't span lines"))
    }

    @Test
    fun codeTagsInDocCommentsAreCode() {
        assertEquals(listOf("y" to false), find("<c>§x§</c> and §y§"))
        assertEquals(listOf("z" to false), find("<code class=\"a\">§a§ §b§</code> §z§"))
        assertEquals(listOf("w" to false), find("<pre>\n§p§\n</pre> §w§"))
        assertEquals(listOf("v" to false), find("<cite>§v§</cite>"))
    }

    @Test
    fun allDelimiters() {
        assertEquals(listOf("a+b" to false), find("so §a+b§."))
        assertEquals(listOf("x" to false, "y" to false), find("§x§ and §y§"))
        assertEquals(listOf("E = mc^2" to true), find("§§E = mc^2§§"))
        assertEquals(listOf("\\alpha" to false, "\\beta" to true), find("\\(\\alpha\\) and \\[\\beta\\]"))
        assertEquals(listOf(d("\\§ 5") to false), find("§\\§ 5§"))
        assertEquals(listOf("a \\\\ b" to true), find("§§a \\\\\nb§§").map { it.first.replace("  ", " ") to it.second })
        assertEquals(emptyList<Pair<String, Boolean>>(), find("§x_1§5"), "a digit right after the closing dollar means money")
            .let { }
    }

    @Test
    fun offsetsCoverTheDelimiters() {
        val s = "ab §x^2§ cd"
        val m = MathFinder.findIn(d(s), (0..s.length).toList()).single()
        assertEquals(d("§x^2§"), d(s).substring(m.start, m.end))
        assertFalse(m.display)
    }
}

class MarkerTest {
    private fun spans(code: String, syntax: CommentSyntax) =
        CommentScanner.scan(d(code), syntax).flatMap { MathFinder.find(d(code), it) }

    @Test
    fun lineMarkersInEveryCommentStyle() {
        val cs = spans("//= L = sum_i (phi_0 + phi_1 x_i - y_i)^2\nvar a = 1; //= a^2\n///= x/2\n/*= sqrt(x) */\n/**\n * Prose line.\n * = int_0^1 x dx\n */", CommentSyntax.CSHARP)
        assertEquals(listOf("L = sum_i (phi_0 + phi_1 x_i - y_i)^2", "a^2", "x/2", "sqrt(x)", "int_0^1 x dx"), cs.map { it.original })
        assertTrue(cs.all { it.source == dev.latexcomments.scan.MathSource.ASCIIMATH && it.display })
        assertEquals("\\frac{x}{2}", cs[2].latex)
        assertEquals(listOf("x^2"), spans("x = 1  #= x^2\n", CommentSyntax.PYTHON).map { it.original })
        assertEquals(listOf("x^2"), spans("--= x^2\n", CommentSyntax.SQL).map { it.original })
        assertEquals(listOf("x^2"), spans("%= x^2\n", CommentSyntax.MATLAB).map { it.original })
    }

    @Test
    fun aMarkedBlockIsMathsOnEveryLine() {
        val code = "/*= theta := theta - eta grad L\n    p(x) = 1/sqrt(2 pi)\n\n    = y^2 */\n/* plain\n   x^2 */"
        assertEquals(listOf("theta := theta - eta grad L", "p(x) = 1/sqrt(2 pi)", "y^2"), spans(code, CommentSyntax.CSHARP).map { it.original })
    }

    @Test
    fun theMarkerStartsAtTheEqualsSign() {
        val code = "    //= x^2\n"
        val s = spans(code, CommentSyntax.CSHARP).single()
        assertEquals("= x^2", code.substring(s.start, s.end))
    }

    @Test
    fun separatorsAndOrdinaryCommentsAreNotMarked() {
        for (c in listOf("//=====\n", "//==== Section ====\n", "// = 5 after the loop\n", "//=\n", "//= \n", "//= ---\n", "/*=======*/\n"))
            assertEquals(emptyList<Any>(), spans(c, CommentSyntax.CSHARP), c)
    }

    @Test
    fun latexKeepsWorkingExactlyAsBefore() {
        val s = spans("//= §x^2§\n// §\\alpha§ and §§\\beta§§\n", CommentSyntax.CSHARP)
        assertEquals(listOf("x^2", "\\alpha", "\\beta"), s.map { it.latex })
        assertTrue(s.all { it.source == dev.latexcomments.scan.MathSource.LATEX })
    }

    @Test
    fun markersCanBeTurnedOff() {
        val code = "//= x^2\n"
        assertEquals(0, CommentScanner.scan(code, CommentSyntax.CSHARP).flatMap { MathFinder.find(code, it, dev.latexcomments.scan.FindOptions(markers = false)) }.size)
    }
}
