package dev.latexcomments.scan

/**
 * One formula in a comment. [start]..[end] (end exclusive) cover the delimiters in the document; [latex] is the
 * formula without delimiters (comment decoration of following lines removed, lines joined by spaces).
 */
data class MathSpan(
    val start: Int, val end: Int, val latex: String, val display: Boolean,
    /** How the formula was written: LaTeX between `$`s, AsciiMath on a `//=` line, or AsciiMath read automatically. */
    val source: MathSource = MathSource.LATEX,
    /** The AsciiMath as written, for the hover preview. */
    val original: String? = null,
)

enum class MathSource { LATEX, ASCIIMATH, ASCIIMATH_AUTO }

/** What to look for. [accept] can reject an automatically found formula (for example one that wouldn't render). */
class FindOptions(val markers: Boolean = true, val auto: Boolean = false, val accept: (String) -> Boolean = { true })

/**
 * Finds `$...$`, `$$...$$`, `\(...\)` and `\[...\]` in comment text, using the rules Markdown maths uses, so that
 * prices and shell variables ("$5 and $10", "$HOME") are not mistaken for maths:
 * an opening `$` is not followed by a space, a closing `$` is not preceded by a space nor followed by a digit,
 * `\$` is a dollar sign, and text in `backticks` is code. Inline maths stays on one line; display maths may span lines.
 */
object MathFinder {
    private const val MAX_LENGTH = 2000

    fun find(text: CharSequence, comment: Comment, options: FindOptions = FindOptions()): List<MathSpan> {
        val out = ArrayList<MathSpan>()
        // Lines marked as AsciiMath ("//= ...", " * = ...") are whole formulas.
        val rest = ArrayList<Segment>()
        for (seg in comment.segments) {
            val marked = if (options.markers) markedLine(text, seg, allowIndent = !comment.lineStyle) else null
            if (marked != null) out.add(marked) else rest.add(seg)
        }
        if (rest.isEmpty()) return out
        // The other lines as one string (so $$ can span lines), with each character's document offset.
        val sb = StringBuilder()
        val offsets = ArrayList<Int>()
        rest.forEachIndexed { idx, seg ->
            if (idx > 0) { sb.append('\n'); offsets.add(seg.start) }
            for (k in seg.start until seg.end) { sb.append(text[k]); offsets.add(k) }
        }
        offsets.add(rest.last().end)
        val latex = findIn(sb, offsets)
        out.addAll(latex)
        // Optional: lines that are clearly maths though nobody marked them.
        if (options.auto) {
            for (seg in rest) {
                if (latex.any { it.end > seg.start && it.start < seg.end }) continue
                auto(text, seg, options)?.let { out.add(it) }
            }
        }
        return out.sortedBy { it.start }
    }

    /**
     * A line that starts with "= " right after the comment marker ("//= x^2"), or in a block or doc comment after
     * its decoration (" * = x^2"), holds AsciiMath. "//=====" separator lines are not formulas (a space must follow).
     */
    internal fun markedLine(text: CharSequence, seg: Segment, allowIndent: Boolean): MathSpan? {
        var k = seg.start
        if (allowIndent) while (k < seg.end && (text[k] == ' ' || text[k] == '\t')) k++
        if (k + 1 >= seg.end || text[k] != '=' || (text[k + 1] != ' ' && text[k + 1] != '\t')) return null
        val eq = k
        var a = k + 1
        while (a < seg.end && text[a].isWhitespace()) a++
        var b = seg.end
        while (b > a && text[b - 1].isWhitespace()) b--
        if (a >= b) return null
        val source = text.subSequence(a, b).toString()
        // "$" means LaTeX: such lines keep working exactly as before.
        if (source.none { it.isLetterOrDigit() } || '$' in source) return null
        return MathSpan(eq, b, dev.latexcomments.asciimath.AsciiMath.toLatex(source), display = true,
            source = MathSource.ASCIIMATH, original = source)
    }

    private fun auto(text: CharSequence, seg: Segment, options: FindOptions): MathSpan? {
        var a = seg.start
        while (a < seg.end && text[a].isWhitespace()) a++
        var b = seg.end
        while (b > a && text[b - 1].isWhitespace()) b--
        if (a >= b) return null
        val source = text.subSequence(a, b).toString()
        if (!MathGuess.looksLikeMaths(source)) return null
        val latex = dev.latexcomments.asciimath.AsciiMath.toLatex(source)
        if (!options.accept(latex)) return null
        return MathSpan(a, b, latex, display = false, source = MathSource.ASCIIMATH_AUTO, original = source)
    }

