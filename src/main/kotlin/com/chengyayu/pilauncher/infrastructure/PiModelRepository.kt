package com.chengyayu.pilauncher.infrastructure

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.chengyayu.pilauncher.domain.PiModel
import java.io.File

/**
 * Reads the models Pi advertises in `~/.pi/agent/models.json`.
 *
 * Any failure degrades to an empty list: a malformed or missing file must never
 * stop the plugin from working.
 */
interface PiModelRepository {
    fun loadModels(): List<PiModel>
}

class FilePiModelRepository(
    private val modelsFile: File = defaultModelsFile()
) : PiModelRepository {

    private val logger = Logger.getInstance(FilePiModelRepository::class.java)

    override fun loadModels(): List<PiModel> {
        if (!modelsFile.exists()) {
            logger.info("models.json not found at ${modelsFile.path}")
            return emptyList()
        }

        return try {
            parse(modelsFile.readText())
        } catch (e: Exception) {
            logger.warn("Failed to parse ${modelsFile.path}", e)
            emptyList()
        }
    }

    private fun parse(json: String): List<PiModel> {
        val root = Gson().fromJson(json, JsonObject::class.java) ?: return emptyList()
        val providers = root.getAsJsonObject("providers") ?: return emptyList()

        return providers.entrySet().flatMap { (providerName, providerElement) ->
            val models = providerElement.asJsonObject.getAsJsonArray("models") ?: return@flatMap emptyList()
            models.mapNotNull { element ->
                val id = element.asJsonObject.get("id")?.asString ?: return@mapNotNull null
                val name = element.asJsonObject.get("name")?.asString ?: ""
                PiModel(id = id, name = name, provider = providerName)
            }
        }
    }

    companion object {
        fun defaultModelsFile(): File =
            File(System.getProperty("user.home"), ".pi/agent/models.json")
    }
}
