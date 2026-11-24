package dev.latexcomments.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import dev.latexcomments.render.Formula
import dev.latexcomments.scan.CommentKind
import dev.latexcomments.scan.MathSpan
import dev.latexcomments.settings.LatexSettings
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Colours, fonts and sizes from the editor's current colour scheme and font size. */
internal object EditorLook {
    fun commentAttributes(editor: Editor, kind: CommentKind): TextAttributes {
        val key = when (kind) {
            CommentKind.DOC -> DefaultLanguageHighlighterColors.DOC_COMMENT
            CommentKind.BLOCK -> DefaultLanguageHighlighterColors.BLOCK_COMMENT
            CommentKind.LINE -> DefaultLanguageHighlighterColors.LINE_COMMENT
        }
        return editor.colorsScheme.getAttributes(key) ?: TextAttributes()
    }

    fun commentColor(editor: Editor, kind: CommentKind): Color =
        commentAttributes(editor, kind).foregroundColor ?: editor.colorsScheme.defaultForeground

    fun commentFont(editor: Editor, kind: CommentKind): Font =
        editor.colorsScheme.getFont(EditorFontType.forJavaStyle(commentAttributes(editor, kind).fontType))

    fun errorColor(editor: Editor): Color =
        editor.colorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)?.effectColor
            ?: editor.colorsScheme.getAttributes(HighlightInfoType.ERROR.attributesKey)?.effectColor ?: JBColor.RED

    fun mathSize(editor: Editor): Float = editor.colorsScheme.editorFontSize2D * LatexSettings.get().sizePercent / 100f

    fun formula(editor: Editor, span: MathSpan, size: Float = mathSize(editor)): Formula =
        Formula.of(span.latex, size, span.display, editor.colorsScheme.getFont(EditorFontType.PLAIN).deriveFont(editor.colorsScheme.editorFontSize2D * 0.85f))

    fun setup(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    }
}

/** Maths in a comment after code: drawn in place of its text, shrunk to fit the line. */
class InlineMathRenderer(val span: MathSpan, private val kind: CommentKind) : EditorCustomElementRenderer {
    private fun scaleFor(editor: Editor, f: Formula): Float = min(1f, (editor.lineHeight - 1f) / max(1f, f.height))

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val f = EditorLook.formula(inlay.editor, span)
        return max(1, ceil(f.width * scaleFor(inlay.editor, f)).toInt())
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val editor = inlay.editor
        val f = EditorLook.formula(editor, span)
        val scale = scaleFor(editor, f)
        val g2 = g as Graphics2D
        EditorLook.setup(g2)
        val top = targetRegion.y + (targetRegion.height - f.height * scale) / 2f
        f.paint(g2, targetRegion.x.toFloat(), top + f.ascent * scale, EditorLook.commentColor(editor, kind), EditorLook.errorColor(editor), scale)
    }
}

private typealias Rectangle = java.awt.Rectangle

/**
 * Comment-only lines with maths: the comment's text and its formulas at full size, one row per line (display
 * maths spanning lines takes one row). The line grows as tall as the tallest formula.
 */
class LinesMathRenderer(private val item: PlanItem.Lines) : CustomFoldRegionRenderer {
    /** A row: pieces of text and formulas from left to right. */
    private class Row(val pieces: List<Any>)

    private class Layout(val rows: List<Row>, val widths: List<Float>, val ascents: List<Float>, val heights: List<Float>, val width: Float, val height: Float)

    private fun rows(region: CustomFoldRegion): List<Row> {
        val text = region.editor.document.immutableCharSequence
        val start = region.startOffset
        val end = min(region.endOffset, text.length)
        val base = item.start
        val spans = item.spans.map { it.copy(start = it.start - base + start, end = it.end - base + start) }
        val rows = ArrayList<Row>()
        var pieces = ArrayList<Any>()
        val sb = StringBuilder()
        var k = start
        var si = 0
        while (k < end) {
            val s = spans.getOrNull(si)
            if (s != null && k == s.start) {
                // "//= x^2" shows as "// x²": the formula replaces the "=", with a space after the comment marker.
                if (s.source != dev.latexcomments.scan.MathSource.LATEX && sb.isNotEmpty() && !sb.last().isWhitespace()) sb.append(' ')
                if (sb.isNotEmpty()) { pieces.add(sb.toString()); sb.setLength(0) }
                pieces.add(s)
                k = s.end; si++
                continue
            }
            val ch = text[k]
            if (ch == '\n') {
                if (sb.isNotEmpty()) { pieces.add(sb.toString()); sb.setLength(0) }
                rows.add(Row(pieces)); pieces = ArrayList()
            } else if (ch != '\r') sb.append(ch)
            k++
        }
        if (sb.isNotEmpty()) pieces.add(sb.toString())
        rows.add(Row(pieces))
        return rows
    }

