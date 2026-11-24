package dev.latexcomments

import dev.latexcomments.scan.CommentScanner
import dev.latexcomments.scan.CommentSyntax
import dev.latexcomments.scan.FindOptions
import dev.latexcomments.scan.MathFinder
import dev.latexcomments.scan.MathGuess
import dev.latexcomments.scan.MathSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The automatic mode must leave ordinary comments alone; a miss is better than a false alarm. */
class MathGuessTest {
    /** Comments as they appear in real code. None of these is a formula. */
    private val notMaths = listOf(
        "a^b", "flags ^ mask", "x ^= y", "hash = hash ^ (hash >> 16)", "i++", "count--", "if (a == b) return;",
        "max_value is the largest allowed", "learning_rate = 0.01", "MAX_BATCH_SIZE = 64", "user_id_map",
        "TODO: fix x_1", "FIXME x^2 overflows", "NOTE: theta must be positive", "HACK: sum_i hack",
        "see https://en.wikipedia.org/wiki/Sigmoid_function", "C:/Users/me/data/train.csv", "./data/mnist/train",
        "~/models/alpha/v2", "returns the sum of x and y", "computes x^2 for every element", "O(n^2) time",
        "Sort by alpha then beta", "alpha channel is premultiplied", "theta: model parameters", "phi is the angle in radians",
        "var y_hat = W * x + b;", "int x = 5", "double sigma = Math.Sqrt(var);", "x = Math.Pow(a, 2);", "y = f();",
        "List<int> xs = new();", "arr[i] = arr[i - 1] + 1", "<summary>Gets the loss.</summary>", "<param name=\"x\">input</param>",
        "@param x the input", "#region helpers", "\"quoted text\"", "nameof(x)", "x => x * x", "a ?? b", "obj?.Value",
        "---------------------------------", "=========", "*************", "1. Load the data", "- remove x_1 later",
        "Step 2: compute the loss", "v2.0.1", "dims: 28x28", "28 x 28 pixels", "3.14", "x", "OK", "",
        "camelCase^2 issue", "isValid && x > 0", "pi_estimate = 4 * inside / total", "e.g. a/b/c", "and/or",
        "(see above)", "x_1, x_2 and x_3 are inputs", "temp^2 - see notes", "Copyright (c) 2026", "#pragma warning disable",
        "Example: y = x^2", "sum of squares", "the delta between runs", "alpha, beta, gamma", "lambda expression below",
    )

    /** Lines that are clearly formulas, written the way people write maths without LaTeX. */
    private val maths = listOf(
        "L = sum_i (phi_0 + phi_1 x_i - y_i)^2",
        "x^2 + y^2 = r^2",
        "sigma^2 = 1/N sum_i (x_i - mu)^2",
        "y = sqrt(x^2 + 1)",
        "theta := theta - eta grad L",
        "alpha = 0.01",
        "E = m c^2",
        "f(x) = int_0^1 g(t) dt",
        "p(x) = 1/(sigma sqrt(2 pi)) e^(-(x-mu)^2/(2 sigma^2))",
        "lim_(n->oo) (1 + 1/n)^n = e",
    )

    /**
     * Formulas the automatic mode misses on purpose: with only subscripts and an equals sign they look just like
     * commented-out code ("x_0 = y_1"). Writing "//=" in front shows them.
     */
    private val missedOnPurpose = listOf("a_(n+1) = a_n + d", "x_0 = y_1", "y = W x + b")

    @Test
    fun weakCasesAreLeftForTheMarker() {
        assertEquals(emptyList<String>(), missedOnPurpose.filter { MathGuess.looksLikeMaths(it) })
    }

    @Test
    fun ordinaryCommentsAreLeftAlone() {
        val wrong = notMaths.filter { MathGuess.looksLikeMaths(it) }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun clearFormulasAreRead() {
        val missed = maths.filterNot { MathGuess.looksLikeMaths(it) }
        assertEquals(emptyList<String>(), missed)
    }

    private fun spans(code: String, options: FindOptions) =
        CommentScanner.scan(code, CommentSyntax.CSHARP).flatMap { MathFinder.find(code, it, options) }

    @Test
    fun automaticModeIsOffUnlessAsked() {
        val code = "// x^2 + y^2 = r^2\n"
        assertEquals(0, spans(code, FindOptions()).size)
        val s = spans(code, FindOptions(auto = true)).single()
        assertEquals(MathSource.ASCIIMATH_AUTO, s.source)
        assertEquals("x^2 + y^2 = r^2", s.original)
        assertEquals(0, spans(code, FindOptions(auto = true, accept = { false })).size, "a formula that wouldn't render is skipped")
    }

    @Test
    fun automaticModeNeverTouchesLatexOrMarkedLines() {
        val code = d("// §x^2§ and y^2 = 1\n//= z^2 = 1\n")
        val s = spans(code, FindOptions(auto = true))
        assertEquals(listOf(MathSource.LATEX, MathSource.ASCIIMATH), s.map { it.source })
    }

    @Test
    fun aMixedFileOnlyGetsItsFormulas() {
        val code = notMaths.joinToString("") { "// $it\n" } + maths.joinToString("") { "// $it\n" }
        val found = spans(code, FindOptions(auto = true)).map { it.original }
        assertEquals(maths, found)
    }

    @Test
    fun nothingFoundInPlainProse() {
        assertNull(MathFinder.markedLine("returns x", dev.latexcomments.scan.Segment(0, 9), false))
    }
}
