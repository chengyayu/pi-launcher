package com.chengyayu.pilauncher.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.chengyayu.pilauncher.domain.PiLaunchOptions

/**
 * Persistent plugin settings (Settings → Tools → Pi Launcher).
 */
@Service(Service.Level.APP)
@State(
    name = "PiLauncherSettings",
    storages = [Storage("PiLauncherSettings.xml")]
)
class PiSettings : PersistentStateComponent<PiSettings.State> {

    data class State(
        var piCommand: String = PiLaunchOptions.DEFAULT_COMMAND,
        var model: String = PiLaunchOptions.DEFAULT_VALUE,
        var customModelId: String = "",
        var thinkingLevel: String = PiLaunchOptions.DEFAULT_VALUE,
        var autoOpenFiles: Boolean = false,
        var shellPath: String = "",
        var extraArgs: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): PiSettings = service()
    }
}

/**
 * Translates persisted settings into the domain model used to build the CLI
 * command. A custom model id wins over the drop-down, and "Default" means
 * "let Pi decide".
 */
fun PiSettings.State.toLaunchOptions(): PiLaunchOptions = PiLaunchOptions(
    command = piCommand.ifBlank { PiLaunchOptions.DEFAULT_COMMAND },
    model = resolveModel(),
    thinkingLevel = thinkingLevel.takeUnless { it.isDefaultValue() },
    extraArgs = extraArgs
)

private fun PiSettings.State.resolveModel(): String? = when {
    customModelId.isNotBlank() -> customModelId
    !model.isDefaultValue() -> model
    else -> null
}

private fun String.isDefaultValue(): Boolean =
    isBlank() || this == PiLaunchOptions.DEFAULT_VALUE