    private fun expandTabs(s: String, column: Int, tabSize: Int): String {
        if ('\t' !in s) return s
        val out = StringBuilder()
        var col = column
        for (ch in s) {
            if (ch == '\t') { val n = tabSize - col % tabSize; repeat(n) { out.append(' ') }; col += n } else { out.append(ch); col++ }
        }
        return out.toString()
    }

    private fun layout(region: CustomFoldRegion): Layout {
        val editor = region.editor
        val font = EditorLook.commentFont(editor, item.kind)
        val fm = editor.contentComponent.getFontMetrics(font)
        val tab = editor.settings.getTabSize(editor.project)
        val rows = rows(region)
        val widths = ArrayList<Float>(); val ascents = ArrayList<Float>(); val heights = ArrayList<Float>()
        val pad = 2f
        for (row in rows) {
            var w = 0f; var col = 0
            var asc = editor.ascent.toFloat()
            var desc = (editor.lineHeight - editor.ascent).toFloat()
            for (p in row.pieces) {
                if (p is String) { val t = expandTabs(p, col, tab); col += t.length; w += fm.stringWidth(t) }
                else if (p is MathSpan) {
                    val f = EditorLook.formula(editor, p)
                    w += f.width; col += 1
                    asc = max(asc, f.ascent + pad); desc = max(desc, f.descent + pad)
                }
            }
            widths.add(w); ascents.add(asc); heights.add(asc + desc)
        }
        return Layout(rows, widths, ascents, heights, widths.maxOrNull() ?: 0f, heights.sum())
    }

    override fun calcWidthInPixels(region: CustomFoldRegion): Int = max(1, ceil(layout(region).width).toInt())

    override fun calcHeightInPixels(region: CustomFoldRegion): Int =
        max(region.editor.lineHeight, ceil(layout(region).height).toInt())

    override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        val editor = region.editor
        val l = layout(region)
        val font = EditorLook.commentFont(editor, item.kind)
        val color = EditorLook.commentColor(editor, item.kind)
        val error = EditorLook.errorColor(editor)
        val tab = editor.settings.getTabSize(editor.project)
        val g2 = g.create() as Graphics2D
        try {
            EditorLook.setup(g2)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, editor.contentComponent.let {
                (it.getClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING) ?: RenderingHints.VALUE_TEXT_ANTIALIAS_ON) })
            g2.font = font
            val fm = g2.fontMetrics
            var y = targetRegion.y.toFloat()
            l.rows.forEachIndexed { i, row ->
                val baseline = y + l.ascents[i]
                var x = targetRegion.x.toFloat()
                var col = 0
                for (p in row.pieces) {
                    if (p is String) {
                        val t = expandTabs(p, col, tab); col += t.length
                        g2.color = color
                        g2.drawString(t, x, baseline)
                        x += fm.stringWidth(t)
                    } else if (p is MathSpan) {
                        val f = EditorLook.formula(editor, p)
                        f.paint(g2, x, baseline, color, error)
                        x += f.width; col += 1
                    }
                }
                y += l.heights[i]
            }
        } finally {
            g2.dispose()
        }
    }

    /** The formula at a point inside the region, for the hover preview. */
    fun formulaAt(region: CustomFoldRegion, regionX: Double, regionY: Double, px: Double, py: Double): MathSpan? {
        val editor = region.editor
        val l = layout(region)
        val fm = editor.contentComponent.getFontMetrics(EditorLook.commentFont(editor, item.kind))
        val tab = editor.settings.getTabSize(editor.project)
        var y = regionY
        l.rows.forEachIndexed { i, row ->
            if (py >= y && py < y + l.heights[i]) {
                var x = regionX; var col = 0
                for (p in row.pieces) {
                    val w = if (p is String) { val t = expandTabs(p, col, tab); col += t.length; fm.stringWidth(t).toDouble() }
                            else { col += 1; EditorLook.formula(editor, p as MathSpan).width.toDouble() }
                    if (p is MathSpan && px >= x && px < x + w) return p
                    x += w
                }
                return null
            }
            y += l.heights[i]
        }
        return null
    }
}
