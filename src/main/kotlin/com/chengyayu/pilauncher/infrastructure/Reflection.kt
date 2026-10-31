package com.chengyayu.pilauncher.infrastructure

import java.lang.reflect.Method

/** The terminal API differs across platform versions and engines (`docs/adr/0002`). */
internal fun Class<*>.methodOrNull(name: String, vararg params: Class<*>?): Method? = try {
    getMethod(name, *params).apply { runCatching { isAccessible = true } }
} catch (_: NoSuchMethodException) {
    null
}

/** Invokes [name]; null when the member is absent, and it throws when the call fails. */
internal fun Any.callOrNull(name: String, vararg params: Class<*>?): Any? =
    javaClass.methodOrNull(name, *params)?.invoke(this)

