package com.bloo.bluelink.data

/** The [E] constant called [name], or null for a missing or unknown name (a stored value from an older or newer build). */
inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }
