package com.chengyayu.pilauncher.infrastructure

import java.lang.reflect.Method

/**
 * Reflection helpers for the terminal API, whose shape differs across platform
 * versions and engines (`docs/adr/0002`): a rename costs a log line, not a crash.
 */
internal fun Class<*>.methodOrNull(name: String, vararg params: Class<*>?): Method? = try {
    getMethod(name, *params).apply { runCatching { isAccessible = true } }
} catch (_: NoSuchMethodException) {
    null
}

/** Invokes [name]; null when the member is absent or the call failed. */
internal fun Any.callOrNull(name: String, vararg params: Class<*>?): Any? =
    javaClass.methodOrNull(name, *params)?.invoke(this)
