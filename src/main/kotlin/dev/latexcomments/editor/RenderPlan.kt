package dev.latexcomments.editor

import dev.latexcomments.scan.Comment
import dev.latexcomments.scan.CommentKind
import dev.latexcomments.scan.CommentScanner
import dev.latexcomments.scan.CommentSyntax
import dev.latexcomments.scan.MathFinder
import dev.latexcomments.scan.MathSpan

/**
 * What to draw for a file's text. Pure (no IDE classes), so it is unit-tested directly.
 *
 * - [Lines]: comment-only lines holding maths are drawn in full (comment text plus formulas at full size, the line
 *   growing as tall as the formula needs). Display maths that spans lines is drawn on one row.
 * - [Inline]: maths in a comment that follows code on the same line is drawn in place of its text, shrunk to fit.
 */
sealed class PlanItem {
    abstract val start: Int
    abstract val end: Int

    /** Document lines [firstLine]..[lastLine], from [start] (first line start) to [end] (last line end). */
    data class Lines(override val start: Int, override val end: Int, val firstLine: Int, val lastLine: Int,
                     val spans: List<MathSpan>, val kind: CommentKind) : PlanItem()

    data class Inline(val span: MathSpan, val kind: CommentKind, val line: Int) : PlanItem() {
        override val start get() = span.start
        override val end get() = span.end
    }
}

/** Line starts of a text, for offset-to-line lookups. */
class LineIndex(private val text: CharSequence) {
    private val starts: IntArray = run {
        val list = ArrayList<Int>()
        list.add(0)
        for (i in text.indices) if (text[i] == '\n') list.add(i + 1)
        list.toIntArray()
    }
    val lineCount get() = starts.size
    fun lineOf(offset: Int): Int {
        var lo = 0; var hi = starts.size - 1
        while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (starts[m] <= offset) lo = m else hi = m - 1 }
        return lo
    }
    fun lineStart(line: Int) = starts[line]
    /** End of the line's text (before "\r\n" or "\n"). */
    fun lineEnd(line: Int): Int {
        var e = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
        if (e > starts[line] && text[e - 1] == '\r') e--
        return e
    }
    fun firstNonSpace(line: Int): Int {
        var k = lineStart(line); val e = lineEnd(line)
        while (k < e && text[k].isWhitespace()) k++
        return k
    }
    fun lastNonSpaceEnd(line: Int): Int {
        var k = lineEnd(line); val s = lineStart(line)
        while (k > s && text[k - 1].isWhitespace()) k--
        return k
    }
}

object RenderPlan {
    fun plan(text: CharSequence, syntax: CommentSyntax, trailing: Boolean = true,
             options: dev.latexcomments.scan.FindOptions = dev.latexcomments.scan.FindOptions()): List<PlanItem> {
        val index = LineIndex(text)
        val out = ArrayList<PlanItem>()
        for (c in CommentScanner.scan(text, syntax)) {
            val spans = MathFinder.find(text, c, options)
            if (spans.isEmpty()) continue
            val lineSpans = ArrayList<MathSpan>()
            for (s in spans) {
                val first = index.lineOf(s.start)
                val last = index.lineOf(maxOf(s.start, s.end - 1))
                if (commentOnly(c, index, first, last)) lineSpans.add(s)
                // After code, AsciiMath uses the compact inline style so it fits the line.
                else if (trailing && first == last) out.add(PlanItem.Inline(
                    if (s.source == dev.latexcomments.scan.MathSource.LATEX) s else s.copy(display = false), c.kind, first))
            }
            out.addAll(groupLines(lineSpans, index, c.kind))
        }
        return out.sortedBy { it.start }
    }

    /** True when lines [first]..[last] hold nothing but this comment (and spaces). */
    private fun commentOnly(c: Comment, index: LineIndex, first: Int, last: Int): Boolean =
        (first..last).all { l -> index.firstNonSpace(l) >= c.start && index.lastNonSpaceEnd(l) <= c.end }

    /** Spans whose lines touch are drawn together. */
    private fun groupLines(spans: List<MathSpan>, index: LineIndex, kind: CommentKind): List<PlanItem.Lines> {
        val out = ArrayList<PlanItem.Lines>()
        var first = -1; var last = -1
        val current = ArrayList<MathSpan>()
        fun flush() {
            if (current.isEmpty()) return
            out.add(PlanItem.Lines(index.lineStart(first), index.lineEnd(last), first, last, current.toList(), kind))
            current.clear()
        }
        for (s in spans.sortedBy { it.start }) {
            val a = index.lineOf(s.start); val b = index.lineOf(maxOf(s.start, s.end - 1))
            if (current.isNotEmpty() && a <= last) { last = maxOf(last, b); current.add(s) }
            else { flush(); first = a; last = b; current.add(s) }
        }
        flush()
        return out
    }
}
