package com.astralofthesun.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

/* ============================================================
   One wrapper for every server call.

   The server decides every result. The app only sends the tap
   and renders what comes back — so every action goes through
   here, and every failure becomes a readable message instead of
   an exception:

     • {"ok":false,"error":"…"}   → ApiError(error)
     • HTTP 401                   → "Sign in to continue."
     • HTTP 404 "Not found."      → ApiError(notLive = true)
       (the route isn't deployed on the bot yet — screens show a
        calm "not live yet" state rather than breaking)
     • no network                 → "Can't reach the server…"
   ============================================================ */

class ApiError(
    message: String,
    val notLive: Boolean = false,
    val unauthorized: Boolean = false,
) : Exception(message)

const val NOT_LIVE_MESSAGE = "This isn't live on the server yet."

internal fun errorFromBody(code: Int, body: String?): ApiError {
    val parsed = runCatching { body?.let { Json.parseToJsonElement(it).jsonObject } }.getOrNull()
    val msg = parsed?.str("error", "message")
    return when {
        code == 404 && (msg == null || msg.equals("Not found.", ignoreCase = true)) ->
            ApiError(NOT_LIVE_MESSAGE, notLive = true)
        code == 401 -> ApiError(msg ?: "Sign in to continue.", unauthorized = true)
        else -> ApiError(msg ?: "Server error ($code). Please try again.")
    }
}

/** Throws ApiError if a 200 body still says ok:false. */
internal fun JsonObject.requireOk(): JsonObject {
    if (bool("ok") == false || bool("success") == false) {
        throw ApiError(str("error", "message") ?: "The server rejected that action.")
    }
    return this
}

suspend fun apiCall(block: suspend () -> JsonObject): Result<JsonObject> = try {
    Result.success(block().requireOk())
} catch (c: CancellationException) {
    throw c
} catch (e: HttpException) {
    Result.failure(errorFromBody(e.code(), runCatching { e.response()?.errorBody()?.string() }.getOrNull()))
} catch (e: ApiError) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(ApiError("Can't reach the server. Check your connection and try again."))
} catch (e: Exception) {
    Result.failure(ApiError(e.message ?: "Something went wrong."))
}

/** Same as [apiCall] but maps the payload. */
suspend fun <T> apiMap(block: suspend () -> JsonObject, map: (JsonObject) -> T): Result<T> =
    apiCall(block).mapCatching { map(it.payload()) }

/** Human message for any failure. */
fun Throwable.userMessage(): String = message ?: "Something went wrong."
val Throwable.isNotLive: Boolean get() = (this as? ApiError)?.notLive == true
