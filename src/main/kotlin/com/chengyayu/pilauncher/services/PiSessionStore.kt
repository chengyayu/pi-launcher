package com.chengyayu.pilauncher.services

/** A Pi Session with no Host. Only what the plugin needs to reopen one; Pi keeps the content (`docs/adr/0006`). */
data class PiSessionRecord(
    val id: String,
    val displayName: String,
    val closedAt: Long
)

/** Remembers parked Pi Sessions across Hosts and IDE restarts (`docs/adr/0006`). */
interface PiSessionStore {

    fun load(): List<PiSessionRecord>

    fun save(records: List<PiSessionRecord>)

    companion object {
        /** In memory only: for tests and for a registry used on its own. */
        fun inMemory(): PiSessionStore = object : PiSessionStore {
            private var records: List<PiSessionRecord> = emptyList()

            override fun load(): List<PiSessionRecord> = records

            override fun save(records: List<PiSessionRecord>) {
                this.records = records
            }
        }
    }
}
