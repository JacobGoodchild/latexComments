package dev.latexcomments.render

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.util.Collections
import javax.swing.JLabel

/**
 * A formula ready to draw: either the typeset maths, or a short plain error message. Sizes are in logical pixels
 * (the IDE scales the drawing for high-DPI screens, and the maths is drawn as shapes, so it stays sharp).
 */
class Formula private constructor(private val icon: TeXIcon?, val error: String?, private val errorFont: Font) {
    val width: Float
    /** From the top to the baseline. */
    val ascent: Float
    /** From the baseline to the bottom. */
    val descent: Float
    val height get() = ascent + descent
    val isError get() = icon == null

    init {
        if (icon != null) {
            val insets = icon.insets
            width = icon.trueIconWidth + insets.left + insets.right
            // trueIconHeight is the whole height (above and below the baseline).
            ascent = icon.trueIconHeight - icon.trueIconDepth + insets.top
            descent = icon.trueIconDepth + insets.bottom
        } else {
            val bounds = errorFont.getStringBounds(error, FRC)
            val lm = errorFont.getLineMetrics(error, FRC)
            width = bounds.width.toFloat() + 8f
            ascent = lm.ascent + 2f
            descent = lm.descent + 2f
        }
    }

    /** Draws with the baseline at [baselineY], scaled by [scale] (1 = full size). */
    fun paint(g: Graphics2D, x: Float, baselineY: Float, color: Color, errorColor: Color, scale: Float = 1f) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.translate(x.toDouble(), baselineY.toDouble())
            if (scale != 1f) g2.scale(scale.toDouble(), scale.toDouble())
            g2.translate(0.0, -ascent.toDouble())
            if (icon != null) {
                synchronized(icon) {
                    icon.setForeground(color)
                    icon.paintIcon(COMPONENT, g2, 0, 0)
                }
            } else {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.color = errorColor
                g2.font = errorFont
                g2.drawString(error, 4f, ascent - 2f)
                g2.stroke = java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, floatArrayOf(2f, 2f), 0f)
                g2.draw(java.awt.geom.Line2D.Float(4f, ascent + 1f, width - 4f, ascent + 1f))
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private val FRC = FontRenderContext(null, true, true)
        private val COMPONENT = JLabel()

        private data class Key(val latex: String, val size: Float, val display: Boolean, val errorFont: Font)
        private val cache: MutableMap<Key, Formula> = Collections.synchronizedMap(object : LinkedHashMap<Key, Formula>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Formula>?) = size > 2000
        })

        /**
         * Typesets [latex] at [size] pixels ([display]: display style, as for `$$`). Never throws: a formula that
         * can't be read becomes a short error message drawn in [errorFont].
         */
        fun of(latex: String, size: Float, display: Boolean, errorFont: Font): Formula =
            cache.getOrPut(Key(latex, size, display, errorFont)) { create(latex, size, display, errorFont) }

        /** Common commands JLaTeXMath doesn't know, defined once (its \\newcommand definitions are global). */
        private val MACROS = listOf(
            "\\newcommand{\\argmin}{\\mathop{\\mathrm{argmin}}}",
            "\\newcommand{\\argmax}{\\mathop{\\mathrm{argmax}}}",
            "\\newcommand{\\bm}[1]{\\boldsymbol{#1}}",
            "\\newcommand{\\coloneqq}{\\mathrel{:=}}",
            "\\newcommand{\\eqqcolon}{\\mathrel{=:}}",
            "\\newcommand{\\color}[2]{\\textcolor{#1}{#2}}",
            "\\newcommand{\\cancel}[1]{\\not{#1}}",
        )
        private val macrosDefined: Boolean by lazy {
            synchronized(TeXFormula::class.java) {
                for (m in MACROS) try { TeXFormula(m) } catch (_: Exception) {}
            }
            true
        }

        private fun create(latex: String, size: Float, display: Boolean, errorFont: Font): Formula = try {
            check(macrosDefined)
            val formula = synchronized(TeXFormula::class.java) { TeXFormula(latex) }
            val icon = formula.createTeXIcon(if (display) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT, size)
            icon.insets = java.awt.Insets(1, 1, 1, 1)
            if (icon.trueIconWidth <= 0f) Formula(null, "empty formula", errorFont) else Formula(icon, null, errorFont)
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            Formula(null, errorMessage(e), errorFont)
        }

        /** A short, plain message: "LaTeX error: unknown command \foo". */
        internal fun errorMessage(e: Throwable): String {
            val all = generateSequence(e) { it.cause }.take(5).mapNotNull { it.message }.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            // The most useful part first: the command that isn't known, wherever it is in the message.
            val unknown = Regex("Unknown symbol or command or predefined TeXFormula: '([^']*)'").find(all)
            val text = when {
                unknown != null -> "unknown command \\" + unknown.groupValues[1]
                all.isEmpty() -> "can't read this formula"
                else -> all.replace(Regex(" at position \\d+:\\d+"), "").replaceFirstChar { it.lowercase() }
            }
            val short = if (text.length > 60) text.take(57) + "..." else text
            return "LaTeX error: $short"
        }

        fun clearCache() = cache.clear()
    }
}
