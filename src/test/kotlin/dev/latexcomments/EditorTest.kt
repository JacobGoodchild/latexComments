package dev.latexcomments

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import dev.latexcomments.editor.InlineMathRenderer
import dev.latexcomments.editor.LinesMathRenderer
import dev.latexcomments.editor.MathEditorController
import dev.latexcomments.settings.LatexSettings

/** The plugin in a real (headless) editor. */
class EditorTest : BasePlatformTestCase() {
    private val code = d(
        """
        |// §L = \sum_i (\phi_0 + \phi_1 x_i - y_i)^2§
        |var total = 0; // trailing §r_i^2§
        |/// Doc with §\alpha§ and §§\frac{1}{2}§§
        |var x = "// §not§";
        |// a bad one §\nosuchcommand§
        |<caret>var y = 1;
        """.trimMargin())

    /** Lets the background scan (which waits for a pause in typing) finish and its result reach the editor. */
    private fun settle() {
        repeat(8) {
            Thread.sleep(60)
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

    private val editor get() = myFixture.editor as EditorEx
    private fun lines() = customRegions().map { editor.document.getLineNumber(it.startOffset) }.sorted().toString() + " caret line " + editor.caretModel.logicalPosition.line + "\n" + editor.document.text
    private fun customRegions() = editor.foldingModel.getAllFoldRegions().filterIsInstance<CustomFoldRegion>().filter { it.renderer is LinesMathRenderer }
    private fun inlineInlays() = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength, InlineMathRenderer::class.java)

    override fun tearDown() {
        try { LatexSettings.get().enabled = true } finally { super.tearDown() }
    }

    fun testRendersCommentsAndKeepsTheCaretLineRaw() {
        myFixture.configureByText("Model.cs", code)
        settle()
        assertNotNull(MathEditorController.of(editor))
        // Lines 0, 2 and 4 are comment-only lines with maths; line 1 has maths after code; line 3 is a string.
        assertEquals(listOf(0, 2, 4), customRegions().map { editor.document.getLineNumber(it.startOffset) }.sorted())
        assertEquals(1, inlineInlays().size)
        for (r in customRegions()) {
            assertTrue(r.renderer.calcHeightInPixels(r) >= editor.lineHeight)
            assertTrue(r.renderer.calcWidthInPixels(r) > 10)
        }
        // Moving the caret onto line 0 shows its raw text; moving away renders it again.
        editor.caretModel.moveToOffset(5)
        settle()
        assertEquals(listOf(2, 4), customRegions().map { editor.document.getLineNumber(it.startOffset) }.sorted())
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(5))
        settle()
        assertEquals(3, customRegions().size)
    }

    fun testTypingKeepsWorkingAndToggleTurnsItOff() {
        myFixture.configureByText("Model.cs", code)
        settle()
        myFixture.type(d("\n// new §x^2§ "))
        settle()
        // The new line holds the caret, so it stays raw until the caret leaves.
        assertEquals(lines(), 3, customRegions().size)
        editor.caretModel.moveToOffset(0)
        settle()
        assertEquals(lines(), 3, customRegions().size) // line 0 raw now, the new line rendered
        LatexSettings.get().enabled = false
        LatexSettings.get().changed()
        settle()
        assertEquals(0, customRegions().size)
        assertEquals(0, inlineInlays().size)
        LatexSettings.get().enabled = true
        LatexSettings.get().changed()
        settle()
        assertEquals(3, customRegions().size)
    }

    fun testOtherFilesAreLeftAlone() {
        myFixture.configureByText("notes.txt", code)
        settle()
        assertNull(MathEditorController.of(editor))
        assertEquals(0, customRegions().size)
    }

    fun testJavaAndPython() {
        myFixture.configureByText("A.java", d("/**\n * Rate §\\eta§.\n */\nclass A { int a; // §b§\n}"))
        settle()
        assertEquals(1, customRegions().size)
        assertEquals(1, inlineInlays().size)
        myFixture.configureByText("t.py", d("def f():\n    \"\"\"§§x^2§§\"\"\"\n    return 1  # §y§\n"))
        settle()
        assertEquals(1, customRegions().size)
        assertEquals(1, inlineInlays().size)
    }
}
