package com.chengyayu.pilauncher.settings

import com.intellij.openapi.options.Configurable
import com.chengyayu.pilauncher.infrastructure.FilePiModelRepository
import com.chengyayu.pilauncher.infrastructure.PiModelRepository
import javax.swing.JComponent

/**
 * Settings → Tools → Pi Launcher.
 *
 * Instantiated reflectively by the platform, so it must keep a no-argument
 * constructor; the form itself is delegated to [PiSettingsForm].
 */
class PiSettingsConfigurable : Configurable {

    private val models: PiModelRepository = FilePiModelRepository()
    private var form: PiSettingsForm? = null

    override fun getDisplayName(): String = "Pi Launcher"

    override fun createComponent(): JComponent {
        val settings = PiSettings.getInstance()
        val modelOptions = PiSettingsForm.modelOptions(models.loadModels())
        return PiSettingsForm(settings.state, modelOptions)
            .also { form = it }
            .panel
    }

    override fun isModified(): Boolean {
        val settings = PiSettings.getInstance().state
        return form?.toState()?.let { it != settings } ?: false
    }

    override fun apply() {
        form?.toState()?.let { PiSettings.getInstance().loadState(it) }
    }

    override fun reset() {
        form?.populate(PiSettings.getInstance().state)
    }

    override fun disposeUIResources() {
        form = null
    }
}
