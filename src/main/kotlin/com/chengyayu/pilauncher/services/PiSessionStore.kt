package com.chengyayu.pilauncher.services

/**
 * A Pi Session the plugin knows about that has no Host.
 *
 * The conversation itself lives in Pi's own session file; the plugin stores only what
 * it has to know to reopen one, and never any of Pi's content (`docs/adr/0006`).
 */
data class PiSessionRecord(
    val id: String,
    val displayName: String,
    val closedAt: Long
)

/**
 * Remembers Pi Sessions across Hosts and across IDE restarts (`docs/adr/0006`).
 *
 * Without this, "close the tab and come back later" would only hold until the IDE
 * restarts, which a user would read as the feature being broken.
 */
interface PiSessionStore {

    fun load(): List<PiSessionRecord>

    fun save(records: List<PiSessionRecord>)

    companion object {
        /** In memory only, and never shared: for tests and for a registry used on its own. */
        fun inMemory(): PiSessionStore = object : PiSessionStore {
            private var records: List<PiSessionRecord> = emptyList()

            override fun load(): List<PiSessionRecord> = records

            override fun save(records: List<PiSessionRecord>) {
                this.records = records
            }
        }
    }
}