    /** [offsets] has one more entry than [s] (the document offset just after the last character). */
    internal fun findIn(s: CharSequence, offsets: List<Int>): List<MathSpan> {
        val out = ArrayList<MathSpan>()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < n && (s[i + 1] == '(' || s[i + 1] == '[') -> {
                    val display = s[i + 1] == '['
                    val closer = if (display) "\\]" else "\\)"
                    val close = indexOf(s, closer, i + 2, allowNewline = display)
                    if (close > i + 2) {
                        out.add(span(s, offsets, i, close + 2, i + 2, close, display))
                        i = close + 2
                        continue
                    }
                    i += 2
                }
                c == '\\' -> i += 2 // an escaped character, such as \$
                c == '<' && codeTagAt(s, i) != null -> {
                    // <c>, <code> and <pre> in documentation comments hold code, not maths.
                    val name = codeTagAt(s, i)!!
                    val close = indexOfIgnoreCase(s, "</$name>", i + name.length + 2)
                    i = if (close > 0) close + name.length + 3 else i + 1
                }
                c == '`' -> {
                    val close = indexOf(s, "`", i + 1, allowNewline = false)
                    i = if (close > 0) close + 1 else i + 1
                }
                c == '$' && i + 1 < n && s[i + 1] == '$' -> {
                    val close = indexOf(s, "$$", i + 2, allowNewline = true)
                    if (close > i + 2 && s.subSequence(i + 2, close).isNotBlank()) {
                        out.add(span(s, offsets, i, close + 2, i + 2, close, true))
                        i = close + 2
                        continue
                    }
                    i += 2
                }
                c == '$' -> {
                    val close = inlineClose(s, i)
                    if (close > 0) {
                        out.add(span(s, offsets, i, close + 1, i + 1, close, false))
                        i = close + 1
                        continue
                    }
                    i++
                }
                else -> i++
            }
        }
        return out
    }

    private val codeTags = listOf("code", "pre", "c")

    private fun codeTagAt(s: CharSequence, i: Int): String? = codeTags.firstOrNull { t ->
        i + t.length + 1 < s.length && s.regionMatches(i + 1, t, 0, t.length, ignoreCase = true) &&
            (s[i + 1 + t.length] == '>' || s[i + 1 + t.length] == ' ')
    }

    private fun indexOfIgnoreCase(s: CharSequence, what: String, from: Int): Int {
        var k = from
        while (k <= s.length - what.length) { if (s.regionMatches(k, what, 0, what.length, ignoreCase = true)) return k; k++ }
        return -1
    }

    private fun inlineClose(s: CharSequence, open: Int): Int {
        val first = open + 1
        if (first >= s.length || s[first].isWhitespace() || s[first] == '$') return -1
        var k = first
        while (k < s.length && k - open < MAX_LENGTH) {
            val ch = s[k]
            if (ch == '\n') return -1
            if (ch == '\\') { k += 2; continue }
            if (ch == '$') {
                val before = s[k - 1]
                val after = if (k + 1 < s.length) s[k + 1] else ' '
                if (!before.isWhitespace() && !after.isDigit() && k > first) return k
                if (!before.isWhitespace() && after.isDigit()) return -1
            }
            k++
        }
        return -1
    }

    private fun indexOf(s: CharSequence, what: String, from: Int, allowNewline: Boolean): Int {
        var k = from
        while (k <= s.length - what.length && k - from < MAX_LENGTH) {
            if (!allowNewline && s[k] == '\n') return -1
            if (s[k] == '\\' && what != "\\]" && what != "\\)") { k += 2; continue }
            var match = true
            for (m in what.indices) if (s[k + m] != what[m]) { match = false; break }
            if (match) return k
            k++
        }
        return -1
    }

    private fun span(s: CharSequence, offsets: List<Int>, from: Int, to: Int, innerFrom: Int, innerTo: Int, display: Boolean): MathSpan {
        val latex = s.subSequence(innerFrom, innerTo).toString().replace('\n', ' ').trim()
        return MathSpan(offsets[from], offsets[to - 1] + 1, latex, display)
    }
}
