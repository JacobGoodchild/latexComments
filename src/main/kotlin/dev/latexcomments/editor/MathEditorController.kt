package dev.latexcomments.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import dev.latexcomments.scan.CommentSyntax
import dev.latexcomments.settings.LatexSettings
import java.beans.PropertyChangeListener

/**
 * Shows the maths of one editor. The text is scanned in the background after typing pauses; only the part of the
 * file on screen (plus a margin) gets rendered regions; the lines with a caret always show the raw text.
 */
class MathEditorController(val editor: EditorEx, private val syntax: CommentSyntax) : Disposable {
    private var plan: List<PlanItem> = emptyList()
    private var planStamp = -1L
    private val shown = LinkedHashMap<PlanItem, Shown>()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    /** What was added to the editor for one plan item. */
    private class Shown(val fold: FoldRegion?, val inlay: Inlay<*>?) {
        val valid get() = (fold?.isValid ?: true) && (inlay?.isValid ?: true)
    }

    init {
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = schedule(rescan = true, delay = 300)
        }, this)
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (event.oldPosition.line != event.newPosition.line) apply()
            }
            override fun caretAdded(event: CaretEvent) = apply()
            override fun caretRemoved(event: CaretEvent) = apply()
        }, this)
        val visible = VisibleAreaListener { schedule(rescan = false, delay = 60) }
        editor.scrollingModel.addVisibleAreaListener(visible, this)
        val fontListener = PropertyChangeListener { e -> if (e.propertyName == EditorEx.PROP_FONT_SIZE) refreshAll() }
        editor.addPropertyChangeListener(fontListener, this)
        rescan()
    }

    private var pendingRescan = false

    private fun schedule(rescan: Boolean, delay: Int) {
        if (rescan) pendingRescan = true
        alarm.cancelAllRequests()
        alarm.addRequest({ if (pendingRescan) { pendingRescan = false; rescan() } else apply() }, delay)
    }

    /** Scans the text in the background, then shows the result. */
    fun rescan() {
        if (!LatexSettings.get().enabled) { removeAll(); return }
        val document = editor.document
        val trailing = LatexSettings.get().trailingComments
        ReadAction.nonBlocking<Pair<Long, List<PlanItem>>> {
            val stamp = document.modificationStamp
            stamp to RenderPlan.plan(document.immutableCharSequence, syntax, trailing)
        }.expireWith(this).coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { (stamp, items) ->
                if (stamp != document.modificationStamp) { schedule(rescan = true, delay = 100); return@finishOnUiThread }
                plan = items; planStamp = stamp
                rekeyShown()
                apply()
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    /**
     * After a rescan the plan's items have new offsets. Regions already on screen moved with the text, so they are
     * matched to the new items by where they are now and what they show; only real changes are redrawn.
     */
    private fun rekeyShown() {
        val byPlace = plan.associateBy { Triple(it.start, it.end, latexOf(it)) }
        val old = LinkedHashMap(shown)
        shown.clear()
        for ((item, s) in old) {
            val fold = s.fold
            val match = if (fold != null && fold.isValid && s.valid) byPlace[Triple(fold.startOffset, fold.endOffset, latexOf(item))] else null
            if (match != null) shown[match] = s else shown[item] = s // unmatched ones are removed by apply()
        }
    }

    private fun latexOf(item: PlanItem): List<String> = when (item) {
        is PlanItem.Lines -> item.spans.map { it.latex }
        is PlanItem.Inline -> listOf(item.span.latex)
    }

    /** Lines that show raw text: every caret's line. */
    private fun caretLines(): Set<Int> = editor.caretModel.allCarets.flatMap { c ->
        val a = editor.document.getLineNumber(c.selectionStart); val b = editor.document.getLineNumber(c.selectionEnd)
        (a..b).toList() + c.logicalPosition.line
    }.toSet()

    private fun visibleLines(): IntRange {
        val area = editor.scrollingModel.visibleArea
        if (area.height <= 0) return 0..200
        val first = editor.xyToLogicalPosition(java.awt.Point(0, area.y)).line
        val last = editor.xyToLogicalPosition(java.awt.Point(0, area.y + area.height)).line
        val margin = maxOf(100, last - first)
        return maxOf(0, first - margin)..(last + margin)
    }

    private fun linesOf(item: PlanItem): IntRange = when (item) {
        is PlanItem.Lines -> item.firstLine..item.lastLine
        is PlanItem.Inline -> item.line..item.line
    }

    /** Brings the editor in line with the plan, the carets and the visible area. Runs on the UI thread. */
    fun apply() {
        if (editor.isDisposed) return
        if (!LatexSettings.get().enabled) { removeAll(); return }
        if (planStamp != editor.document.modificationStamp) return // a rescan is on its way
        val carets = caretLines()
        val visible = visibleLines()
        val wanted = plan.filter { item ->
            val lines = linesOf(item)
            lines.first <= visible.last && lines.last >= visible.first && lines.none { it in carets }
        }.toSet()
        val folding = editor.foldingModel as FoldingModelEx
        val toRemove = shown.filter { (item, s) -> item !in wanted || !s.valid }
        val toAdd = wanted.filter { it !in shown || shown[it]?.valid == false }
        if (toRemove.isEmpty() && toAdd.isEmpty()) return
        folding.runBatchFoldingOperation {
            for ((item, s) in toRemove) {
                s.fold?.let { if (it.isValid) folding.removeFoldRegion(it) }
                s.inlay?.let { if (it.isValid) Disposer.dispose(it) }
                shown.remove(item)
            }
            for (item in toAdd) {
                if (item.end > editor.document.textLength) continue
                when (item) {
                    is PlanItem.Lines -> {
                        if (item.lastLine >= editor.document.lineCount) continue
                        val region = folding.addCustomLinesFolding(item.firstLine, item.lastLine, LinesMathRenderer(item))
                        if (region != null) shown[item] = Shown(region, null)
                    }
                    is PlanItem.Inline -> {
                        val fold = folding.createFoldRegion(item.span.start, item.span.end, "", null, true)
                        if (fold == null) continue
                        fold.isExpanded = false
                        val inlay = editor.inlayModel.addInlineElement(item.span.end, false, InlineMathRenderer(item.span, item.kind))
                        shown[item] = Shown(fold, inlay)
                    }
                }
            }
        }
    }

    fun removeAll() {
        if (shown.isEmpty()) return
        val folding = editor.foldingModel as FoldingModelEx
        folding.runBatchFoldingOperation {
            for (s in shown.values) {
                s.fold?.let { if (it.isValid) folding.removeFoldRegion(it) }
                s.inlay?.let { if (it.isValid) Disposer.dispose(it) }
            }
        }
        shown.clear()
    }

    /** Sizes or colours changed: draw everything again. */
    fun refreshAll() {
        removeAll()
        rescan()
    }

    /** The plan items currently drawn (for tests and the hover preview). */
    val shownItems: Set<PlanItem> get() = shown.keys

    /** Plan items (drawn or not) whose maths covers [offset]. */
    fun itemAt(offset: Int): PlanItem? = plan.firstOrNull { offset >= it.start && offset < it.end }

    fun spanAt(offset: Int) = plan.flatMap { if (it is PlanItem.Lines) it.spans else listOf((it as PlanItem.Inline).span) }
        .firstOrNull { offset >= it.start && offset < it.end }

    override fun dispose() {
        if (!editor.isDisposed) removeAll()
    }

    companion object {
        val KEY = Key.create<MathEditorController>("latexcomments.controller")
        fun of(editor: com.intellij.openapi.editor.Editor): MathEditorController? = editor.getUserData(KEY)

        fun forAllEditors(action: (MathEditorController) -> Unit) {
            ApplicationManager.getApplication().invokeLater {
                for (e in com.intellij.openapi.editor.EditorFactory.getInstance().allEditors) of(e)?.let(action)
            }
        }
    }
}
