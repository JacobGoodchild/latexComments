package dev.latexcomments.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.messages.Topic
import javax.swing.JComponent

@Service(Service.Level.APP)
@State(name = "LatexCommentsSettings", storages = [Storage("latexcomments.xml")])
class LatexSettings : PersistentStateComponent<LatexSettings.Data> {
    class Data {
        var enabled: Boolean = true
        /** Size of the maths compared with the editor font, in percent. */
        var sizePercent: Int = 125
        /** Also render maths in comments that follow code on the same line (drawn at line height). */
        var trailingComments: Boolean = true
        var hoverPreview: Boolean = true
        /** Lines starting with "//=" (or "#=", " * = " ...) are AsciiMath. */
        var asciiMathLines: Boolean = true
        /** Read unmarked comment lines as AsciiMath when they are clearly maths. Off by default. */
        var autoDetect: Boolean = false
    }

    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }

    var enabled by data::enabled
    var sizePercent: Int
        get() = data.sizePercent.coerceIn(MIN_SIZE, MAX_SIZE)
        set(v) { data.sizePercent = v.coerceIn(MIN_SIZE, MAX_SIZE) }
    var trailingComments by data::trailingComments
    var hoverPreview by data::hoverPreview
    var asciiMathLines by data::asciiMathLines
    var autoDetect by data::autoDetect

    /** Tells every open editor to redraw. */
    fun changed() = ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).settingsChanged()

    fun interface Listener { fun settingsChanged() }

    companion object {
        const val MIN_SIZE = 60
        const val MAX_SIZE = 250
        val TOPIC = Topic.create("LaTeX Comments settings", Listener::class.java)
        fun get(): LatexSettings = ApplicationManager.getApplication().getService(LatexSettings::class.java)
    }
}

/** Settings > Tools > LaTeX Comments. */
class LatexConfigurable : Configurable {
    private var panel: DialogPanel? = null

    override fun getDisplayName() = "LaTeX Comments"

    override fun createComponent(): JComponent {
        val s = LatexSettings.get()
        return panel {
            row { checkBox("Show LaTeX in comments as formulas").bindSelected(s::enabled)
                .comment("Write \$...\$ or \$\$...\$\$ in a comment. The line under the caret shows the raw text for editing.") }
            row("Formula size:") {
                intTextField(LatexSettings.MIN_SIZE..LatexSettings.MAX_SIZE).bindIntText(s::sizePercent).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
                label("% of the editor font")
            }
            row { checkBox("Also in comments after code on the same line").bindSelected(s::trailingComments)
                .comment("Those formulas are shrunk to fit the line.") }
            row { checkBox("Show a larger preview when the mouse is over a formula").bindSelected(s::hoverPreview) }
            group("Easy maths (no LaTeX needed)") {
                row { checkBox("Lines starting with //= are maths").bindSelected(s::asciiMathLines)
                    .comment("Write maths the way you would type it: //= L = sum_i (phi_0 + phi_1 x_i - y_i)^2. " +
                        "Also #=, ///= and * = in block comments. The notation is called AsciiMath.") }
                row { checkBox("Also show other comment lines as maths when they clearly are").bindSelected(s::autoDetect)
                    .comment("Off by default. Only lines like x^2 + y^2 = r^2 or sigma = sqrt(v) are shown; " +
                        "anything that looks like code, a name or a note is left alone. Hover over a formula to see how it was read.") }
            }
        }.also { panel = it }
    }

    override fun isModified() = panel?.isModified() ?: false
    override fun apply() { panel?.apply(); LatexSettings.get().changed() }
    override fun reset() { panel?.reset() }
    override fun disposeUIResources() { panel = null }
}
