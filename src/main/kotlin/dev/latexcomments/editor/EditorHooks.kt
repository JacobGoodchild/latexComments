package dev.latexcomments.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.JBUI
import dev.latexcomments.render.Formula
import dev.latexcomments.scan.CommentSyntax
import dev.latexcomments.scan.MathSpan
import dev.latexcomments.settings.LatexSettings
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import javax.swing.JComponent

/** Attaches a [MathEditorController] to every code editor (not to consoles, diffs or previews). */
class MathEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor as? EditorEx ?: return
        if (editor.editorKind != EditorKind.MAIN_EDITOR) return
        val file = editor.virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val syntax = CommentSyntax.forFileName(file.name) ?: return
        val controller = MathEditorController(editor, syntax)
        editor.putUserData(MathEditorController.KEY, controller)
        editor.addEditorMouseMotionListener(HoverPreview(controller), controller)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val c = MathEditorController.of(event.editor) ?: return
        event.editor.putUserData(MathEditorController.KEY, null)
        Disposer.dispose(c)
    }
}

/** Redraws all formulas when settings or the colour scheme change. */
class SettingsRefresher : LatexSettings.Listener, EditorColorsListener {
    override fun settingsChanged() {
        Formula.clearCache()
        MathEditorController.forAllEditors { it.refreshAll() }
    }

    override fun globalSchemeChange(scheme: EditorColorsScheme?) = settingsChanged()
}

/** A larger picture of the formula under the mouse (rendered or raw), without taking focus. */
class HoverPreview(private val controller: MathEditorController) : EditorMouseMotionListener {
    private var hint: LightweightHint? = null
    private var hintFor: String? = null

    override fun mouseMoved(e: EditorMouseEvent) {
        if (!LatexSettings.get().hoverPreview || !LatexSettings.get().enabled || e.area != EditorMouseEventArea.EDITING_AREA) { hide(); return }
        val span = spanUnderMouse(e)
        if (span == null) { hide(); return }
        if (hint?.isVisible == true && hintFor == span.latex) return
        show(e, span)
    }

    private fun spanUnderMouse(e: EditorMouseEvent): MathSpan? {
        (e.inlay?.renderer as? InlineMathRenderer)?.let { return it.span }
        val region = e.collapsedFoldRegion
        if (region is CustomFoldRegion) {
            val r = region.renderer as? LinesMathRenderer ?: return null
            val at = region.location ?: return null
            return r.formulaAt(region, at.x.toDouble(), at.y.toDouble(), e.mouseEvent.x.toDouble(), e.mouseEvent.y.toDouble())
        }
        if (!e.isOverText) return null
        return controller.spanAt(e.offset)
    }

    private fun show(e: EditorMouseEvent, span: MathSpan) {
        hide()
        val editor = e.editor
        val size = EditorLook.mathSize(editor) * 1.8f
        val f = EditorLook.formula(editor, span, size)
        val color = editor.colorsScheme.defaultForeground
        val error = EditorLook.errorColor(editor)
        val pad = JBUI.scale(8)
        val component = object : JComponent() {
            init {
                preferredSize = Dimension(f.width.toInt() + 2 * pad, f.height.toInt() + 2 * pad)
                isOpaque = false
            }
            override fun paintComponent(g: Graphics) {
                EditorLook.setup(g as Graphics2D)
                f.paint(g, pad.toFloat(), pad + f.ascent, color, error)
            }
        }
        val panel = HintUtil.createInformationLabel("").apply { removeAll(); layout = java.awt.BorderLayout(); add(component) }
        val h = LightweightHint(panel)
        val p = Point(e.mouseEvent.x, e.mouseEvent.y + editor.lineHeight)
        val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING or HintManager.HIDE_BY_OTHER_HINT
        HintManagerImpl.getInstanceImpl().showEditorHint(h, editor, p, flags, 0, false)
        hint = h; hintFor = span.latex
    }

    private fun hide() {
        hint?.hide(); hint = null; hintFor = null
    }
}

/** View > Render LaTeX in Comments (also in the editor's right-click menu). */
class ToggleLatexAction : ToggleAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isSelected(e: AnActionEvent) = LatexSettings.get().enabled
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        LatexSettings.get().enabled = state
        LatexSettings.get().changed()
    }
}
