package com.chengyayu.pilauncher.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * The per-project [PiSessionStore] backed by a state file. Per project because Pi looks
 * sessions up per working directory, so an id from another project would not resolve.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "PiLauncherSessions",
    storages = [Storage("PiLauncherSessions.xml")]
)
class PersistentPiSessionStore : PiSessionStore, PersistentStateComponent<PersistentPiSessionStore.Bean> {

    /** Plain mutable classes with defaults, as the serializer expects. */
    class Bean(var sessions: MutableList<Record> = mutableListOf())

    class Record(var id: String = "", var displayName: String = "", var closedAt: Long = 0)

    private var bean = Bean()

    override fun getState(): Bean = bean

    override fun loadState(state: Bean) {
        bean = state
    }

    override fun load(): List<PiSessionRecord> = bean.sessions.map {
        PiSessionRecord(id = it.id, displayName = it.displayName, closedAt = it.closedAt)
    }

    override fun save(records: List<PiSessionRecord>) {
        bean = Bean(records.map { Record(it.id, it.displayName, it.closedAt) }.toMutableList())
    }

    companion object {
        fun getInstance(project: Project): PersistentPiSessionStore = project.service()
    }
}
