package com.astralofthesun.app.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/* ============================================================
   Astral of the Sun — API client
   Base URL for the live backend (astral-bot on Railway).
   The bot uses a session cookie (set on /api/auth/verify-otp
   or /api/auth/register, read back on /api/auth/session),
   so the OkHttp client carries a persistent CookieJar — every
   request automatically re-sends the session cookie, and every
   response's Set-Cookie is captured and saved to disk.
   ============================================================ */

object ApiConfig {
    const val BASE_URL = "https://astral-bot-production-afb0.up.railway.app/"
}

/** Persists cookies (the session cookie in particular) across app restarts. */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("astral_cookies", Context.MODE_PRIVATE)
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    init {
        val raw = prefs.getString("cookies", null)
        if (raw != null) {
            raw.split("||").filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split("|>")
                if (parts.size == 2) {
                    val host = parts[0]
                    val cookie = Cookie.parse(HttpUrl.Builder().scheme("https").host(host).build(), parts[1])
                    if (cookie != null) {
                        store.getOrPut(host) { mutableListOf() }.add(cookie)
                    }
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        cookies.forEach { new ->
            list.removeAll { it.name == new.name }
            if (!new.expiresAt.let { it != 0L && it < System.currentTimeMillis() }) {
                list.add(new)
            }
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        return store[host]?.filter { it.expiresAt > now || it.persistent.not() } ?: emptyList()
    }

    fun clear() {
        store.clear()
        persist()
    }

    private fun persist() {
        val serialized = store.entries.joinToString("||") { (host, cookies) ->
            cookies.joinToString("||") { "$host|>${it.toString()}" }
        }
        prefs.edit().putString("cookies", serialized).apply()
    }
}

object ApiClient {
    private var retrofit: Retrofit? = null
    lateinit var cookieJar: PersistentCookieJar
        private set

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Call once, e.g. from Application/MainActivity, before using [api]. */
    fun init(context: Context) {
        if (retrofit != null) return
        cookieJar = PersistentCookieJar(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()

        retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    val api: AstralApi by lazy {
        checkNotNull(retrofit) { "ApiClient.init(context) must be called before use." }
            .create(AstralApi::class.java)
    }

    /** Public asset URL for an item image served from /assets/items/:file. */
    fun assetUrl(file: String): String = ApiConfig.BASE_URL + "assets/items/" + file
}
