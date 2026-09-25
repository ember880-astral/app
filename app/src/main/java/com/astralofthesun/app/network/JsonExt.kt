package com.astralofthesun.app.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/* ============================================================
   Defensive JSON readers shared by Repository, Parsers and the
   Pokémon screens. First key that exists wins, so a renamed
   backend field degrades to "empty", never to a crash.
   ============================================================ */

internal fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k] ?: continue
        if (v is JsonNull) continue
        (v as? JsonPrimitive)?.contentOrNull?.let { return it }
    }
    return null
}

internal fun JsonObject.num(vararg keys: String): Long? {
    for (k in keys) {
        val p = this[k] as? JsonPrimitive ?: continue
        if (p is JsonNull) continue
        p.longOrNull?.let { return it }
        p.doubleOrNull?.let { return it.toLong() }
    }
    return null
}

internal fun JsonObject.numInt(vararg keys: String): Int? = num(*keys)?.toInt()

internal fun JsonObject.bool(vararg keys: String): Boolean? {
    for (k in keys) {
        val p = this[k] as? JsonPrimitive ?: continue
        if (p is JsonNull) continue
        p.booleanOrNull?.let { return it }
    }
    return null
}

internal fun JsonObject.obj(vararg keys: String): JsonObject? {
    for (k in keys) (this[k] as? JsonObject)?.let { return it }
    return null
}

internal fun JsonObject.arr(vararg keys: String): List<JsonElement> {
    for (k in keys) (this[k] as? JsonArray)?.let { return it }
    return emptyList()
}

internal fun JsonObject.objs(vararg keys: String): List<JsonObject> = arr(*keys).mapNotNull { it as? JsonObject }

internal fun JsonObject.strs(vararg keys: String): List<String> =
    arr(*keys).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }

internal fun JsonObject.ints(vararg keys: String): List<Int> =
    arr(*keys).mapNotNull { (it as? JsonPrimitive)?.longOrNull?.toInt() }

/** Some endpoints wrap the payload in {data: {...}} or {result: {...}}; unwrap if present. */
internal fun JsonObject.payload(): JsonObject = obj("data", "result") ?: this
