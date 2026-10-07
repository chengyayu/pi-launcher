package com.chengyayu.pilauncher.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.chengyayu.pilauncher.domain.PiLaunchOptions
import com.chengyayu.pilauncher.domain.PiModel
import java.awt.Dimension
import java.awt.Font
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * The Swing form behind Settings → Tools → Pi Launcher, plus the mapping between
 * its widgets and [PiSettings.State].
 *
 * Kept separate from [PiSettingsConfigurable] so the Configurable only deals with
 * the lifecycle contract.
 */
internal class PiSettingsForm(
    state: PiSettings.State,
    modelOptions: Array<String>
) {

    private val piCommandField = JBTextField()
    private val modelCombo = ComboBox(modelOptions)
    private val customModelField = JBTextField()
    private val thinkingLevelCombo = ComboBox(THINKING_LEVELS)
    private val extraArgsField = JBTextField()
    private val autoOpenFilesCheckbox = JCheckBox(AUTO_OPEN_LABEL, state.autoOpenFiles)

    val panel: JPanel = FormBuilder.createFormBuilder()
        .addSeparator()
        .addSectionLabel("Model")
        .addLabeledComponent(JBLabel("Model:"), modelCombo, 1, false)
        .addLabeledComponent(JBLabel("Custom model id:"), customModelField, 1, false)
        .addHint("If set, overrides the model dropdown.")
        .addLabeledComponent(JBLabel("Thinking level:"), thinkingLevelCombo, 1, false)
        .addHint("Some models may not support all thinking levels.")
        .addSeparator()
        .addSectionLabel("General")
        .addLabeledComponent(JBLabel("Pi command:"), piCommandField, 1, false)
        .addLabeledComponent(JBLabel("Extra arguments:"), extraArgsField, 1, false)
        .addSeparator()
        .addSectionLabel("Options")
        .addComponent(autoOpenFilesCheckbox, 1)
        .addComponentFillVertically(JPanel(), 0)
        .panel
        .also { form ->
            listOf(modelCombo, customModelField, thinkingLevelCombo).forEach {
                it.preferredSize = Dimension(FIELD_WIDTH, it.preferredSize.height)
            }
            // Illustrate the expected format with a model the user actually has,
            // rather than a hard-coded vendor example.
            customModelField.emptyText.text =
                modelOptions.firstOrNull { it != PiLaunchOptions.DEFAULT_VALUE }
                    ?: "provider/model-id"
            populate(state)
        }

    fun populate(state: PiSettings.State) {
        piCommandField.text = state.piCommand
        modelCombo.selectedItem = state.model
        customModelField.text = state.customModelId
        thinkingLevelCombo.selectedItem = state.thinkingLevel
        extraArgsField.text = state.extraArgs
        autoOpenFilesCheckbox.isSelected = state.autoOpenFiles
    }

    fun toState(): PiSettings.State = PiSettings.State(
        piCommand = piCommandField.text.ifBlank { PiLaunchOptions.DEFAULT_COMMAND },
        model = modelCombo.selectedItem as? String ?: PiLaunchOptions.DEFAULT_VALUE,
        customModelId = customModelField.text,
        thinkingLevel = thinkingLevelCombo.selectedItem as? String ?: PiLaunchOptions.DEFAULT_VALUE,
        extraArgs = extraArgsField.text,
        autoOpenFiles = autoOpenFilesCheckbox.isSelected
    )

    companion object {
        private const val FIELD_WIDTH = 400
        private const val AUTO_OPEN_LABEL = "Auto-open files modified by Pi (off by default)"

        val THINKING_LEVELS = arrayOf(
            PiLaunchOptions.DEFAULT_VALUE,
            "none",
            "low",
            "medium",
            "high",
            "max"
        )

        /** Model ids for the drop-down, always prefixed with the default entry. */
        fun modelOptions(models: List<PiModel>): Array<String> =
            (listOf(PiLaunchOptions.DEFAULT_VALUE) + models.map { it.commandArg }).toTypedArray()

        private fun FormBuilder.addSectionLabel(text: String) = addComponent(
            JBLabel(text).apply {
                font = font.deriveFont(Font.BOLD)
                border = JBUI.Borders.emptyTop(4)
            }
        )

        private fun FormBuilder.addHint(text: String) = addComponentToRightColumn(
            JBLabel(text).apply {
                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                font = JBUI.Fonts.smallFont()
            },
            0
        )
    }
}
